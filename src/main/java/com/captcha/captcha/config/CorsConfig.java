package com.captcha.captcha.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 跨域请求配置。
 *
 * 由于前后端分离架构中前端（http://localhost:xxxx）和后端（http://localhost:8080）端口不同，
 * 浏览器同源策略会阻止前端直接请求后端接口。此配置通过 CORS（Cross-Origin Resource Sharing）
 * 允许前端页面跨域访问后端 API。
 *
 * 配置规则：
 * - allowCredentials(true)：允许携带 Cookie 和 Authorization Header
 * - allowedOriginPattern("*")：允许所有来源（生产环境建议限制为具体域名）
 * - allowedHeader("*")：允许所有请求头
 * - allowedMethod("*")：允许所有 HTTP 方法（GET/POST/PUT/DELETE 等）
 * - maxAge(3600L)：预检请求（OPTIONS）的缓存时间，减少OPTIONS请求次数
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许携带认证信息（Cookie、Authorization Header）
        config.setAllowCredentials(true);
        // 允许所有来源（"*" 支持通配符，如 "http://*.example.com"）
        config.addAllowedOriginPattern("*");
        // 允许所有请求头
        config.addAllowedHeader("*");
        // 允许所有 HTTP 方法
        config.addAllowedMethod("*");
        // 预检请求缓存时间：3600 秒，减少 OPTIONS 预检次数
        config.setMaxAge(3600L);

        // 将以上配置注册到所有路径
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
