package com.captcha.captcha.dto;

/**
 * 登录请求的请求参数
 *
 * - username: 用户名
 * - password: 密码
 * - verifyToken：验证码验证通过后的token
 */
public class LoginRequest {
    private String username;
    private String password;
    private String verifyToken;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getVerifyToken() {
        return verifyToken;
    }

    public void setVerifyToken(String verifyToken) {
        this.verifyToken = verifyToken;
    }
}
