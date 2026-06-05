package com.zyt.utils;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtUtil JWT 工具类测试")
class JwtUtilTest {

    private static final String TEST_SECRET = "this-is-a-test-secret-key-with-at-least-256-bits-for-hmac-sha256!!";
    private static final long ACCESS_EXPIRATION = 1_800_000L;  // 30 min
    private static final long REFRESH_EXPIRATION = 604_800_000L; // 7 days

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", ACCESS_EXPIRATION);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpiration", REFRESH_EXPIRATION);
    }

    // ==================== generateAccessToken ====================

    @Test
    @DisplayName("generateAccessToken：生成非空、非空字符串")
    void shouldGenerateNonNullAccessToken() {
        String token = jwtUtil.generateAccessToken("user@test.com", "user");
        assertNotNull(token);
        assertFalse(token.trim().isEmpty());
    }

    @Test
    @DisplayName("generateAccessToken：subject 为传入的 username")
    void shouldSetCorrectSubject() {
        String token = jwtUtil.generateAccessToken("alice@example.com", "admin");
        assertEquals("alice@example.com", jwtUtil.getUsernameFromToken(token));
    }

    @Test
    @DisplayName("generateAccessToken：包含 role claim")
    void shouldContainRoleClaim() {
        String token = jwtUtil.generateAccessToken("bob@test.com", "admin");
        assertEquals("admin", jwtUtil.getRoleFromToken(token));
    }

    @Test
    @DisplayName("generateAccessToken：type=access")
    void shouldHaveAccessTypeClaim() {
        String token = jwtUtil.generateAccessToken("test@test.com", "user");
        assertTrue(jwtUtil.isAccessToken(token));
        assertFalse(jwtUtil.isRefreshToken(token));
    }

    // ==================== generateRefreshToken ====================

    @Test
    @DisplayName("generateRefreshToken：type=refresh")
    void shouldHaveRefreshTypeClaim() {
        String token = jwtUtil.generateRefreshToken("test@test.com", "user");
        assertTrue(jwtUtil.isRefreshToken(token));
        assertFalse(jwtUtil.isAccessToken(token));
    }

    @Test
    @DisplayName("generateRefreshToken：subject 正确")
    void shouldSetCorrectSubjectInRefreshToken() {
        String token = jwtUtil.generateRefreshToken("carol@test.com", "user");
        assertEquals("carol@test.com", jwtUtil.getUsernameFromToken(token));
    }

    @Test
    @DisplayName("generateRefreshToken：包含 role claim")
    void shouldContainRoleClaimInRefreshToken() {
        String token = jwtUtil.generateRefreshToken("dave@test.com", "admin");
        assertEquals("admin", jwtUtil.getRoleFromToken(token));
    }

    // ==================== generateToken (兼容) ====================

    @Test
    @DisplayName("generateToken：等效于 generateAccessToken")
    void shouldGenerateTokenEquivalentToAccessToken() {
        String token1 = jwtUtil.generateToken("eve@test.com", "user");
        String token2 = jwtUtil.generateAccessToken("eve@test.com", "user");
        // 时间戳可能略有差异，但 type claim 应一致
        assertTrue(jwtUtil.isAccessToken(token1));
        assertTrue(jwtUtil.isAccessToken(token2));
        assertEquals(jwtUtil.getUsernameFromToken(token1), jwtUtil.getUsernameFromToken(token2));
    }

    // ==================== getUsernameFromToken ====================

    @Test
    @DisplayName("getUsernameFromToken：正确提取 subject")
    void shouldExtractUsername() {
        String token = jwtUtil.generateAccessToken("frank@test.com", "user");
        assertEquals("frank@test.com", jwtUtil.getUsernameFromToken(token));
    }

    // ==================== getRoleFromToken ====================

    @Test
    @DisplayName("getRoleFromToken：正确提取 role claim")
    void shouldExtractRole() {
        String token = jwtUtil.generateAccessToken("grace@test.com", "admin");
        assertEquals("admin", jwtUtil.getRoleFromToken(token));
    }

    // ==================== validateToken ====================

    @Test
    @DisplayName("validateToken：合法 token 返回 true")
    void shouldValidateLegitimateToken() {
        String token = jwtUtil.generateAccessToken("hank@test.com", "user");
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("validateToken：篡改 token 返回 false")
    void shouldRejectTamperedToken() {
        String token = jwtUtil.generateAccessToken("iris@test.com", "user");
        String tampered = token.substring(0, token.length() - 3) + "xxx";
        assertFalse(jwtUtil.validateToken(tampered));
    }

    @Test
    @DisplayName("validateToken：空 token 返回 false")
    void shouldRejectEmptyToken() {
        assertFalse(jwtUtil.validateToken(""));
        assertFalse(jwtUtil.validateToken(null));
    }

    // ==================== isTokenExpired ====================

    @Test
    @DisplayName("isTokenExpired：未过期 token 返回 false")
    void shouldNotBeExpired_forFreshToken() {
        String token = jwtUtil.generateAccessToken("jack@test.com", "user");
        assertFalse(jwtUtil.isTokenExpired(token));
    }

    @Test
    @DisplayName("isTokenExpired：已过期 token 返回 true")
    void shouldBeExpired_forExpiredToken() {
        // 使用 jjwt API 直接构建一个已过期的 token
        SecretKey key = new SecretKeySpec(
                TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        Date pastExpiration = new Date(System.currentTimeMillis() - 10_000);
        String expiredToken = Jwts.builder()
                .subject("expired@test.com")
                .claim("type", "access")
                .claim("role", "user")
                .issuedAt(new Date(System.currentTimeMillis() - 20_000))
                .expiration(pastExpiration)
                .signWith(key)
                .compact();

        assertTrue(jwtUtil.isTokenExpired(expiredToken));
    }

    // ==================== isAccessToken / isRefreshToken ====================

    @Test
    @DisplayName("type 区分：access token 和 refresh token 不混淆")
    void shouldDistinguishTokenTypes() {
        String accessToken = jwtUtil.generateAccessToken("kate@test.com", "user");
        String refreshToken = jwtUtil.generateRefreshToken("kate@test.com", "user");

        // access token
        assertTrue(jwtUtil.isAccessToken(accessToken));
        assertFalse(jwtUtil.isRefreshToken(accessToken));

        // refresh token
        assertTrue(jwtUtil.isRefreshToken(refreshToken));
        assertFalse(jwtUtil.isAccessToken(refreshToken));
    }

    @Test
    @DisplayName("isRefreshToken：无效 token 返回 false")
    void shouldReturnFalseForInvalidToken() {
        assertFalse(jwtUtil.isRefreshToken("invalid-token"));
        assertFalse(jwtUtil.isRefreshToken(""));
    }

    @Test
    @DisplayName("isAccessToken：无效 token 返回 false")
    void shouldReturnFalseForInvalidAccessToken() {
        assertFalse(jwtUtil.isAccessToken("invalid-token"));
        assertFalse(jwtUtil.isAccessToken(""));
    }
}
