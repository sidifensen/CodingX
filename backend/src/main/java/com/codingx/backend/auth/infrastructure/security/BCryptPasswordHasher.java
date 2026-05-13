package com.codingx.backend.auth.infrastructure.security;
import com.codingx.backend.auth.domain.service.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Bridges security infrastructure for BCryptPasswordHasher.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Executes the logic defined by hash.
     * @param plainPassword input argument.
     * @return processing result.
     */
    @Override
    public String hash(String plainPassword) {
        return passwordEncoder.encode(plainPassword);
    }

    /**
     * Checks whether the input handled by matches satisfies the expected condition.
     * @param plainPassword input argument.
     * @param passwordHash input argument.
     * @return processing result.
     */
    @Override
    public boolean matches(String plainPassword, String passwordHash) {
        return passwordEncoder.matches(plainPassword, passwordHash);
    }
}
