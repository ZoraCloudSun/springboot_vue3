package com.zyt.utils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Random;

/**
 * 图形验证码生成工具
 * 6位大写字母，旋转扭曲 + 颜色偏移 + 多层干扰 + 边缘增强，肉眼可辨而机器难以识别
 *
 * 防攻击设计：
 * - 每个字符独立随机旋转 ±25°、随机字号(24-30)、随机Y轴偏移
 * - 40个干扰点 + 40个干扰弧线 + 5条干扰直线 + 边缘增强滤镜（参照 Python PIL 逻辑）
 * - 字符库纯大写 A-Z，天然无混淆字符
 * - 验证码 Redis 1分钟 TTL，一次性使用
 */
public class CaptchaUtil {

    private static final int WIDTH = 150;
    private static final int HEIGHT = 30;
    private static final int CHAR_LENGTH = 6;
    private static final Random RANDOM = new Random();

    public static class CaptchaResult {
        private final String code;
        private final String base64Image;

        public CaptchaResult(String code, String base64Image) {
            this.code = code;
            this.base64Image = base64Image;
        }

        public String getCode() {
            return code;
        }

        public String getBase64Image() {
            return base64Image;
        }
    }

    private static char rndChar() {
        return (char) (65 + RANDOM.nextInt(26));
    }

    private static Color rndColor() {
        return new Color(
                RANDOM.nextInt(256),
                10 + RANDOM.nextInt(246),
                64 + RANDOM.nextInt(192));
    }

    private static Color rndLightColor() {
        return new Color(
                230 + RANDOM.nextInt(26),
                230 + RANDOM.nextInt(26),
                230 + RANDOM.nextInt(26));
    }

    public static CaptchaResult generate() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. 浅色背景
        g.setColor(rndLightColor());
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 2. 写文字 —— 每个字符独立旋转、独立颜色、独立Y轴偏移
        StringBuilder code = new StringBuilder();
        String[] fontNames = { "Arial", "Helvetica", "SansSerif" };
        int[] fontStyles = { Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC };
        float[] fontSizes = { 17f, 18f, 19f, 20f };
        int charStep = WIDTH / CHAR_LENGTH;

        for (int i = 0; i < CHAR_LENGTH; i++) {
            char ch = rndChar();
            code.append(ch);

            // 随机字体/样式/字号
            g.setFont(new Font(
                    fontNames[RANDOM.nextInt(fontNames.length)],
                    fontStyles[RANDOM.nextInt(fontStyles.length)],
                    (int) fontSizes[RANDOM.nextInt(fontSizes.length)]));
            g.setColor(rndColor());

            // 随机旋转 ±25°
            int cx = charStep * i + charStep / 2;
            int cy = HEIGHT / 2;
            double angle = (RANDOM.nextDouble() - 0.5) * Math.PI / 3.6;
            g.rotate(angle, cx, cy);

            // 随机Y轴偏移 + 微调X，让字符上下起伏、间距不规则
            int yOffset = RANDOM.nextInt(8) - 4;
            int xOffset = RANDOM.nextInt(4) - 2;
            g.drawString(String.valueOf(ch), charStep * i + 4 + xOffset, HEIGHT / 2 + 6 + yOffset);

            g.rotate(-angle, cx, cy);
        }

        // 3. 写干扰点（40个）—— 参照 Python
        for (int i = 0; i < 40; i++) {
            g.setColor(rndColor());
            g.fillRect(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT), 1, 1);
        }

        // 4. 写干扰弧线（40个）—— 参照 Python draw.arc
        for (int i = 0; i < 40; i++) {
            g.setColor(rndColor());
            int x = RANDOM.nextInt(WIDTH);
            int y = RANDOM.nextInt(HEIGHT);
            g.drawArc(x, y, 4, 4, RANDOM.nextInt(360), 90);
        }

        // 5. 画干扰直线（5条）—— 参照 Python draw.line
        for (int i = 0; i < 5; i++) {
            g.setColor(rndColor());
            g.drawLine(
                    RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT),
                    RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT));
        }

        g.dispose();

        // 6. 边缘增强滤镜 —— 参照 Python img.filter(ImageFilter.EDGE_ENHANCE_MORE)
        image = edgeEnhance(image);

        // 7. 输出 Base64
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            String base64 = "data:image/png;base64," +
                    Base64.getEncoder().encodeToString(baos.toByteArray());
            return new CaptchaResult(code.toString(), base64);
        } catch (Exception e) {
            throw new RuntimeException("验证码图片生成失败", e);
        }
    }

    /**
     * 边缘增强 —— 拉普拉斯卷积核，让字符轮廓更清晰，同时增加机器识别难度
     */
    private static BufferedImage edgeEnhance(BufferedImage src) {
        float[] edgeKernel = {
                -0.25f, -0.25f, -0.25f,
                -0.25f, 3.0f, -0.25f,
                -0.25f, -0.25f, -0.25f
        };
        Kernel kernel = new Kernel(3, 3, edgeKernel);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        return op.filter(src, null);
    }
}
