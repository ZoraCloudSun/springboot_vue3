package com.zyt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zyt.entity.User;
import com.zyt.mapper.UserMapper;
import com.zyt.service.UserService;
import com.zyt.utils.CaptchaUtil;
import com.zyt.utils.EmailUtil;
import com.zyt.utils.JwtUtil;
import com.zyt.utils.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private BCryptPasswordEncoder passwordEncoder;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private EmailUtil emailUtil;

    // Redis key 前缀
    // token: —— accessToken 存储，30min TTL
    // refresh_token: —— refreshToken 存储，7天 TTL
    // email_code: —— 邮箱验证码存储，5min TTL
    // captcha: —— 图形验证码存储，2min TTL
    private static final String TOKEN_PREFIX = "token:";
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String EMAIL_CODE_PREFIX = "email_code:";
    private static final String CAPTCHA_PREFIX = "captcha:";

    /**
     * 生成图形验证码
     *
     * @return Map 包含 captchaId 和 base64 图片
     */
    @Override
    public Map<String, String> generateCaptcha() {
        CaptchaUtil.CaptchaResult result = CaptchaUtil.generate();
        String captchaId = UUID.randomUUID().toString();
        // Redis 存储，1分钟过期
        stringRedisTemplate.opsForValue().set(CAPTCHA_PREFIX + captchaId, result.getCode(), 1, TimeUnit.MINUTES);
        Map<String, String> data = new HashMap<>();
        data.put("captchaId", captchaId);
        data.put("captchaImage", result.getBase64Image());
        return data;
    }

    /**
     * 图形验证码校验（通用）
     * 校验通过后立即删除验证码，防止重复使用
     */
    private ResponseUtil validateCaptcha(String captchaId, String captchaCode) {
        if (Objects.isNull(captchaId) || captchaId.trim().isEmpty()
                || Objects.isNull(captchaCode) || captchaCode.trim().isEmpty()) {
            return new ResponseUtil(400, "图形验证码不能为空", null);
        }
        String key = CAPTCHA_PREFIX + captchaId;
        String storedCode = stringRedisTemplate.opsForValue().get(key);
        if (Objects.isNull(storedCode)) {
            return new ResponseUtil(400, "图形验证码已过期，请刷新重试", null);
        }
        if (!storedCode.equalsIgnoreCase(captchaCode.trim())) {
            stringRedisTemplate.delete(key);
            return new ResponseUtil(400, "图形验证码错误", null);
        }
        // 验证通过，立即删除（一次性使用）
        stringRedisTemplate.delete(key);
        return null; // null 表示校验通过
    }

    /**
     * 发送邮箱验证码（需先通过图形验证码）
     */
    @Override
    public ResponseUtil sendCode(String email, String captchaId, String captchaCode) {
        // 图形验证码校验
        ResponseUtil captchaError = validateCaptcha(captchaId, captchaCode);
        if (Objects.nonNull(captchaError))
            return captchaError;

        if (Objects.isNull(email) || email.trim().isEmpty()) {
            return new ResponseUtil(400, "邮箱不能为空", null);
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            return new ResponseUtil(400, "邮箱格式不正确", null);
        }
        // 60秒防刷
        String codeKey = EMAIL_CODE_PREFIX + email;
        String existingCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (Objects.nonNull(existingCode)) {
            Long ttl = stringRedisTemplate.getExpire(codeKey);
            if (Objects.nonNull(ttl) && ttl > 240) {
                return new ResponseUtil(400, "验证码已发送，" + (ttl - 240) + " 秒后可重新获取", null);
            }
        }
        String code = String.format("%06d", (int) (Math.random() * 1000000));
        stringRedisTemplate.opsForValue().set(codeKey, code, 5, TimeUnit.MINUTES);
        emailUtil.sendVerificationCode(email, code);
        return new ResponseUtil(200, "验证码已发送至邮箱，5分钟内有效", null);
    }

    /**
     * 邮箱验证码注册（需通过图形验证码 + 邮箱验证码）
     */
    @Override
    public ResponseUtil register(String email, String password, String code,
            String captchaId, String captchaCode) {
        // 图形验证码
        ResponseUtil captchaError = validateCaptcha(captchaId, captchaCode);
        if (Objects.nonNull(captchaError))
            return captchaError;

        if (Objects.isNull(email) || email.trim().isEmpty()
                || Objects.isNull(password) || password.trim().isEmpty()
                || Objects.isNull(code) || code.trim().isEmpty()) {
            return new ResponseUtil(400, "邮箱、密码和验证码不能为空", null);
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            return new ResponseUtil(400, "邮箱格式不正确", null);
        }
        if (password.length() < 6) {
            return new ResponseUtil(400, "密码不能少于6位", null);
        }
        // 邮箱验证码
        String codeKey = EMAIL_CODE_PREFIX + email;
        String storedCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (Objects.isNull(storedCode)) {
            return new ResponseUtil(400, "验证码已过期或未发送，请重新获取", null);
        }
        if (!storedCode.equals(code.trim())) {
            return new ResponseUtil(400, "验证码不正确", null);
        }
        User exist = userMapper.selectOne(new QueryWrapper<User>().eq("username", email));
        if (Objects.nonNull(exist)) {
            return new ResponseUtil(400, "该邮箱已注册", null);
        }
        User user = new User();
        user.setUsername(email);
        user.setPassword(passwordEncoder.encode(password));
        userMapper.insert(user);
        stringRedisTemplate.delete(codeKey);
        return new ResponseUtil(200, "注册成功", null);
    }

    /**
     * 登录（需通过图形验证码）
     */
    @Override
    public ResponseUtil login(String username, String password,
            String captchaId, String captchaCode) {
        // 图形验证码
        ResponseUtil captchaError = validateCaptcha(captchaId, captchaCode);
        if (Objects.nonNull(captchaError))
            return captchaError;

        if (Objects.isNull(username) || Objects.isNull(password)) {
            return new ResponseUtil(400, "邮箱和密码不能为空", null);
        }
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (Objects.isNull(user)) {
            return new ResponseUtil(400, "用户不存在", null);
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return new ResponseUtil(400, "密码错误", null);
        }
        // 单设备登录
        String tokenKey = TOKEN_PREFIX + username;
        String refreshKey = REFRESH_TOKEN_PREFIX + username;
        String oldToken = stringRedisTemplate.opsForValue().get(tokenKey);
        if (Objects.nonNull(oldToken)) {
            stringRedisTemplate.delete(tokenKey);
            stringRedisTemplate.delete(refreshKey);
        }
        String accessToken = jwtUtil.generateAccessToken(username);
        String refreshToken = jwtUtil.generateRefreshToken(username);
        stringRedisTemplate.opsForValue().set(tokenKey, accessToken, 30, TimeUnit.MINUTES);
        stringRedisTemplate.opsForValue().set(refreshKey, refreshToken, 7, TimeUnit.DAYS);
        Map<String, String> data = new HashMap<>();
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        return new ResponseUtil(200, "登录成功", data);
    }

    @Override
    public ResponseUtil logout(String token) {
        String username;
        try {
            username = jwtUtil.getUsernameFromToken(token);
        } catch (Exception e) {
            return new ResponseUtil(400, "Token 无效", null);
        }
        stringRedisTemplate.delete(TOKEN_PREFIX + username);
        stringRedisTemplate.delete(REFRESH_TOKEN_PREFIX + username);
        return new ResponseUtil(200, "登出成功", null);
    }

    @Override
    public ResponseUtil refresh(String refreshToken) {
        if (Objects.isNull(refreshToken) || refreshToken.trim().isEmpty()) {
            return new ResponseUtil(400, "refreshToken 不能为空", null);
        }
        if (!jwtUtil.validateToken(refreshToken)) {
            return new ResponseUtil(401, "refreshToken 无效或已过期，请重新登录", null);
        }
        String username = jwtUtil.getUsernameFromToken(refreshToken);
        String refreshKey = REFRESH_TOKEN_PREFIX + username;
        String storedRefreshToken = stringRedisTemplate.opsForValue().get(refreshKey);
        if (Objects.isNull(storedRefreshToken) || !storedRefreshToken.equals(refreshToken)) {
            return new ResponseUtil(401, "refreshToken 已失效，请重新登录", null);
        }
        String newAccessToken = jwtUtil.generateAccessToken(username);
        stringRedisTemplate.opsForValue().set(TOKEN_PREFIX + username, newAccessToken, 30, TimeUnit.MINUTES);
        Map<String, String> data = new HashMap<>();
        data.put("accessToken", newAccessToken);
        return new ResponseUtil(200, "Token 刷新成功", data);
    }
}
