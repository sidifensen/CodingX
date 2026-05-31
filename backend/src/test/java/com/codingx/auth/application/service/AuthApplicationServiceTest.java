package com.codingx.auth.application.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
     * 用户仓储测试替身，用于控制账号查询和保存结果。
     */
    @Mock
    private UserRepository userRepository;

    /**
     * 密码哈希测试替身，用于模拟密码匹配结果。
     */
    @Mock
    private PasswordHasher passwordHasher;

    /**
     * 会话网关测试替身，用于模拟请求 IP 和登录令牌。
     */
    @Mock
    private AuthSessionGateway authSessionGateway;

    /**
     * 被测认证应用服务。
     */
    @InjectMocks
    private AuthApplicationService authApplicationService;

    /**
     * 活跃用户凭证正确时应创建会话并更新登录审计信息。
     */
    @Test
    void loginCreatesSessionForActiveUser() {
        User user = User.create(1002L, "demo", "CodingX Demo", "hash", UserType.USER, UserStatus.ACTIVE);
        when(userRepository.findByUsername("demo")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("password", "hash")).thenReturn(true);
        when(authSessionGateway.currentRequestIp()).thenReturn("127.0.0.1");
        when(authSessionGateway.login(user)).thenReturn("token-1");
        LoginResult result = authApplicationService.login(new LoginCommand("demo", "password"));
        assertEquals("token-1", result.token());
        verify(userRepository).save(any(User.class));
        verify(authSessionGateway).login(user);
    }

    /**
     * 密码错误时应拒绝登录且不创建会话。
     */
    @Test
    void loginRejectsWrongPassword() {
        User user = User.create(1002L, "demo", "CodingX Demo", "hash", UserType.USER, UserStatus.ACTIVE);
        when(userRepository.findByUsername("demo")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("wrong", "hash")).thenReturn(false);
        assertThrows(UnauthorizedException.class, () -> authApplicationService.login(new LoginCommand("demo", "wrong")));
    }
}
