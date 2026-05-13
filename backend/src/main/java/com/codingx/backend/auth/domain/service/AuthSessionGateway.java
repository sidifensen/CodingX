package com.codingx.backend.auth.domain.service;
import com.codingx.backend.auth.domain.model.User;

/**
 * Defines the domain service contract exposed by AuthSessionGateway.
 */
public interface AuthSessionGateway {

    /**
     * Authenticates the current user and returns the login result.
     * @param user input argument.
     * @return processing result.
     */
    String login(User user);

    /**
     * Clears the current login session.
     */
    void logoutCurrent();

    /**
     * Returns the data for the current authenticated user.
     * @return processing result.
     */
    Long currentLoginId();
}
