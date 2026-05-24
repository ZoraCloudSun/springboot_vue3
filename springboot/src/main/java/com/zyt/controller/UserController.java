package com.zyt.controller;

import com.zyt.service.UserService;
import com.zyt.utils.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 获取图形验证码 —— 无需 Token，开放访问
     * 返回 captchaId（用于校验时标识）和 base64 图片
     */
    @GetMapping("/captcha")
    public ResponseUtil captcha() {
        Map<String, String> data = userService.generateCaptcha();
        return new ResponseUtil(200, "验证码生成成功", data);
    }

    /**
     * 发送邮箱验证码 —— 需要先通过图形验证码校验
     * 60秒内同一邮箱只能发送一次，邮箱验证码5分钟有效
     */
    @PostMapping("/send-code")
    public ResponseUtil sendCode(@RequestBody Map<String, String> body) {
        return userService.sendCode(
                body.get("email"),
                body.get("captchaId"),
                body.get("captchaCode")
        );
    }

    /**
     * 邮箱验证码注册 —— 需要图形验证码 + 邮箱验证码
     * 邮箱即用户名，双重验证通过后写入数据库
     */
    @PostMapping("/register")
    public ResponseUtil register(@RequestBody Map<String, String> body) {
        return userService.register(
                body.get("email"),
                body.get("password"),
                body.get("code"),
                body.get("captchaId"),
                body.get("captchaCode")
        );
    }

    /**
     * 用户登录 —— 需要图形验证码校验
     * 验证通过后返回双 Token（accessToken + refreshToken）
     */
    @PostMapping("/login")
    public ResponseUtil login(@RequestBody Map<String, String> body) {
        return userService.login(
                body.get("username"),
                body.get("password"),
                body.get("captchaId"),
                body.get("captchaCode")
        );
    }

    @PostMapping("/logout")
    public ResponseUtil logout(@RequestHeader("Authorization") String token) {
        return userService.logout(token);
    }

    @PostMapping("/refresh")
    public ResponseUtil refresh(@RequestBody Map<String, String> body) {
        return userService.refresh(body.get("refreshToken"));
    }

    @GetMapping("/info")
    public ResponseUtil info() {
        return new ResponseUtil(200, "鉴权通过，可正常访问", null);
    }
}
