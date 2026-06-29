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
 * 验证码相关接口
 */
@RestController
@RequestMapping("/api/captcha")
public class CaptchaController {
    // 导入对应service
    // 生成图片相关
    @Autowired
    private CaptchaImageService captchaImageService;
    // 图片缓存redis相关
    @Autowired
    private CaptchaCacheService captchaCacheService;
    // 轨迹风控相关
    @Autowired
    private TrackRiskService trackRiskService;
    // jwt相关
    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 获取验证码
     * 后端生成一张底图同时随机生成一个图标在底图上,返回对应的base64编码字符串
     * 同时把图标在底图的目标位置 targetX 存入 Redis（2 分钟有效期），返回唯一的 captchaId
     */
    @GetMapping("/get")
    public ResultVO<CaptchaVO> getCaptcha() {
        try {
            // 生成验证码图片（包含底图和目标图案）和目标位置 targetX
            CaptchaImageService.CaptchaResult result = captchaImageService.generateCaptcha();
            // 生成唯一 ID，用于后续验证时关联 Redis 中存储的目标位置(replace去掉-,让ID更短)
            String captchaId = UUID.randomUUID().toString().replace("-", "");

            // 将 targetX 存入 Redis，key = "captcha:" + captchaId，2 分钟后过期
            captchaCacheService.saveCaptcha(captchaId, result.targetX);

            // 将图片转为 base64 字符串，前端使用canvas的drawImage来绘制
            String imageBase64 = captchaImageService.imageToBase64(result.image);
            // 新建一个验证码实例对象(base64编码添加data前缀)
            CaptchaVO vo = new CaptchaVO(captchaId, "data:image/png;base64," + imageBase64);
            return ResultVO.ok(vo);
        } catch (Exception e) {
            e.printStackTrace();//将报错信息打印到控制台
            return ResultVO.fail(500, "图片生成失败: " + e.getMessage());//返回失败信息
        }
    }

    /**
     * 校验用户提交的滑块位置
     *
     * 流程：
     * 1. 参数校验（captchaId、offsetX 不能为空）
     * 2. 从 Redis 取回该 captchaId 对应的目标位置 targetX，若不存在则已国企或不存在
     * 3. 轨迹风控检测（耗时过短 / 坐标跳跃 / 速度过于均匀均视为机器人行为）
     * 4. 位置容差校验
     * 5. 校验通过后生成一次性 verifyToken 并存入 Redis，返回给前端
     */
    @PostMapping("/verify")
    public ResultVO<String> verifyCaptcha(@RequestBody CaptchaVerifyRequest request) {
        // 判空
        if (request.getCaptchaId() == null || request.getOffsetX() == null) {
            return ResultVO.fail("参数不完整");
        }

        // 根据 captchaId 从 Redis 取目标位置，若返回 null 表示已过期或不存在
        CaptchaCacheService.CaptchaData data = captchaCacheService.getCaptcha(request.getCaptchaId());
        if (data == null) {
            return ResultVO.fail("验证码已过期，请重新获取");
        }
        // 获取redis里的目标位置
        int targetX = data.getTargetX();
        //获取前端提交的终点位置
        int offsetX = request.getOffsetX();

        // 轨迹风控：检测拖动轨迹是否为机器人模拟
        boolean isRisk = trackRiskService.checkRisk(request.getTrack(), offsetX, targetX);
        if (isRisk) {
            // 风控命中后立即删除验证码，防止重复使用同一轨迹绕过检测
            captchaCacheService.removeCaptcha(request.getCaptchaId());
            return ResultVO.fail("操作异常，请重试");
        }

        // 位置容差校验：diff >= 0（右边）容忍 60px，diff < 0（左边）容忍 10px
        int diff = offsetX - targetX;
        int tolerance = diff >= 0 ? 60 : 10;
        if (Math.abs(diff) > tolerance) {
            return ResultVO.fail("验证失败，位置偏差过大");
        }

        // 验证成功后删除 Redis 中的验证码数据（一次性使用）
        captchaCacheService.removeCaptcha(request.getCaptchaId());

        // 生成一次性 verifyToken 并存入 Redis，5 分钟后过期(用户名暂时设为匿名),此token用于验证码验证成功的标识,在/login的接口用于校验,通过后发放登录token
        String verifyToken = jwtUtil.generateVerifyToken();
        captchaCacheService.saveVerifyToken(verifyToken, "anonymous");

        return ResultVO.ok(verifyToken);
    }

}
