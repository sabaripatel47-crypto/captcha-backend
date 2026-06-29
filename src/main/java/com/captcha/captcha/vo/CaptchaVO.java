package com.captcha.captcha.vo;

/**
 * 验证码获取接口的返回值
 *
 * 前端调用 GET /api/captcha/get 后，服务端返回此类实例
 */
public class CaptchaVO {
    /** 验证码唯一标识 UUID，用于获取redis对应的存储数据 */
    private String captchaId;
    /** 验证码图片的 Base64 编码，格式为 "data:image/png;base64,xxxxx" */
    private String imageBase64;

    public CaptchaVO() {}

    public CaptchaVO(String captchaId, String imageBase64) {
        this.captchaId = captchaId;
        this.imageBase64 = imageBase64;
    }

    public String getCaptchaId() { return captchaId; }
    public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }
    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }
}
