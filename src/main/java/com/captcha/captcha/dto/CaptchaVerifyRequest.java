package com.captcha.captcha.dto;

/**
 * 验证码校验接口的请求参数
 *
 * 前端完成滑块拖动后，调用 POST /api/captcha/verify，提交以下数据：
 * - captchaId：验证码唯一标识（从 get 接口获取）
 * - offsetX：滑块最终位置的 X 坐标（像素）
 * - track：拖动轨迹数据，格式为 List[{x, t}]，用于后端判断是否为机器人(x:时刻对应坐标,t:时刻对应时间)
 */
public class CaptchaVerifyRequest {
    private String captchaId;
    /** 用户拖动滑块的最终位置 X 坐标 */
    private Integer offsetX;
    
    private Object track;

    public String getCaptchaId() { return captchaId; }
    public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }
    public Integer getOffsetX() { return offsetX; }
    public void setOffsetX(Integer offsetX) { this.offsetX = offsetX; }
    public Object getTrack() { return track; }
    public void setTrack(Object track) { this.track = track; }
}
