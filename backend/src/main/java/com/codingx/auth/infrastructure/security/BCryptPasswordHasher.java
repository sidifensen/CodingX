package com.codingx.auth.infrastructure.security;
import com.codingx.auth.domain.service.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt 密码哈希适配器，负责把领域密码契约落到 Spring Security 实现。
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    /**
     * BCrypt 编码器，内部负责盐值生成和哈希校验。
     */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 生成明文密码的 BCrypt 哈希。
     * @param plainPassword 明文密码。
     * @return BCrypt 哈希值。
     */
    @Override
    public String hash(String plainPassword) {
        // 步骤 1：交给 BCryptPasswordEncoder 生成带盐哈希，调用方只保存返回值。
        return passwordEncoder.encode(plainPassword);
    }

    /**
     * 校验明文密码是否匹配已保存的 BCrypt 哈希。
     * @param plainPassword 本次输入的明文密码。
     * @param passwordHash 数据库中保存的 BCrypt 哈希。
     * @return true 表示密码匹配。
     */
    @Override
    public boolean matches(String plainPassword, String passwordHash) {
        // 步骤 1：BCryptPasswordEncoder 会从哈希中读取盐值并完成匹配。
        return passwordEncoder.matches(plainPassword, passwordHash);
    }
}
