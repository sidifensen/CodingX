package com.codingx.backend.auth.application.service;
import cn.hutool.core.util.StrUtil;
import com.codingx.backend.auth.application.command.LoginCommand;
import com.codingx.backend.auth.domain.model.User;
import com.codingx.backend.auth.domain.repository.UserRepository;
import com.codingx.backend.auth.domain.service.AuthSessionGateway;
import com.codingx.backend.auth.domain.service.PasswordHasher;
import com.codingx.backend.common.exception.NotFoundException;
import com.codingx.backend.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Orchestrates application flow for AuthApplicationService by coordinating domain objects and infrastructure services.
 */
@Service
@RequiredArgsConstructor
public class AuthApplicationService {

    /**
     * UserRepository dependency.
     */
    private final UserRepository userRepository;
    /**
     * passwordHasher value.
     */
    private final PasswordHasher passwordHasher;
    /**
     * AuthSessionGateway dependency.
     */
    private final AuthSessionGateway authSessionGateway;

    /**
     * Authenticates the current user and returns the login result.
     * @param command input argument.
     * @return processing result.
     */
    public LoginResult login(LoginCommand command) {
        if (StrUtil.hasBlank(command.username(), command.password())) {
            throw new IllegalArgumentException("Username and password are required");
        }
        User user = userRepository.findByUsername(command.username())
            .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));
        user.ensureActive();
        if (!passwordHasher.matches(command.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        String token = authSessionGateway.login(user);
        return new LoginResult(user.getId(), user.getUsername(), user.getDisplayName(), user.getUserType(), token);
    }

    /**
     * Clears the current login session.
     */
    public void logoutCurrent() {
        authSessionGateway.logoutCurrent();
    }

    /**
     * Returns the data for the current authenticated user.
     * @return processing result.
     */
    public User currentUser() {
        Long userId = authSessionGateway.currentLoginId();
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("Current user not found"));
    }
}
