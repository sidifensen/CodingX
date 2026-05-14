package com.codingx.auth.infrastructure.security;
import com.codingx.auth.domain.service.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 实现 BCryptPasswordHasher 的安全基础设施适配。
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 执行 hash 定义的处理逻辑。
     * @param plainPassword 输入参数。
     * @return 输入参数。
     */
    @Override
    public String hash(String plainPassword) {
        return passwordEncoder.encode(plainPassword);
    }

    /**
     * 检查 matches 处理的输入是否满足条件。
     * @param plainPassword 输入参数。
     * @param passwordHash 输入参数。
     * @return 输入参数。
     */
    @Override
    public boolean matches(String plainPassword, String passwordHash) {
        return passwordEncoder.matches(plainPassword, passwordHash);
    }
}
