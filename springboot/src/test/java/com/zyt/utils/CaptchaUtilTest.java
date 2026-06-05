package com.zyt.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CaptchaUtil 图形验证码测试")
class CaptchaUtilTest {

    @Test
    @DisplayName("生成验证码：返回非 null CaptchaResult")
    void shouldReturnNonNullResult() {
        CaptchaUtil.CaptchaResult result = CaptchaUtil.generate();
        assertNotNull(result);
        assertNotNull(result.getCode());
        assertNotNull(result.getBase64Image());
    }

    @Test
    @DisplayName("生成验证码：验证码长度为 6")
    void shouldGenerateCodeWithLengthSix() {
        CaptchaUtil.CaptchaResult result = CaptchaUtil.generate();
        assertEquals(6, result.getCode().length());
    }

    @Test
    @DisplayName("生成验证码：仅包含大写字母 A-Z")
    void shouldContainOnlyUppercaseLetters() {
        CaptchaUtil.CaptchaResult result = CaptchaUtil.generate();
        for (char c : result.getCode().toCharArray()) {
            assertTrue(c >= 'A' && c <= 'Z',
                    "验证码字符应为 A-Z，实际包含: " + c);
        }
    }

    @Test
    @DisplayName("生成验证码：Base64 图片以 data:image/png;base64, 开头")
    void shouldStartWithBase64PngPrefix() {
        CaptchaUtil.CaptchaResult result = CaptchaUtil.generate();
        assertTrue(result.getBase64Image().startsWith("data:image/png;base64,"),
                "Base64 图片应以 data:image/png;base64, 开头");
    }

    @Test
    @DisplayName("多次生成：验证码不相同（随机性校验）")
    void shouldGenerateDifferentCodes() {
        CaptchaUtil.CaptchaResult r1 = CaptchaUtil.generate();
        CaptchaUtil.CaptchaResult r2 = CaptchaUtil.generate();
        // 极小概率相同（1/26^6），几乎不可能
        assertNotEquals(r1.getCode(), r2.getCode(),
                "两次生成的验证码不应相同");
    }

    @Test
    @DisplayName("生成验证码：Base64 图片非空且长度合理")
    void shouldGenerateNonEmptyBase64Image() {
        CaptchaUtil.CaptchaResult result = CaptchaUtil.generate();
        assertNotNull(result.getBase64Image());
        // Base64 编码的 PNG 至少应该有几百字节
        assertTrue(result.getBase64Image().length() > 100,
                "Base64 图片长度应 > 100");
    }
}
