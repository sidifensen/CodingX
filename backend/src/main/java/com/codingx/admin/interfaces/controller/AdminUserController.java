package com.codingx.auth.interfaces.controller;

import com.codingx.auth.application.service.AdminUserManagementService;
import com.codingx.auth.application.service.AdminUserPageView;
import com.codingx.auth.domain.model.User;
import com.codingx.auth.interfaces.request.AdminUserCreateRequest;
import com.codingx.auth.interfaces.request.AdminUserResetPasswordRequest;
import com.codingx.auth.interfaces.request.AdminUserStatusUpdateRequest;
import com.codingx.auth.interfaces.request.AdminUserUpdateRequest;
import com.codingx.auth.interfaces.response.AdminUserDetailResponse;
import com.codingx.auth.interfaces.response.AdminUserPageResponse;
import com.codingx.auth.interfaces.response.AdminUserSummaryResponse;
import com.codingx.common.model.ApiResponse;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供管理端用户管理接口：列表、详情、新增、编辑、状态变更、审核与重置密码。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserManagementService adminUserManagementService;

    /**
     * 分页查询用户。
     * @param current 当前页码。
     * @param size 每页条数。
     * @param status 状态筛选。
     * @param keyword 关键词筛选。
     * @return 分页结果。
     */
    @GetMapping
    public ApiResponse<AdminUserPageResponse> listUsers(
        @RequestParam(defaultValue = "1") int current,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "ALL") String status,
        @RequestParam(required = false) String keyword
    ) {
        AdminUserPageView pageView = adminUserManagementService.listUsers(current, size, status, keyword);
        return ApiResponse.success(AdminUserPageResponse.builder()
            .records(pageView.records().stream().map(this::toSummary).toList())
            .total(pageView.total())
            .current(pageView.current())
            .size(pageView.size())
            .pages(pageView.pages())
            .build());
    }

    /**
     * 查询用户详情。
     * @param id 用户 ID。
     * @return 用户详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<AdminUserDetailResponse> getUser(@PathVariable Long id) {
        return ApiResponse.success(toDetail(adminUserManagementService.getUserDetail(id)));
    }

    /**
     * 新增用户。
     * @param request 新增请求。
     * @return 新增后用户详情。
     */
    @PostMapping
    public ApiResponse<AdminUserDetailResponse> createUser(@RequestBody AdminUserCreateRequest request) {
        return ApiResponse.success(toDetail(adminUserManagementService.createUser(request)));
    }

    /**
     * 编辑用户。
     * @param id 用户 ID。
     * @param request 编辑请求。
     * @return 更新后用户详情。
     */
    @PutMapping("/{id}")
    public ApiResponse<AdminUserDetailResponse> updateUser(@PathVariable Long id, @RequestBody AdminUserUpdateRequest request) {
        return ApiResponse.success(toDetail(adminUserManagementService.updateUser(id, request)));
    }

    /**
     * 更新用户状态。
     * @param id 用户 ID。
     * @param request 状态更新请求。
     * @return 统一成功响应。
     */
    @PostMapping("/{id}/status")
    public ApiResponse<Void> updateUserStatus(@PathVariable Long id, @RequestBody AdminUserStatusUpdateRequest request) {
        adminUserManagementService.updateUserStatus(id, request.status());
        return ApiResponse.successMessage("状态更新成功");
    }

    /**
     * 审核通过待审核用户。
     * @param id 用户 ID。
     * @return 统一成功响应。
     */
    @PostMapping("/{id}/approve")
    public ApiResponse<Void> approveUser(@PathVariable Long id) {
        adminUserManagementService.approveUser(id);
        return ApiResponse.successMessage("审核通过");
    }

    /**
     * 重置用户密码。
     * @param id 用户 ID。
     * @param request 重置密码请求。
     * @return 统一成功响应。
     */
    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody AdminUserResetPasswordRequest request) {
        adminUserManagementService.resetPassword(id, request.newPassword());
        return ApiResponse.successMessage("密码重置成功");
    }

    private AdminUserSummaryResponse toSummary(User user) {
        return new AdminUserSummaryResponse(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getEmail(),
            user.getPhone(),
            user.getAvatarUrl(),
            user.getUserType().name(),
            toUserTypeLabel(user),
            user.getStatus().name(),
            toStatusLabel(user),
            user.getLastLoginAt(),
            user.getCreatedAt()
        );
    }

    private AdminUserDetailResponse toDetail(User user) {
        return new AdminUserDetailResponse(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getEmail(),
            user.getPhone(),
            user.getAvatarUrl(),
            user.getUserType().name(),
            toUserTypeLabel(user),
            user.getStatus().name(),
            toStatusLabel(user),
            user.getLastLoginAt(),
            user.getLastLoginIp(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }

    private String toStatusLabel(User user) {
        return switch (user.getStatus()) {
            case ACTIVE -> "正常";
            case DISABLED -> "禁用";
            case PENDING -> "待审核";
        };
    }

    private String toUserTypeLabel(User user) {
        return switch (user.getUserType()) {
            case ADMIN -> "管理员";
            case USER -> StrUtil.equalsAnyIgnoreCase(user.getStatus().name(), "PENDING") ? "待审核用户" : "普通用户";
        };
    }
}
