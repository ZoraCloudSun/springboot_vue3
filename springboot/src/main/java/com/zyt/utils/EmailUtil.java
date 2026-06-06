package com.zyt.utils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 邮件发送工具类
 * 使用网易163邮箱 SMTP 服务发送验证码邮件
 *
 * 使用前需在 .env（Docker）或 application.yml（手动）中配置正确的163邮箱和SMTP授权码：
 * - 登录 mail.163.com → 设置 → POP3/SMTP/IMAP → 开启SMTP服务 → 获取授权码
 * - 将授权码填入 MAIL_PASSWORD（注意不是邮箱登录密码）
 * - Docker 部署：在 .env 中设置 MAIL_USERNAME 和 MAIL_PASSWORD
 */
@Component
public class EmailUtil {

    private static final Logger log = LoggerFactory.getLogger(EmailUtil.class);

    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    /**
     * 启动时检查邮件配置
     * 如果凭据为空，在日志中输出明确警告，方便 Docker 部署时快速定位
     */
    @PostConstruct
    public void checkMailConfig() {
        if (from == null || from.isBlank()) {
            log.warn("================================================");
            log.warn("⚠ 邮件凭据未配置！");
            log.warn("  注册验证码、密码重置等邮件功能将不可用。");
            log.warn("  配置方法：在 .env 文件中设置 MAIL_USERNAME 和 MAIL_PASSWORD");
            log.warn("  - MAIL_USERNAME: 你的163邮箱地址（如 your@163.com）");
            log.warn("  - MAIL_PASSWORD: SMTP 授权码（非登录密码，在 mail.163.com 设置中获取）");
            log.warn("================================================");
        } else {
            log.info("邮件服务已配置，发件人: {}", from);
        }
    }

    /**
     * 异步发送邮箱验证码
     * 新线程发送，避免 SMTP 连接耗时阻塞 HTTP 响应导致前端超时报"网络错误"
     * 
     * @param to   收件人邮箱地址
     * @param code 6位数字验证码
     */
    public void sendVerificationCode(String to, String code) {
        new Thread(() -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(to);
                message.setSubject("Spring Boot Auth — 邮箱验证码");
                message.setText(String.format("""
                        您好！

                        您的验证码是：%s

                        该验证码 5 分钟内有效，请勿泄露给他人。
                        如非本人操作，请忽略此邮件。

                        —— Spring Boot Auth 认证系统
                        """, code));
                mailSender.send(message);
            } catch (Exception e) {
                log.error("邮件发送失败，收件人: {}", to, e);
            }
        }).start();
    }

    /**
     * 异步发送密码重置验证码
     * 与注册验证码使用独立的邮件模板（标题和正文不同），避免用户混淆
     *
     * @param to   收件人邮箱地址
     * @param code 6位数字验证码
     */
    public void sendResetCode(String to, String code) {
        new Thread(() -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(to);
                message.setSubject("Spring Boot Auth — 密码重置验证码");
                message.setText(String.format("""
                        您好！

                        您正在申请重置密码，验证码是：%s

                        该验证码 5 分钟内有效，请勿泄露给他人。
                        如非本人操作，请忽略此邮件。

                        —— Spring Boot Auth 认证系统
                        """, code));
                mailSender.send(message);
            } catch (Exception e) {
                log.error("邮件发送失败，收件人: {}", to, e);
            }
        }).start();
    }
}
