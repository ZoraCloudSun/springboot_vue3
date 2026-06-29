package com.zora.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zora.entity.User;
import com.zora.mapper.UserMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 用户上下文工具类
 * <p>
 * 从当前 HTTP 请求中提取用户身份信息（email/role/userId），并缓存 userId 查询结果。
 * 消除各 Service 中重复的 {@code private User findUserByEmail(String email)} 私有方法。
 * </p>
 *
 * <p><b>缓存策略:</b> 优先从 request attribute 获取（LoginInterceptor 已设置 userId/email/role），
 * 免查库。无 request context 时（如定时任务），自行查询 UserMapper。</p>
 *
 * <p><b>ThreadLocal 缓存:</b> 同一请求内多次调用 getUserId() 最多查一次库。
 * Spring MVC 每请求一线程模型下，无需显式清理。</p>
 *
 * @see com.zora.config.auth.LoginInterceptor
 */
@Component
public class UserContext {

    @Resource
    private UserMapper userMapper;

    private final ThreadLocal<String> emailCache = new ThreadLocal<>();
    private final ThreadLocal<Integer> userIdCache = new ThreadLocal<>();
    private final ThreadLocal<String> roleCache = new ThreadLocal<>();

    /**
     * 获取当前请求用户的 email
     * @return 用户邮箱，无登录上下文时返回 null
     */
    public String getEmail() {
        String cached = emailCache.get();
        if (cached != null) return cached;

        HttpServletRequest request = getCurrentRequest();
        if (request == null) return null;

        String email = (String) request.getAttribute("userEmail");
        if (email != null) emailCache.set(email);
        return email;
    }

    /**
     * 获取当前请求用户的 ID
     * <p>优先从 request attribute "userId" 获取（LoginInterceptor 已设置，免查库）；
     * 其次自行查库并缓存。</p>
     *
     * @return 用户 ID，无登录上下文或用户不存在时返回 null
     */
    public Integer getUserId() {
        Integer cached = userIdCache.get();
        if (cached != null) return cached;

        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            Object userIdAttr = request.getAttribute("userId");
            if (userIdAttr instanceof Integer uid) {
                userIdCache.set(uid);
                return uid;
            }
        }

        // 降级: 自行查库
        String email = getEmail();
        if (email == null) return null;

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user != null) {
            userIdCache.set(user.getId());
            return user.getId();
        }
        throw new com.zora.exception.NotFoundException("用户不存在");
    }

    /**
     * 获取当前请求用户的角色
     * @return 角色字符串（"user" / "admin"），无登录上下文时返回 null
     */
    public String getRole() {
        String cached = roleCache.get();
        if (cached != null) return cached;

        HttpServletRequest request = getCurrentRequest();
        if (request == null) return null;

        String role = (String) request.getAttribute("userRole");
        if (role != null) roleCache.set(role);
        return role;
    }

    /**
     * 清除当前线程缓存（仅在线程池复用极端场景下需要）
     */
    public void clear() {
        emailCache.remove();
        userIdCache.remove();
        roleCache.remove();
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }
}
