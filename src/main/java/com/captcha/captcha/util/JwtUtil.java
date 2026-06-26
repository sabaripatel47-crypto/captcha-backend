package com.captcha.captcha.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类，负责 Token 的生成与解析。
 *
 * 提供两类 Token：
 * 1. generateToken(username)：带签名的登录 Token，包含用户名和过期时间，用于长期身份认证
 * 2. generateVerifyToken()：无签名 UUID，作为验证码校验通过后的一次性验证凭证（存入 Redis）
 *
 * JWT 密钥和过期时间从配置文件读取（captcha.jwt.secret / captcha.jwt.expire）。
 */
@Component
public class JwtUtil {

    /** JWT 签名密钥，从配置文件注入（需至少 32 字节） */
    @Value("${captcha.jwt.secret}")
    private String secret;

    /** Token 过期时间（秒），从配置文件注入 */
    @Value("${captcha.jwt.expire}")
    private Integer expire;

    /** 构建 HMAC-SHA256 签名密钥 */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成带签名的登录 Token。
     * Token 中包含用户名（subject）和自定义 claims，过期后需重新登录。
     *
     * @param username 登录用户名
     * @return JWT 字符串，可直接返回给前端存储
     */
    public String generateToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        return Jwts.builder()
                .setClaims(claims)                        // 自定义 claims
                .setSubject(username)                     // 主题（用户名）
                .setIssuedAt(new Date())                  // 签发时间
                .setExpiration(new Date(System.currentTimeMillis() + expire * 1000L)) // 过期时间
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 生成一次性验证码验证 Token（UUID）。
     * 此方法不使用 JWT 签名，而是直接生成无序 UUID 存入 Redis 作为一次性凭证。
     * 相比 JWT，UUID 更轻量且天然支持一次性消费（查后即删）。
     */
    public String generateVerifyToken() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 解析 JWT Token 并返回 Claims（包含用户信息和过期时间）。
     *
     * @param token JWT 字符串
     * @return Claims 对象，包含 subject、claims、过期时间等
     * @throws Exception 若 Token 被篡改或签名不匹配则抛出异常
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 从 JWT Token 中提取用户名（subject）。
     */
    public String getUsernameFromToken(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * 判断 Token 是否已过期。
     *
     * @param token JWT 字符串
     * @return true 表示已过期或解析失败，false 表示有效
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = parseToken(token).getExpiration();
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
