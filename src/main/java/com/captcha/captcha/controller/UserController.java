package com.captcha.captcha.controller;

import com.captcha.captcha.dto.LoginRequest;
import com.captcha.captcha.entity.User;
import com.captcha.captcha.service.CaptchaCacheService;
import com.captcha.captcha.service.UserService;
import com.captcha.captcha.util.JwtUtil;
import com.captcha.captcha.vo.ResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private CaptchaCacheService captchaCacheService;

    @Autowired
    private JwtUtil jwtUtil;
    //登录接口
    @PostMapping("/login")
    public ResultVO<Map<String, String>> login(@RequestBody LoginRequest request) {
        if (request.getUsername() == null || request.getPassword() == null) {
            return ResultVO.fail("用户名和密码不能为空");
        }

        if (request.getVerifyToken() == null || request.getVerifyToken().isEmpty()) {
            return ResultVO.fail("请先完成验证码");
        }

        String username = captchaCacheService.consumeVerifyToken(request.getVerifyToken());
        if (username == null) {
            return ResultVO.fail("验证码已失效，请重新验证");
        }
        // 调用登录逻辑查询对应的用户
        User user = userService.login(request.getUsername(), request.getPassword());
        if (user == null) {
            return ResultVO.fail("用户名或密码错误");
        }
        // 创建对应的登录token
        String token = jwtUtil.generateToken(user.getUsername());

        Map<String, String> data = new HashMap<>();
        data.put("token", token);//设置token
        data.put("username", user.getUsername());//设置用户名

        return ResultVO.ok(data);
    }
    //获取用户信息
    @GetMapping("/info")
    public ResultVO<Map<String, Object>> getUserInfo(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResultVO.fail(401, "未登录");
        }

        String token = authHeader.substring(7);
        try {
            String username = jwtUtil.getUsernameFromToken(token);
            if (jwtUtil.isTokenExpired(token)) {
                return ResultVO.fail(401, "Token 已过期");
            }
            User user = userService.findByUsername(username);
            if (user == null) {
                return ResultVO.fail(404, "用户不存在");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("id", user.getId());
            data.put("username", user.getUsername());
            return ResultVO.ok(data);
        } catch (Exception e) {
            return ResultVO.fail(401, "无效的 Token");
        }
    }
}
