package com.zyt.config;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Resource
    private LoginInterceptor loginInterceptor;

    @Resource
    private RoleInterceptor roleInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Knife4j / Swagger 文档路径（无需认证）
        String[] swaggerPaths = {"/doc.html", "/webjars/**", "/v3/api-docs/**",
                "/swagger-ui/**", "/swagger-resources/**", "/favicon.ico", "/actuator/**"};

        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/user/login", "/user/register", "/user/refresh", "/user/send-code",
                        "/user/send-bind-code",
                        "/user/captcha",
                        "/user/forgot-password/send-code", "/user/forgot-password/reset",
                        "/user/wechat/qrcode", "/user/wechat/check",
                        "/user/wechat/callback", "/user/wechat/bind-email")
                .excludePathPatterns(swaggerPaths);

        // RoleInterceptor 在 LoginInterceptor 之后执行，读取其设置的 request attributes
        registry.addInterceptor(roleInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/user/login", "/user/register", "/user/refresh", "/user/send-code",
                        "/user/send-bind-code",
                        "/user/captcha",
                        "/user/forgot-password/send-code", "/user/forgot-password/reset",
                        "/user/wechat/qrcode", "/user/wechat/check",
                        "/user/wechat/callback", "/user/wechat/bind-email")
                .excludePathPatterns(swaggerPaths);
    }
}
