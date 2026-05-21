package com.codingx.auth.infrastructure.security;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.auth.domain.model.User;
import com.codingx.auth.domain.service.AuthSessionGateway;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 实现 SaTokenSessionGateway 的安全基础设施适配。
 */
@Component
public class SaTokenSessionGateway implements AuthSessionGateway {

    /**
     * 校验当前用户并返回登录结果。
     * @param user 输入参数。
     * @return 输入参数。
     */
    @Override
    public String login(User user) {
        StpUtil.login(user.getId());
        StpUtil.getSession().set("username", user.getUsername());
        StpUtil.getSession().set("displayName", user.getDisplayName());
        StpUtil.getSession().set("userType", user.getUserType().name());
        return StpUtil.getTokenValue();
    }

    /**
     * 清理当前登录会话。
     */
    @Override
    public void logoutCurrent() {
        StpUtil.logout();
    }

    /**
     * 返回当前登录用户信息。
     * @return 输入参数。
     */
    @Override
    public Long currentLoginId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception exception) {
            throw new UnauthorizedException(ErrorMessageCatalog.AUTH_NOT_LOGGED_IN);
        }
    }

    /**
     * 返回当前请求来源 IP，用于记录登录审计信息。
     * @return 客户端 IP，获取失败时返回 null。
     */
    @Override
    public String currentRequestIp() {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return null;
        }
        HttpServletRequest request = requestAttributes.getRequest();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(forwardedFor)) {
            String firstIp = forwardedFor.split(",")[0];
            return StrUtil.trimToNull(firstIp);
        }
        return StrUtil.trimToNull(request.getRemoteAddr());
    }
}
