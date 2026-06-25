package com.captcha.captcha.vo;

public class CaptchaVO {
    private String captchaId;
    private String imageBase64;

    public CaptchaVO() {}

    public CaptchaVO(String captchaId, String imageBase64) {
        this.captchaId = captchaId;
        this.imageBase64 = imageBase64;
    }

    public String getCaptchaId() {
        return captchaId;
    }

    public void setCaptchaId(String captchaId) {
        this.captchaId = captchaId;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }
}
