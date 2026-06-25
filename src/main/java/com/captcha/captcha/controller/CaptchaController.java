package com.captcha.captcha.controller;

import com.captcha.captcha.dto.CaptchaVerifyRequest;
import com.captcha.captcha.service.CaptchaCacheService;
import com.captcha.captcha.service.CaptchaImageService;
import com.captcha.captcha.service.TrackRiskService;
import com.captcha.captcha.util.JwtUtil;
import com.captcha.captcha.vo.CaptchaVO;
import com.captcha.captcha.vo.ResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/captcha")
public class CaptchaController {

    @Autowired
    private CaptchaImageService captchaImageService;

    @Autowired
    private CaptchaCacheService captchaCacheService;

    @Autowired
    private TrackRiskService trackRiskService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/get")
    public ResultVO<CaptchaVO> getCaptcha() {
        try {
            CaptchaImageService.CaptchaResult result = captchaImageService.generateCaptcha();
            String captchaId = UUID.randomUUID().toString().replace("-", "");

            captchaCacheService.saveCaptcha(captchaId, result.targetX);

            String imageBase64 = captchaImageService.imageToBase64(result.image);

            CaptchaVO vo = new CaptchaVO(captchaId, "data:image/png;base64," + imageBase64);
            return ResultVO.ok(vo);
        } catch (Exception e) {
            e.printStackTrace();
            return ResultVO.fail(500, "图片生成失败: " + e.getMessage());
        }
    }

    @PostMapping("/verify")
    public ResultVO<String> verifyCaptcha(@RequestBody CaptchaVerifyRequest request) {
        if (request.getCaptchaId() == null || request.getOffsetX() == null) {
            return ResultVO.fail("参数不完整");
        }

        CaptchaCacheService.CaptchaData data = captchaCacheService.getCaptcha(request.getCaptchaId());
        if (data == null) {
            return ResultVO.fail("验证码已过期，请重新获取");
        }

        int targetX = data.getTargetX();
        int offsetX = request.getOffsetX();

        boolean isRisk = trackRiskService.checkRisk(request.getTrack(), offsetX, targetX);
        if (isRisk) {
            captchaCacheService.removeCaptcha(request.getCaptchaId());
            return ResultVO.fail("操作异常，请重试");
        }
        // 控制验证码校验的范围，不对称宽容度：偏右（多滑）宽容，偏左（少滑）严格
        int diff = offsetX - targetX;
        int tolerance = diff >= 0 ? 50 : 25;
        if (Math.abs(diff) > tolerance) {
            return ResultVO.fail("验证失败，位置偏差过大");
        }

        captchaCacheService.removeCaptcha(request.getCaptchaId());

        String verifyToken = jwtUtil.generateVerifyToken();
        captchaCacheService.saveVerifyToken(verifyToken, "anonymous");

        return ResultVO.ok(verifyToken);
    }

    @PostMapping("/check")
    public ResultVO<Boolean> checkVerifyToken(@RequestBody java.util.Map<String, String> body) {
        String token = body.get("verifyToken");
        if (token == null || token.isEmpty()) {
            return ResultVO.fail("Token 不能为空");
        }

        String username = captchaCacheService.consumeVerifyToken(token);
        if (username == null) {
            return ResultVO.ok(false);
        }

        return ResultVO.ok(true);
    }
}
