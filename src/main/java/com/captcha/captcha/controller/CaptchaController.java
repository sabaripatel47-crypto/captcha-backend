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

/**
 * 验证码前后端交互的入口 Controller。
 *
 * 提供三个接口：
 * 1. GET  /api/captcha/get      - 生成并返回一张带目标图案的验证码图片
 * 2. POST /api/captcha/verify   - 校验用户拖动滑块的结果
 * 3. POST /api/captcha/check    - 验证 verifyToken 是否有效（一次性使用）
 */
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

    /**
     * 获取验证码。
     * 后端生成一张底图（画布上随机位置绘制一个目标图案），将图片 base64 编码后返回给前端，
     * 同时把目标位置 targetX 存入 Redis（2 分钟有效期），返回唯一的 captchaId。
     */
    @GetMapping("/get")
    public ResultVO<CaptchaVO> getCaptcha() {
        try {
            // 生成验证码图片（包含底图和目标图案）和目标位置 targetX
            CaptchaImageService.CaptchaResult result = captchaImageService.generateCaptcha();
            // 生成唯一 ID，用于后续验证时关联 Redis 中存储的目标位置
            String captchaId = UUID.randomUUID().toString().replace("-", "");

            // 将 targetX 存入 Redis，key = "captcha:" + captchaId，2 分钟后自动过期
            captchaCacheService.saveCaptcha(captchaId, result.targetX);

            // 将图片转为 base64 字符串，前端可直接设为 img src
            String imageBase64 = captchaImageService.imageToBase64(result.image);

            CaptchaVO vo = new CaptchaVO(captchaId, "data:image/png;base64," + imageBase64);
            return ResultVO.ok(vo);
        } catch (Exception e) {
            e.printStackTrace();
            return ResultVO.fail(500, "图片生成失败: " + e.getMessage());
        }
    }

    /**
     * 校验用户提交的滑块位置。
     *
     * 流程：
     * 1. 参数校验（captchaId、offsetX 不能为空）
     * 2. 从 Redis 取回该 captchaId 对应的目标位置 targetX，若不存在则已过期
     * 3. 轨迹风控检测（耗时过短 / 坐标跳跃 / 速度过于均匀均视为机器人行为）
     * 4. 位置容差校验：偏右容忍 50px，偏左容忍 25px（防止用户"多滑"但不允许"少滑"）
     * 5. 校验通过后生成一次性 verifyToken 并存入 Redis，返回给前端
     */
    @PostMapping("/verify")
    public ResultVO<String> verifyCaptcha(@RequestBody CaptchaVerifyRequest request) {
        if (request.getCaptchaId() == null || request.getOffsetX() == null) {
            return ResultVO.fail("参数不完整");
        }

        // 根据 captchaId 从 Redis 取目标位置，若返回 null 表示已过期或不存在
        CaptchaCacheService.CaptchaData data = captchaCacheService.getCaptcha(request.getCaptchaId());
        if (data == null) {
            return ResultVO.fail("验证码已过期，请重新获取");
        }

        int targetX = data.getTargetX();
        int offsetX = request.getOffsetX();

        // 轨迹风控：检测拖动轨迹是否为机器人模拟
        boolean isRisk = trackRiskService.checkRisk(request.getTrack(), offsetX, targetX);
        if (isRisk) {
            // 风控命中后立即删除验证码，防止重复使用同一轨迹绕过检测
            captchaCacheService.removeCaptcha(request.getCaptchaId());
            return ResultVO.fail("操作异常，请重试");
        }

        // 位置容差校验：diff >= 0（多滑）容忍 50px，diff < 0（少滑）容忍 25px
        int diff = offsetX - targetX;
        int tolerance = diff >= 0 ? 50 : 25;
        if (Math.abs(diff) > tolerance) {
            return ResultVO.fail("验证失败，位置偏差过大");
        }

        // 验证成功后删除 Redis 中的验证码数据（一次性使用）
        captchaCacheService.removeCaptcha(request.getCaptchaId());

        // 生成一次性 verifyToken 并存入 Redis，5 分钟后过期
        String verifyToken = jwtUtil.generateVerifyToken();
        captchaCacheService.saveVerifyToken(verifyToken, "anonymous");

        return ResultVO.ok(verifyToken);
    }

    /**
     * 检查 verifyToken 是否有效（一次性）。
     *
     * 前端提交 verifyToken，服务端从 Redis 中查找：
     * - 若存在，说明验证码校验已通过，返回 true，并将该 token 从 Redis 删除（防止重复使用）
     * - 若不存在（已被删除或已过期），返回 false
     */
    @PostMapping("/check")
    public ResultVO<Boolean> checkVerifyToken(@RequestBody java.util.Map<String, String> body) {
        String token = body.get("verifyToken");
        if (token == null || token.isEmpty()) {
            return ResultVO.fail("Token 不能为空");
        }

        // consumeVerifyToken 内部会先查后删，返回值即为之前存入的用户标识
        String username = captchaCacheService.consumeVerifyToken(token);
        if (username == null) {
            return ResultVO.ok(false);
        }

        return ResultVO.ok(true);
    }
}
