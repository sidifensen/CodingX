package com.codingx.auth.application.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.codingx.auth.application.command.LoginCommand;
import com.codingx.auth.domain.model.User;
import com.codingx.auth.domain.model.UserStatus;
import com.codingx.auth.domain.model.UserType;
import com.codingx.auth.domain.repository.UserRepository;
import com.codingx.auth.domain.service.AuthSessionGateway;
import com.codingx.auth.domain.service.PasswordHasher;
import com.codingx.common.exception.UnauthorizedException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证 AuthApplicationService 的关键场景。
 */
@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTest {

    /**
     * UserRepository 依赖。
     */
    @Mock
    private UserRepository userRepository;

    /**
     * passwordHasher 字段。
     */
    @Mock
    private PasswordHasher passwordHasher;

    /**
     * AuthSessionGateway 依赖。
     */
    @Mock
    private AuthSessionGateway authSessionGateway;

    /**
     * AuthApplicationService 依赖。
     */
    @InjectMocks
    private AuthApplicationService authApplicationService;

    /**
     * 校验当前用户并返回登录结果。
     */
    @Test
    void loginCreatesSessionForActiveUser() {
        User user = User.create(1002L, "demo", "CodingX Demo", "hash", UserType.USER, UserStatus.ACTIVE);
        when(userRepository.findByUsername("demo")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("password", "hash")).thenReturn(true);
        when(authSessionGateway.login(user)).thenReturn("token-1");
        LoginResult result = authApplicationService.login(new LoginCommand("demo", "password"));
        assertEquals("token-1", result.token());
        verify(authSessionGateway).login(user);
    }

    /**
     * 校验当前用户并返回登录结果。
     */
    @Test
    void loginRejectsWrongPassword() {
        User user = User.create(1002L, "demo", "CodingX Demo", "hash", UserType.USER, UserStatus.ACTIVE);
        when(userRepository.findByUsername("demo")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("wrong", "hash")).thenReturn(false);
        assertThrows(UnauthorizedException.class, () -> authApplicationService.login(new LoginCommand("demo", "wrong")));
    }
}
