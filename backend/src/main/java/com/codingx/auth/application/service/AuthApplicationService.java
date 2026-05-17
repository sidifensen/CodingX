package com.codingx.auth.application.service;
import cn.hutool.core.util.StrUtil;
import com.codingx.auth.application.command.LoginCommand;
import com.codingx.auth.domain.model.User;
import com.codingx.auth.domain.repository.UserRepository;
import com.codingx.auth.domain.service.AuthSessionGateway;
import com.codingx.auth.domain.service.PasswordHasher;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.NotFoundException;
import com.codingx.common.exception.UnauthorizedException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责协调 AuthApplicationService 的应用流程，串联领域对象与基础设施服务。
 */
@Service
@RequiredArgsConstructor
public class AuthApplicationService {

    /**
     * UserRepository 依赖。
     */
    private final UserRepository userRepository;

    /**
     * passwordHasher 字段。
     */
    private final PasswordHasher passwordHasher;

    /**
     * AuthSessionGateway 依赖。
     */
    private final AuthSessionGateway authSessionGateway;

    /**
     * 校验当前用户并返回登录结果。
     * @param command 输入参数。
     * @return 输入参数。
     */
    public LoginResult login(LoginCommand command) {
        if (StrUtil.hasBlank(command.username(), command.password())) {
            throw new IllegalArgumentException(ErrorMessageCatalog.LOGIN_REQUIRED_CREDENTIALS);
        }
        User user = userRepository.findByUsername(command.username())
            .orElseThrow(() -> new UnauthorizedException(ErrorMessageCatalog.LOGIN_INVALID_CREDENTIALS));
        user.ensureActive();
        if (!passwordHasher.matches(command.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(ErrorMessageCatalog.LOGIN_INVALID_CREDENTIALS);
        }
        user.markLogin(LocalDateTime.now(), authSessionGateway.currentRequestIp());
        userRepository.save(user);
        String token = authSessionGateway.login(user);
        return new LoginResult(user.getId(), user.getUsername(), user.getDisplayName(), user.getUserType(), token);
    }

    /**
     * 清理当前登录会话。
     */
    public void logoutCurrent() {
        authSessionGateway.logoutCurrent();
    }

    /**
     * 返回当前登录用户信息。
     * @return 输入参数。
     */
    public User currentUser() {
        Long userId = authSessionGateway.currentLoginId();
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.CURRENT_USER_NOT_FOUND));
    }
}
