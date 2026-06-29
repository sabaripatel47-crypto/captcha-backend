package com.captcha.captcha.service;

import com.captcha.captcha.entity.User;
import com.captcha.captcha.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    public User login(String username, String password) {
        User user = userMapper.findByUsername(username);
        //将查找到的用户密码和输入密码进行比较
        if (user != null && user.getPassword().equals(password)) {
            return user;
        }
        return null;
    }
}
