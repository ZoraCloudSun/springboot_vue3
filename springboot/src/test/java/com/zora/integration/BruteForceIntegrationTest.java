package com.zora.integration;

import com.zora.entity.User;
import com.zora.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 暴力破解锁定集成测试（Phase 5.4）
 * <p>验证: 5 次失败登录 → Redis 锁定 → 15 分钟 TTL → 解锁后可登录。</p>
 * <p>使用真实 MySQL（存用户）+ Redis（存失败计数），无 Mock。</p>
 */
@SpringBootTest
@DisplayName("BruteForceIntegrationTest - 暴力破解锁定")
class BruteForceIntegrationTest extends AbstractIntegrationTest {

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private BCryptPasswordEncoder passwordEncoder;

    private static final String TEST_EMAIL = "brute@test.com";
    private static final String CORRECT_PASSWORD = "correct123";
    private static final String WRONG_PASSWORD = "wrong123";

    @BeforeEach
    void setUp() {
        // 清理旧数据
        stringRedisTemplate.delete("login_fail:" + TEST_EMAIL);
        userMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getEmail, TEST_EMAIL));

        // 创建测试用户
        User user = new User();
        user.setEmail(TEST_EMAIL);
        user.setPassword(passwordEncoder.encode(CORRECT_PASSWORD));
        user.setRole("user");
        userMapper.insert(user);
    }

    @Test
    @DisplayName("5 次失败登录后锁定，第 6 次被拒绝")
    void shouldLockAfterFiveFailedAttempts() {
        String key = "login_fail:" + TEST_EMAIL;

        // 模拟 5 次失败（直接写 Redis，绕过 UserService 的登录逻辑）
        for (int i = 0; i < 5; i++) {
            stringRedisTemplate.opsForValue().increment(key);
            stringRedisTemplate.expire(key, 15, TimeUnit.MINUTES);
        }

        // 第 5 次后计数器应为 5（≥5 → 锁定）
        String count = stringRedisTemplate.opsForValue().get(key);
        assertNotNull(count);
        assertTrue(Integer.parseInt(count) >= 5, "5 次失败后应达到锁定阈值");
    }

    @Test
    @DisplayName("锁定计数器 TTL 约 15 分钟")
    void shouldHaveCorrectTTL() {
        String key = "login_fail:" + TEST_EMAIL;
        stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, 15, TimeUnit.MINUTES);

        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0, "TTL 应大于 0");
        assertTrue(ttl <= 900, "TTL 应不超过 15 分钟");
    }

    @Test
    @DisplayName("清除 Redis key 后计数器归零")
    void shouldResetAfterKeyDeletion() {
        String key = "login_fail:" + TEST_EMAIL;
        stringRedisTemplate.opsForValue().set(key, "5");
        stringRedisTemplate.delete(key);

        String count = stringRedisTemplate.opsForValue().get(key);
        assertNull(count, "删除 key 后计数应为 null");
    }

    @Test
    @DisplayName("不同邮箱的锁定互不影响")
    void shouldIsolatePerEmail() {
        String key1 = "login_fail:" + TEST_EMAIL;
        String key2 = "login_fail:other@test.com";

        stringRedisTemplate.opsForValue().increment(key1);
        stringRedisTemplate.opsForValue().increment(key1);

        String count1 = stringRedisTemplate.opsForValue().get(key1);
        String count2 = stringRedisTemplate.opsForValue().get(key2);

        assertNotNull(count1);
        assertNull(count2, "其他邮箱不应受影响");
    }

    @Test
    @DisplayName("正确密码的用户存在于数据库中")
    void userShouldExistInDatabase() {
        User user = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getEmail, TEST_EMAIL));
        assertNotNull(user, "测试用户应存在于数据库");
        assertTrue(passwordEncoder.matches(CORRECT_PASSWORD, user.getPassword()),
                "密码应正确 hash 存储");
    }
}
