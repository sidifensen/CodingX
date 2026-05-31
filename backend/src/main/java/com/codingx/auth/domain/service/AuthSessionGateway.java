package com.codingx.auth.domain.service;
import com.codingx.auth.domain.model.User;

/**
 * 认证会话网关契约，隔离应用服务与具体登录态框架。
 */
public interface AuthSessionGateway {

    /**
     * 为指定用户创建登录会话。
     * @param user 已通过凭证校验的用户。
     * @return 当前会话访问令牌。
     */
    String login(User user);

    /**
     * 清理当前登录会话。
     */
    void logoutCurrent();

    /**
     * 读取当前请求绑定的登录用户标识。
     * @return 当前登录用户主键。
     */
    Long currentLoginId();

    /**
     * 返回当前请求来源 IP，用于记录登录审计信息。
     * @return 客户端 IP，获取失败时可返回 null。
     */
    String currentRequestIp();
}
