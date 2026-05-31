package com.codingx.auth.domain.service;

/**
 * 密码哈希服务契约，隔离认证领域与具体加密算法。
 */
public interface PasswordHasher {

    /**
     * 将明文密码转换为不可逆哈希。
     * @param plainPassword 明文密码。
     * @return 密码哈希值。
     */
    String hash(String plainPassword);

    /**
     * 校验明文密码是否匹配已保存的哈希值。
     * @param plainPassword 本次登录输入的明文密码。
     * @param passwordHash 数据库中保存的密码哈希。
     * @return true 表示密码匹配。
     */
    boolean matches(String plainPassword, String passwordHash);
}
