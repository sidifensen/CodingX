package com.codingx.admin.interfaces.controller;

import com.codingx.auth.application.service.AdminUserManagementService;
import com.codingx.auth.application.service.AdminUserPageView;
import com.codingx.auth.application.service.UserViewService;
import com.codingx.auth.interfaces.request.AdminUserCreateRequest;
import com.codingx.auth.interfaces.request.AdminUserResetPasswordRequest;
import com.codingx.auth.interfaces.request.AdminUserStatusUpdateRequest;
import com.codingx.auth.interfaces.request.AdminUserUpdateRequest;
import com.codingx.auth.interfaces.response.AdminUserDetailResponse;
import com.codingx.auth.interfaces.response.AdminUserPageResponse;
import com.codingx.common.model.ApiResponse;
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

    /**
     * 管理端用户应用服务，承接权限校验、用户查询和用户状态变更。
     */
    private final AdminUserManagementService adminUserManagementService;

    /**
     * 用户视图服务，负责把用户领域对象转换为管理端响应结构。
     */
    private final UserViewService userViewService;

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
        // 步骤 1：分页、状态和关键词过滤交给应用服务处理，Controller 不直接访问仓储。
        AdminUserPageView pageView = adminUserManagementService.listUsers(current, size, status, keyword);
        // 步骤 2：分页响应和中文展示标签由视图服务统一生成。
        return ApiResponse.success(userViewService.toAdminPageResponse(pageView));
    }

    /**
     * 查询用户详情。
     * @param id 用户 ID。
     * @return 用户详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<AdminUserDetailResponse> getUser(@PathVariable Long id) {
        // 步骤 1：应用服务负责管理员权限校验和用户存在性校验。
        // 步骤 2：详情响应由视图服务生成，避免 Controller 持有字段映射规则。
        return ApiResponse.success(userViewService.toAdminDetail(adminUserManagementService.getUserDetail(id)));
    }

    /**
     * 新增用户。
     * @param request 新增请求。
     * @return 新增后用户详情。
     */
    @PostMapping
    public ApiResponse<AdminUserDetailResponse> createUser(@RequestBody AdminUserCreateRequest request) {
        // 步骤 1：应用服务负责校验输入、保存用户并创建默认工作空间。
        // 步骤 2：新增后的用户详情由视图服务统一投影。
        return ApiResponse.success(userViewService.toAdminDetail(adminUserManagementService.createUser(request)));
    }

    /**
     * 编辑用户。
     * @param id 用户 ID。
     * @param request 编辑请求。
     * @return 更新后用户详情。
     */
    @PutMapping("/{id}")
    public ApiResponse<AdminUserDetailResponse> updateUser(@PathVariable Long id, @RequestBody AdminUserUpdateRequest request) {
        // 步骤 1：应用服务负责校验邮箱唯一性、状态合法性并保存资料。
        // 步骤 2：更新后的用户详情由视图服务统一投影。
        return ApiResponse.success(userViewService.toAdminDetail(adminUserManagementService.updateUser(id, request)));
    }

    /**
     * 更新用户状态。
     * @param id 用户 ID。
     * @param request 状态更新请求。
     * @return 统一成功响应。
     */
    @PostMapping("/{id}/status")
    public ApiResponse<Void> updateUserStatus(@PathVariable Long id, @RequestBody AdminUserStatusUpdateRequest request) {
        // 步骤 1：应用服务负责管理员权限校验和目标状态解析。
        adminUserManagementService.updateUserStatus(id, request.status());
        // 步骤 2：状态更新成功后返回统一中文提示。
        return ApiResponse.successMessage("状态更新成功");
    }

    /**
     * 审核通过待审核用户。
     * @param id 用户 ID。
     * @return 统一成功响应。
     */
    @PostMapping("/{id}/approve")
    public ApiResponse<Void> approveUser(@PathVariable Long id) {
        // 步骤 1：应用服务只允许 PENDING 用户进入审核通过流转。
        adminUserManagementService.approveUser(id);
        // 步骤 2：审核成功后返回管理端提示文案。
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
        // 步骤 1：应用服务负责校验新密码并写入加密后的密码哈希。
        adminUserManagementService.resetPassword(id, request.newPassword());
        // 步骤 2：密码重置成功后不返回密码相关数据。
        return ApiResponse.successMessage("密码重置成功");
    }
}
