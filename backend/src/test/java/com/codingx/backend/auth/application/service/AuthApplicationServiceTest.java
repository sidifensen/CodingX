package com.codingx.backend.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.backend.auth.application.command.LoginCommand;
import com.codingx.backend.auth.domain.model.User;
import com.codingx.backend.auth.domain.model.UserStatus;
import com.codingx.backend.auth.domain.model.UserType;
import com.codingx.backend.auth.domain.repository.UserRepository;
import com.codingx.backend.auth.domain.service.AuthSessionGateway;
import com.codingx.backend.auth.domain.service.PasswordHasher;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private AuthSessionGateway authSessionGateway;

    @InjectMocks
    private AuthApplicationService authApplicationService;

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

    @Test
    void loginRejectsWrongPassword() {
        User user = User.create(1002L, "demo", "CodingX Demo", "hash", UserType.USER, UserStatus.ACTIVE);
        when(userRepository.findByUsername("demo")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("wrong", "hash")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> authApplicationService.login(new LoginCommand("demo", "wrong")));
    }
}
