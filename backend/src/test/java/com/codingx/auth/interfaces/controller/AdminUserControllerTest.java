package com.codingx.auth.interfaces.controller;
import com.codingx.admin.interfaces.controller.AdminUserController;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.auth.application.service.AdminUserManagementService;
import com.codingx.auth.application.service.AdminUserPageView;
import com.codingx.auth.domain.model.User;
import com.codingx.auth.domain.model.UserStatus;
import com.codingx.auth.domain.model.UserType;
import com.codingx.config.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证管理端用户接口 HTTP 契约。
 */
@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock
    private AdminUserManagementService adminUserManagementService;

    @InjectMocks
    private AdminUserController adminUserController;

    /**
     * 用户列表接口应返回分页结构。
     */
    @Test
    void listUsersReturnsPagePayload() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 5, 17, 14, 10, 0);
        when(adminUserManagementService.listUsers(1, 10, "ALL", null)).thenReturn(
            AdminUserPageView.builder()
                .records(List.of(User.builder()
                    .id(2001L)
                    .username("user")
                    .displayName("用户A")
                    .userType(UserType.USER)
                    .status(UserStatus.ACTIVE)
                    .createdAt(now)
                    .build()))
                .total(1L)
                .current(1L)
                .size(10L)
                .pages(1L)
                .build()
        );

        mockMvc().perform(get("/api/admin/users")
                .param("current", "1")
                .param("size", "10")
                .param("status", "ALL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].statusLabel").value("正常"));
    }

    /**
     * 状态切换接口应返回成功响应。
     */
    @Test
    void updateStatusReturnsSuccess() throws Exception {
        mockMvc().perform(post("/api/admin/users/2001/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DISABLED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * 编辑接口应返回更新后用户。
     */
    @Test
    void updateUserReturnsUpdatedPayload() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 5, 17, 14, 10, 0);
        when(adminUserManagementService.updateUser(eq(2001L), any())).thenReturn(
            User.builder()
                .id(2001L)
                .username("user")
                .displayName("用户B")
                .userType(UserType.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build()
        );

        mockMvc().perform(put("/api/admin/users/2001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"用户B\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(adminUserController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
