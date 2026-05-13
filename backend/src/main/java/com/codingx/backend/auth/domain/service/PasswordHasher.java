package com.codingx.backend.auth.domain.service;

/**
 * Defines the domain service contract exposed by PasswordHasher.
 */
public interface PasswordHasher {

    /**
     * Executes the logic defined by hash.
     * @param plainPassword input argument.
     * @return processing result.
     */
    String hash(String plainPassword);

    /**
     * Checks whether the input handled by matches satisfies the expected condition.
     * @param plainPassword input argument.
     * @param passwordHash input argument.
     * @return processing result.
     */
    boolean matches(String plainPassword, String passwordHash);
}
