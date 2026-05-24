package com.zyt.service;

import com.zyt.utils.ResponseUtil;

import java.util.Map;

public interface UserService {
    Map<String, String> generateCaptcha();
    ResponseUtil sendCode(String email, String captchaId, String captchaCode);
    ResponseUtil register(String email, String password, String code, String captchaId, String captchaCode);
    ResponseUtil login(String username, String password, String captchaId, String captchaCode);
    ResponseUtil logout(String token);
    ResponseUtil refresh(String refreshToken);
}
