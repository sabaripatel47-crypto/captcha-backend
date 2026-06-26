package com.captcha.captcha.dto;

/**
 * 验证码校验接口的请求参数。
 *
 * 前端完成滑块拖动后，调用 POST /api/captcha/verify，提交以下数据：
 * - captchaId：验证码唯一标识（从 get 接口获取）
 * - offsetX：滑块最终位置的 X 坐标（像素）
 * - track：拖动轨迹数据，格式为 List[{x, t}]，用于后端风控分析
 */
public class CaptchaVerifyRequest {
    /** 验证码唯一标识，对应 get 接口返回的 captchaId */
    private String captchaId;
    /** 用户拖动滑块的最终位置 X 坐标（像素） */
    private Integer offsetX;
    /**
     * 拖动轨迹数据，格式为 List[{x, t}]：
     * - x：滑块在时刻 t 时的 X 坐标
     * - t：相对拖动开始时刻的毫秒数（从 0 开始递增）
     * 类型为 Object，前端传什么就是什么，后端 TrackRiskService 负责解析
     */
    private Object track;

    public String getCaptchaId() { return captchaId; }
    public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }
    public Integer getOffsetX() { return offsetX; }
    public void setOffsetX(Integer offsetX) { this.offsetX = offsetX; }
    public Object getTrack() { return track; }
    public void setTrack(Object track) { this.track = track; }
}
