package com.captcha.captcha.service;

import com.captcha.captcha.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class CaptchaCacheService {

    private static final String CAPTCHA_KEY_PREFIX = "captcha:";
    private static final String VERIFY_TOKEN_KEY_PREFIX = "verifyToken:";
    private static final long CAPTCHA_TTL = 120;
    private static final long VERIFY_TOKEN_TTL = 300;

    private final StringRedisTemplate redisTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    public CaptchaCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveCaptcha(String captchaId, int targetX) {
        String key = CAPTCHA_KEY_PREFIX + captchaId;
        String value = String.valueOf(targetX) + ":" + (System.currentTimeMillis() / 1000);
        redisTemplate.opsForValue().set(key, value, CAPTCHA_TTL, TimeUnit.SECONDS);
    }

    public CaptchaData getCaptcha(String captchaId) {
        String key = CAPTCHA_KEY_PREFIX + captchaId;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        String[] parts = value.split(":");
        CaptchaData data = new CaptchaData();
        data.setTargetX(Integer.parseInt(parts[0]));
        data.setCreateTime(Long.parseLong(parts[1]));
        return data;
    }

    public void removeCaptcha(String captchaId) {
        redisTemplate.delete(CAPTCHA_KEY_PREFIX + captchaId);
    }

    public void saveVerifyToken(String token, String username) {
        String key = VERIFY_TOKEN_KEY_PREFIX + token;
        redisTemplate.opsForValue().set(key, username, VERIFY_TOKEN_TTL, TimeUnit.SECONDS);
    }

    public String consumeVerifyToken(String token) {
        String key = VERIFY_TOKEN_KEY_PREFIX + token;
        String username = redisTemplate.opsForValue().get(key);
        if (username != null) {
            redisTemplate.delete(key);
        }
        return username;
    }

    public String generateToken(String username) {
        return jwtUtil.generateToken(username);
    }

    public static class CaptchaData {
        private int targetX;
        private long createTime;

        public int getTargetX() { return targetX; }
        public void setTargetX(int targetX) { this.targetX = targetX; }
        public long getCreateTime() { return createTime; }
        public void setCreateTime(long createTime) { this.createTime = createTime; }
    }
}
