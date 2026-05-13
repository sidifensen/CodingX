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

@Service
@RequiredArgsConstructor
public class AuthApplicationService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AuthSessionGateway authSessionGateway;

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

    public void logoutCurrent() {
        authSessionGateway.logoutCurrent();
    }

    public User currentUser() {
        Long userId = authSessionGateway.currentLoginId();
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("Current user not found"));
    }
}