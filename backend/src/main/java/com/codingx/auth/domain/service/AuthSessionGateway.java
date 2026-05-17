package com.codingx.auth.domain.service;
import com.codingx.auth.domain.model.User;

/**
 * 定义 AuthSessionGateway 的领域服务契约。
 */
public interface AuthSessionGateway {

    /**
     * 校验当前用户并返回登录结果。
     * @param user 输入参数。
     * @return 输入参数。
     */
    String login(User user);

    /**
     * 清理当前登录会话。
     */
    void logoutCurrent();

    /**
     * 返回当前登录用户信息。
     * @return 输入参数。
     */
    Long currentLoginId();

    /**
     * 返回当前请求来源 IP，用于记录登录审计信息。
     * @return 客户端 IP，获取失败时可返回 null。
     */
    String currentRequestIp();
}
