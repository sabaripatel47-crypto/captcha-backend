package com.captcha.captcha.service;

import com.captcha.captcha.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 验证码数据的 Redis 缓存服务。
 *
 * 负责管理两类数据：
 * 1. 验证码数据（captcha:xxx）：key 为 captchaId，value 为 "targetX:创建时间戳"，2 分钟过期
 * 2. 验证 Token（verifyToken:xxx）：key 为一次性 verifyToken，value 为用户名，5 分钟过期
 *
 * 所有数据均设有过期时间，Redis 自动清理，无需手动删除。
 */
@Service
public class CaptchaCacheService {

    /** 验证码数据的 Redis key 前缀 */
    private static final String CAPTCHA_KEY_PREFIX = "captcha:";
    /** 一次性验证 Token 的 Redis key 前缀 */
    private static final String VERIFY_TOKEN_KEY_PREFIX = "verifyToken:";
    /** 验证码数据有效期：120 秒（2 分钟），超时后前端需重新获取 */
    private static final long CAPTCHA_TTL = 120;
    /** 验证 Token 有效期：300 秒（5 分钟），超时后需重新完成验证码校验 */
    private static final long VERIFY_TOKEN_TTL = 300;

    private final StringRedisTemplate redisTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    public CaptchaCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 将验证码的目标位置 targetX 存入 Redis。
     * value 格式为 "targetX:时间戳"，时间戳用于判断验证码创建时间（暂未用于业务逻辑）。
     *
     * @param captchaId 唯一标识，由 Controller 在生成验证码时生成 UUID
     * @param targetX   目标图案左上角 X 坐标
     */
    public void saveCaptcha(String captchaId, int targetX) {
        String key = CAPTCHA_KEY_PREFIX + captchaId;
        // 时间戳精确到秒，便于后续扩展（如限制同一 IP 每秒请求次数）
        String value = String.valueOf(targetX) + ":" + (System.currentTimeMillis() / 1000);
        redisTemplate.opsForValue().set(key, value, CAPTCHA_TTL, TimeUnit.SECONDS);
    }

    /**
     * 根据 captchaId 从 Redis 获取验证码数据。
     *
     * @param captchaId 验证码唯一标识
     * @return CaptchaData 对象，包含 targetX 和 createTime；若 key 不存在或已过期返回 null
     */
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

    /**
     * 删除验证码数据（一次性使用，验证完成后立即删除，防止重放攻击）。
     */
    public void removeCaptcha(String captchaId) {
        redisTemplate.delete(CAPTCHA_KEY_PREFIX + captchaId);
    }

    /**
     * 将验证通过的 verifyToken 存入 Redis。
     *
     * @param token    verifyToken（UUID），由 JwtUtil.generateVerifyToken() 生成
     * @param username 用户标识（目前固定为 "anonymous"）
     */
    public void saveVerifyToken(String token, String username) {
        String key = VERIFY_TOKEN_KEY_PREFIX + token;
        redisTemplate.opsForValue().set(key, username, VERIFY_TOKEN_TTL, TimeUnit.SECONDS);
    }

    /**
     * 消费（使用并删除）verifyToken。
     * 先查后删，保证一次性使用：验证通过后立即从 Redis 中删除 token。
     *
     * @param token verifyToken
     * @return 若 token 存在则返回存入的用户标识并删除 key；若不存在或已过期返回 null
     */
    public String consumeVerifyToken(String token) {
        String key = VERIFY_TOKEN_KEY_PREFIX + token;
        String username = redisTemplate.opsForValue().get(key);
        if (username != null) {
            redisTemplate.delete(key);
        }
        return username;
    }

    /**
     * 根据用户名生成登录 JWT Token。
     * 与 verifyToken 不同，登录 Token 有签名和过期时间，用于长期身份认证。
     */
    public String generateToken(String username) {
        return jwtUtil.generateToken(username);
    }

    /**
     * 验证码数据的内部封装类。
     * 用于在 getCaptcha() 中组装返回数据。
     */
    public static class CaptchaData {
        private int targetX;
        private long createTime;

        public int getTargetX() { return targetX; }
        public void setTargetX(int targetX) { this.targetX = targetX; }
        public long getCreateTime() { return createTime; }
        public void setCreateTime(long createTime) { this.createTime = createTime; }
    }
}
