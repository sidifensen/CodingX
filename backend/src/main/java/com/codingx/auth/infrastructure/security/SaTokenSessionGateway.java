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
 * Sa-Token 会话网关适配器，负责登录态创建、清理和当前请求上下文读取。
 */
@Component
public class SaTokenSessionGateway implements AuthSessionGateway {

    /**
     * 为已通过认证的用户创建 Sa-Token 登录会话。
     * @param user 已通过凭证校验的用户。
     * @return Sa-Token 当前访问令牌。
     */
    @Override
    public String login(User user) {
        // 步骤 1：用用户主键创建登录态，后续接口可通过 Sa-Token 解析登录用户。
        StpUtil.login(user.getId());
        // 步骤 2：把常用展示信息写入会话，减少后续简单场景重复查库。
        StpUtil.getSession().set("username", user.getUsername());
        StpUtil.getSession().set("displayName", user.getDisplayName());
        StpUtil.getSession().set("userType", user.getUserType().name());
        // 步骤 3：返回访问令牌给登录接口响应。
        return StpUtil.getTokenValue();
    }

    /**
     * 清理当前登录会话。
     */
    @Override
    public void logoutCurrent() {
        // 步骤 1：退出当前 Sa-Token 会话，清理服务端登录态。
        StpUtil.logout();
    }

    /**
     * 读取当前登录用户标识。
     * @return 当前登录用户主键。
     */
    @Override
    public Long currentLoginId() {
        // 步骤 1：优先使用 Sa-Token 当前登录 ID。
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception exception) {
            // 步骤 2：未登录或登录态异常统一转换为中文未登录异常。
            throw new UnauthorizedException(ErrorMessageCatalog.AUTH_NOT_LOGGED_IN);
        }
    }

    /**
     * 返回当前请求来源 IP，用于记录登录审计信息。
     * @return 客户端 IP，获取失败时返回 null。
     */
    @Override
    public String currentRequestIp() {
        // 步骤 1：非 Web 请求上下文无法获取客户端 IP，直接返回 null。
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return null;
        }
        // 步骤 2：优先读取反向代理透传的 X-Forwarded-For，第一个地址通常是客户端原始 IP。
        HttpServletRequest request = requestAttributes.getRequest();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(forwardedFor)) {
            String firstIp = forwardedFor.split(",")[0];
            return StrUtil.trimToNull(firstIp);
        }
        // 步骤 3：没有代理头时回退到 Servlet 容器提供的远端地址。
        return StrUtil.trimToNull(request.getRemoteAddr());
    }
}
