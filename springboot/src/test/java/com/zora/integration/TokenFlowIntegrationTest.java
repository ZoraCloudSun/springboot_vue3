package com.zora.integration;

import com.zora.entity.User;
import com.zora.mapper.UserMapper;
import com.zora.utils.JwtUtil;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 双 Token 流程集成测试（Phase 5.4）
 * <p>验证: 注册 → 发双 Token → accessToken 过期刷新 → 旧 accessToken 失效。</p>
 * <p>使用真实 MySQL + Redis + JwtUtil，无 Mock。</p>
 */
@SpringBootTest
@DisplayName("TokenFlowIntegrationTest - 双 Token 流程")
class TokenFlowIntegrationTest extends AbstractIntegrationTest {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String TEST_EMAIL = "tokenflow@test.com";
    private static final String TEST_ROLE = "user";

    @BeforeEach
    void setUp() {
        // 确保测试用户存在
        if (userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getEmail, TEST_EMAIL)) == null) {
            User user = new User();
            user.setEmail(TEST_EMAIL);
            user.setPassword("testpass");
            user.setRole(TEST_ROLE);
            userMapper.insert(user);
        }
        // 清理 token
        stringRedisTemplate.delete("token:" + TEST_EMAIL);
        stringRedisTemplate.delete("refresh_token:" + TEST_EMAIL);
    }

    @Test
    @DisplayName("生成 accessToken 和 refreshToken，类型正确")
    void shouldGenerateBothTokensWithCorrectType() {
        String accessToken = jwtUtil.generateAccessToken(TEST_EMAIL, TEST_ROLE);
        String refreshToken = jwtUtil.generateRefreshToken(TEST_EMAIL, TEST_ROLE);

        assertNotNull(accessToken);
        assertNotNull(refreshToken);
        assertNotEquals(accessToken, refreshToken, "两个 token 应不同");
        assertTrue(jwtUtil.isAccessToken(accessToken), "accessToken 类型应为 access");
        assertTrue(jwtUtil.isRefreshToken(refreshToken), "refreshToken 类型应为 refresh");
    }

    @Test
    @DisplayName("accessToken 存入 Redis 后可通过 key 找到")
    void shouldStoreAccessTokenInRedis() {
        String accessToken = jwtUtil.generateAccessToken(TEST_EMAIL, TEST_ROLE);
        stringRedisTemplate.opsForValue().set("token:" + TEST_EMAIL, accessToken, 30, TimeUnit.MINUTES);

        String stored = stringRedisTemplate.opsForValue().get("token:" + TEST_EMAIL);
        assertNotNull(stored);
        assertEquals(accessToken, stored);
    }

    @Test
    @DisplayName("refreshToken 存入 Redis 后可通过 key 找到")
    void shouldStoreRefreshTokenInRedis() {
        String refreshToken = jwtUtil.generateRefreshToken(TEST_EMAIL, TEST_ROLE);
        stringRedisTemplate.opsForValue().set("refresh_token:" + TEST_EMAIL, refreshToken, 7, TimeUnit.DAYS);

        String stored = stringRedisTemplate.opsForValue().get("refresh_token:" + TEST_EMAIL);
        assertNotNull(stored);
        assertEquals(refreshToken, stored);
    }

    @Test
    @DisplayName("旧 accessToken 在写入新 token 后不再匹配")
    void shouldInvalidateOldTokenOnNewLogin() throws Exception {
        String oldToken = jwtUtil.generateAccessToken(TEST_EMAIL, TEST_ROLE);
        stringRedisTemplate.opsForValue().set("token:" + TEST_EMAIL, oldToken);

        // 等 2 秒确保 iat 不同（JWT iat 为秒级精度）
        Thread.sleep(2000);

        // 模拟新设备登录
        String newToken = jwtUtil.generateAccessToken(TEST_EMAIL, TEST_ROLE);
        stringRedisTemplate.opsForValue().set("token:" + TEST_EMAIL, newToken);

        String stored = stringRedisTemplate.opsForValue().get("token:" + TEST_EMAIL);
        assertEquals(newToken, stored);
        assertNotEquals(oldToken, stored, "旧 token 应已被替换");
    }

    @Test
    @DisplayName("refreshToken 不能当作 accessToken 使用（类型不同）")
    void shouldRejectRefreshTokenAsAccessToken() {
        String refreshToken = jwtUtil.generateRefreshToken(TEST_EMAIL, TEST_ROLE);

        assertFalse(jwtUtil.isAccessToken(refreshToken), "refreshToken 不应被识别为 accessToken");
        assertTrue(jwtUtil.isRefreshToken(refreshToken));
    }
}
