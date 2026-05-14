package com.codingx.auth.domain.service;

/**
 * 定义 PasswordHasher 的领域服务契约。
 */
public interface PasswordHasher {

    /**
     * 执行 hash 定义的处理逻辑。
     * @param plainPassword 输入参数。
     * @return 输入参数。
     */
    String hash(String plainPassword);

    /**
     * 检查 matches 处理的输入是否满足条件。
     * @param plainPassword 输入参数。
     * @param passwordHash 输入参数。
     * @return 输入参数。
     */
    boolean matches(String plainPassword, String passwordHash);
}
