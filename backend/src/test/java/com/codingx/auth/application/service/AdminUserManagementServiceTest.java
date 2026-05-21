package com.codingx.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.auth.domain.model.User;
import com.codingx.auth.domain.model.UserStatus;
import com.codingx.auth.domain.model.UserType;
import com.codingx.auth.domain.repository.UserRepository;
import com.codingx.auth.domain.service.AuthSessionGateway;
import com.codingx.auth.domain.service.PasswordHasher;
import com.codingx.auth.interfaces.request.AdminUserCreateRequest;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.common.exception.NotFoundException;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证管理端用户管理服务核心业务：管理员权限、状态流转与密码重置。
 */
@ExtendWith(MockitoExtension.class)
class AdminUserManagementServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthSessionGateway authSessionGateway;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private WorkspaceRepositoryImpl workspaceRepository;

    @InjectMocks
    private AdminUserManagementService adminUserManagementService;

    /**
     * 管理员可按状态筛选用户列表。
     */
    @Test
    void listUsersFiltersByStatus() {
        User admin = buildUser(1001L, "admin", UserType.ADMIN, UserStatus.ACTIVE);
        User pendingUser = buildUser(2001L, "waiting-user", UserType.USER, UserStatus.PENDING);
        when(authSessionGateway.currentLoginId()).thenReturn(1001L);
        when(userRepository.findById(1001L)).thenReturn(Optional.of(admin));
        when(userRepository.pageUsers(1, 10, UserStatus.PENDING, "")).thenReturn(
            AdminUserPageView.builder().records(List.of(pendingUser)).total(1L).current(1L).size(10L).pages(1L).build()
        );

        AdminUserPageView pageView = adminUserManagementService.listUsers(1, 10, "PENDING", "");

        assertEquals(1, pageView.records().size());
        assertEquals(UserStatus.PENDING, pageView.records().getFirst().getStatus());
    }

    /**
     * 非管理员调用管理端接口应被拒绝。
     */
    @Test
    void listUsersRejectsNonAdmin() {
        User normalUser = buildUser(1002L, "user", UserType.USER, UserStatus.ACTIVE);
        when(authSessionGateway.currentLoginId()).thenReturn(1002L);
        when(userRepository.findById(1002L)).thenReturn(Optional.of(normalUser));

        assertThrows(ForbiddenException.class, () -> adminUserManagementService.listUsers(1, 10, "ALL", ""));
    }

    /**
     * 待审核用户应可审核通过并变为 ACTIVE。
     */
    @Test
    void approveUserTurnsPendingIntoActive() {
        User admin = buildUser(1001L, "admin", UserType.ADMIN, UserStatus.ACTIVE);
        User pendingUser = buildUser(2001L, "waiting-user", UserType.USER, UserStatus.PENDING);
        when(authSessionGateway.currentLoginId()).thenReturn(1001L);
        when(userRepository.findById(1001L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2001L)).thenReturn(Optional.of(pendingUser));

        adminUserManagementService.approveUser(2001L);

        verify(userRepository).save(any(User.class));
    }

    /**
     * 重置密码应更新密码哈希并持久化。
     */
    @Test
    void resetPasswordUpdatesPasswordHash() {
        User admin = buildUser(1001L, "admin", UserType.ADMIN, UserStatus.ACTIVE);
        User targetUser = buildUser(2001L, "target", UserType.USER, UserStatus.ACTIVE);
        when(authSessionGateway.currentLoginId()).thenReturn(1001L);
        when(userRepository.findById(1001L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2001L)).thenReturn(Optional.of(targetUser));
        when(passwordHasher.hash("new-pass")).thenReturn("hashed-new-pass");

        adminUserManagementService.resetPassword(2001L, "new-pass");

        verify(passwordHasher).hash("new-pass");
        verify(userRepository).save(any(User.class));
    }

    /**
     * 查询不存在的用户详情应返回 NOT_FOUND 语义。
     */
    @Test
    void getUserDetailThrowsWhenUserMissing() {
        User admin = buildUser(1001L, "admin", UserType.ADMIN, UserStatus.ACTIVE);
        when(authSessionGateway.currentLoginId()).thenReturn(1001L);
        when(userRepository.findById(1001L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> adminUserManagementService.getUserDetail(9999L));
    }

    /**
     * 新增用户成功后应写入目标状态与加密后的密码哈希。
     */
    @Test
    void createUserPersistsRequestedStatusAndHashedPassword() {
        User admin = buildUser(1001L, "admin", UserType.ADMIN, UserStatus.ACTIVE);
        when(authSessionGateway.currentLoginId()).thenReturn(1001L);
        when(userRepository.findById(1001L)).thenReturn(Optional.of(admin));
        when(passwordHasher.hash("123456")).thenReturn("hashed-123456");

        adminUserManagementService.createUser(new AdminUserCreateRequest(
            "new-user",
            "新用户",
            "123456",
            "USER",
            "PENDING",
            "new-user@codingx.io",
            "13800000009",
            "https://example.com/new.png"
        ));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        verify(workspaceRepository).ensureDefaultCloudWorkspace(userCaptor.getValue().getId(), "新用户");
        assertEquals("hashed-123456", userCaptor.getValue().getPasswordHash());
        assertEquals(UserStatus.PENDING, userCaptor.getValue().getStatus());
    }

    private User buildUser(Long id, String username, UserType userType, UserStatus status) {
        return User.builder()
            .id(id)
            .username(username)
            .displayName(username)
            .passwordHash("hash")
            .userType(userType)
            .status(status)
            .email(username + "@codingx.io")
            .phone("13800000000")
            .avatarUrl("https://example.com/avatar.png")
            .createdAt(LocalDateTime.of(2026, 5, 17, 12, 0, 0))
            .updatedAt(LocalDateTime.of(2026, 5, 17, 12, 0, 0))
            .build();
    }
}
