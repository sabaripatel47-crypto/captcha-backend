package com.captcha.captcha.dto;

public class CaptchaVerifyRequest {
    private String captchaId;
    private Integer offsetX;
    private Object track;

    public String getCaptchaId() {
        return captchaId;
    }

    public void setCaptchaId(String captchaId) {
        this.captchaId = captchaId;
    }

    public Integer getOffsetX() {
        return offsetX;
    }

    public void setOffsetX(Integer offsetX) {
        this.offsetX = offsetX;
    }

    public Object getTrack() {
        return track;
    }

    public void setTrack(Object track) {
        this.track = track;
    }
}
