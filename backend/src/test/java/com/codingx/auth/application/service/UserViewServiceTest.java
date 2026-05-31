package com.codingx.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.codingx.auth.domain.model.User;
import com.codingx.auth.domain.model.UserStatus;
import com.codingx.auth.domain.model.UserType;
import com.codingx.auth.interfaces.response.AdminUserDetailResponse;
import com.codingx.auth.interfaces.response.AdminUserPageResponse;
import com.codingx.auth.interfaces.response.LoginResponse;
import com.codingx.auth.interfaces.response.MeResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证用户视图服务统一生成认证与管理端用户响应。
 */
class UserViewServiceTest {

    private final UserViewService userViewService = new UserViewService();

    /**
     * 登录响应应完整保留会话令牌与用户身份信息。
     */
    @Test
    void toLoginResponseProjectsLoginResult() {
        LoginResult result = new LoginResult(1002L, "demo", "演示用户", UserType.USER, "token-1");

        LoginResponse response = userViewService.toLoginResponse(result);

        assertEquals(1002L, response.userId());
        assertEquals("demo", response.username());
        assertEquals("演示用户", response.displayName());
        assertEquals(UserType.USER, response.userType());
        assertEquals("token-1", response.token());
    }

    /**
     * 当前用户响应不应携带密码、邮箱等管理端字段。
     */
    @Test
    void toMeResponseProjectsCurrentUserIdentityOnly() {
        User user = buildUser(1002L, "demo", UserType.USER, UserStatus.ACTIVE);

        MeResponse response = userViewService.toMeResponse(user);

        assertEquals(1002L, response.userId());
        assertEquals("demo", response.username());
        assertEquals("演示用户", response.displayName());
        assertEquals(UserType.USER, response.userType());
    }

    /**
     * 管理端分页响应应保持分页元数据并生成中文状态标签。
     */
    @Test
    void toAdminPageResponseProjectsPageMetadataAndLabels() {
        User activeUser = buildUser(2001L, "active-user", UserType.USER, UserStatus.ACTIVE);
        AdminUserPageView pageView = AdminUserPageView.builder()
            .records(List.of(activeUser))
            .total(1L)
            .current(1L)
            .size(10L)
            .pages(1L)
            .build();

        AdminUserPageResponse response = userViewService.toAdminPageResponse(pageView);

        assertEquals(1L, response.total());
        assertEquals(1L, response.current());
        assertEquals(10L, response.size());
        assertEquals(1L, response.pages());
        assertEquals("正常", response.records().getFirst().statusLabel());
        assertEquals("普通用户", response.records().getFirst().userTypeLabel());
    }

    /**
     * 待审核普通用户的类型标签应服务于管理端审核语义。
     */
    @Test
    void toAdminDetailResponseLabelsPendingUserForReview() {
        User pendingUser = buildUser(2002L, "pending-user", UserType.USER, UserStatus.PENDING);

        AdminUserDetailResponse response = userViewService.toAdminDetail(pendingUser);

        assertEquals("PENDING", response.status());
        assertEquals("待审核", response.statusLabel());
        assertEquals("待审核用户", response.userTypeLabel());
    }

    private User buildUser(Long id, String username, UserType userType, UserStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 5, 31, 12, 0, 0);
        return User.builder()
            .id(id)
            .username(username)
            .displayName("演示用户")
            .passwordHash("hash")
            .userType(userType)
            .status(status)
            .email(username + "@codingx.io")
            .phone("13800000000")
            .avatarUrl("https://example.com/avatar.png")
            .lastLoginAt(now)
            .lastLoginIp("127.0.0.1")
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
