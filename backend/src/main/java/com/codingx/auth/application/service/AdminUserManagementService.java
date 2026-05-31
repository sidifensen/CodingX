package com.codingx.auth.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.auth.domain.model.User;
import com.codingx.auth.domain.model.UserStatus;
import com.codingx.auth.domain.model.UserType;
import com.codingx.auth.domain.repository.UserRepository;
import com.codingx.auth.domain.service.AuthSessionGateway;
import com.codingx.auth.domain.service.PasswordHasher;
import com.codingx.auth.interfaces.request.AdminUserCreateRequest;
import com.codingx.auth.interfaces.request.AdminUserUpdateRequest;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.common.exception.NotFoundException;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供管理端用户管理能力：列表、详情、新增、编辑、状态流转、审核与重置密码。
 */
@Service
@RequiredArgsConstructor
public class AdminUserManagementService {

    /**
     * 用户仓储，用于管理端读取和保存用户聚合。
     */
    private final UserRepository userRepository;

    /**
     * 认证会话网关，用于识别当前操作人并校验管理员身份。
     */
    private final AuthSessionGateway authSessionGateway;

    /**
     * 密码哈希服务，用于新增用户和重置密码时生成安全哈希。
     */
    private final PasswordHasher passwordHasher;

    /**
     * 工作空间仓储，用于新建用户后补齐默认云端工作空间。
     */
    private final WorkspaceRepositoryImpl workspaceRepository;

    /**
     * 分页查询用户。
     * @param current 当前页码。
     * @param size 每页条数。
     * @param status 状态筛选。
     * @param keyword 关键词筛选。
     * @return 分页结果。
     */
    public AdminUserPageView listUsers(int current, int size, String status, String keyword) {
        // 步骤 1：所有管理端用户操作必须先确认当前操作人是管理员。
        ensureAdminOperator();
        // 步骤 2：将前端传入的状态字符串转换为可空枚举，ALL 或空值表示不过滤状态。
        UserStatus statusFilter = parseStatusFilter(status);
        // 步骤 3：关键词统一 trim 后交给仓储拼装用户名、展示名和邮箱模糊查询。
        return userRepository.pageUsers(current, size, statusFilter, StrUtil.trimToEmpty(keyword));
    }

    /**
     * 查询用户详情。
     * @param userId 用户 ID。
     * @return 用户详情。
     */
    public User getUserDetail(Long userId) {
        // 步骤 1：先校验管理员身份，避免普通用户枚举其他用户资料。
        ensureAdminOperator();
        // 步骤 2：读取目标用户，缺失时抛出统一的管理端用户不存在异常。
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.ADMIN_USER_NOT_FOUND));
    }

    /**
     * 新增用户。
     * @param request 新增请求。
     * @return 新增后用户。
     */
    public User createUser(AdminUserCreateRequest request) {
        // 步骤 1：校验当前操作人是管理员，并检查新增请求必填项。
        ensureAdminOperator();
        validateCreateRequest(request);
        // 步骤 2：邮箱统一小写和去空白后做唯一性检查，空邮箱不参与唯一性冲突。
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail, null)) {
            throw new BusinessException("ADMIN_USER_EMAIL_DUPLICATED", ErrorMessageCatalog.ADMIN_USER_EMAIL_DUPLICATED);
        }
        // 步骤 3：生成用户主键、密码哈希和初始状态，避免 Controller 拼装领域对象。
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
            .id(IdUtil.getSnowflakeNextId())
            .username(request.username().trim())
            .displayName(request.displayName().trim())
            .passwordHash(passwordHasher.hash(request.password()))
            .userType(parseUserType(request.userType()))
            .status(parseWritableStatus(request.status()))
            .email(normalizedEmail)
            .phone(normalizePhone(request.phone()))
            .avatarUrl(normalizeAvatar(request.avatarUrl()))
            .createdAt(now)
            .updatedAt(now)
            .build();
        // 步骤 4：先保存用户，再补齐默认云端空间，确保无 workspaceId 会话有稳定归属。
        userRepository.save(user);
        workspaceRepository.ensureDefaultCloudWorkspace(user.getId(), user.getDisplayName());
        return user;
    }

    /**
     * 编辑用户基础资料。
     * @param userId 用户 ID。
     * @param request 编辑请求。
     * @return 更新后用户。
     */
    public User updateUser(Long userId, AdminUserUpdateRequest request) {
        // 步骤 1：校验管理员身份并读取目标用户。
        ensureAdminOperator();
        User existing = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.ADMIN_USER_NOT_FOUND));
        // 步骤 2：展示名称是管理端用户资料必填项，空值直接拒绝。
        if (StrUtil.isBlank(request.displayName())) {
            throw new BusinessException("ADMIN_USER_DISPLAY_NAME_REQUIRED", ErrorMessageCatalog.ADMIN_USER_DISPLAY_NAME_REQUIRED);
        }
        // 步骤 3：邮箱规整后检查是否被其他用户占用。
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail, userId)) {
            throw new BusinessException("ADMIN_USER_EMAIL_DUPLICATED", ErrorMessageCatalog.ADMIN_USER_EMAIL_DUPLICATED);
        }
        // 步骤 4：更新基础资料；用户类型和状态只在请求显式传入时才覆盖。
        existing.updateProfile(request.displayName().trim(), normalizedEmail, normalizePhone(request.phone()), normalizeAvatar(request.avatarUrl()));
        if (StrUtil.isNotBlank(request.userType())) {
            existing = existing.toBuilder()
                .userType(parseUserType(request.userType()))
                .updatedAt(LocalDateTime.now())
                .build();
        }
        if (StrUtil.isNotBlank(request.status())) {
            existing.updateStatus(parseWritableStatus(request.status()));
        }
        // 步骤 5：保存更新后的用户聚合并返回给视图服务投影。
        userRepository.save(existing);
        return existing;
    }

    /**
     * 更新用户状态（ACTIVE/DISABLED/PENDING）。
     * @param userId 用户 ID。
     * @param status 目标状态。
     */
    public void updateUserStatus(Long userId, String status) {
        // 步骤 1：校验管理员身份并读取目标用户。
        ensureAdminOperator();
        User existing = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.ADMIN_USER_NOT_FOUND));
        // 步骤 2：解析目标状态并由领域对象刷新状态和更新时间。
        existing.updateStatus(parseWritableStatus(status));
        // 步骤 3：保存状态变更。
        userRepository.save(existing);
    }

    /**
     * 审核通过待审核用户。
     * @param userId 用户 ID。
     */
    public void approveUser(Long userId) {
        // 步骤 1：校验管理员身份并读取目标用户。
        ensureAdminOperator();
        User existing = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.ADMIN_USER_NOT_FOUND));
        // 步骤 2：只有待审核用户可以被审核通过，避免重复审核或误改禁用用户。
        if (existing.getStatus() != UserStatus.PENDING) {
            throw new BusinessException("ADMIN_USER_APPROVE_STATUS_INVALID", ErrorMessageCatalog.ADMIN_USER_APPROVE_STATUS_INVALID);
        }
        // 步骤 3：审核通过后状态流转为 ACTIVE 并持久化。
        existing.updateStatus(UserStatus.ACTIVE);
        userRepository.save(existing);
    }

    /**
     * 重置用户密码。
     * @param userId 用户 ID。
     * @param newPassword 新密码明文。
     */
    public void resetPassword(Long userId, String newPassword) {
        // 步骤 1：校验管理员身份和新密码必填条件。
        ensureAdminOperator();
        if (StrUtil.isBlank(newPassword)) {
            throw new BusinessException("ADMIN_USER_PASSWORD_REQUIRED", ErrorMessageCatalog.ADMIN_USER_PASSWORD_REQUIRED);
        }
        // 步骤 2：读取目标用户并生成新密码哈希，禁止保存明文密码。
        User existing = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.ADMIN_USER_NOT_FOUND));
        existing.updatePasswordHash(passwordHasher.hash(newPassword.trim()));
        // 步骤 3：保存密码哈希变更。
        userRepository.save(existing);
    }

    private User ensureAdminOperator() {
        // 步骤 1：从当前会话读取操作人，未登录时由会话网关抛出统一未授权异常。
        Long operatorId = authSessionGateway.currentLoginId();
        // 步骤 2：加载操作人最新状态，用户缺失时返回当前用户不存在。
        User operator = userRepository.findById(operatorId)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.CURRENT_USER_NOT_FOUND));
        // 步骤 3：只有 ADMIN 用户类型允许调用管理端用户接口。
        if (operator.getUserType() != UserType.ADMIN) {
            throw new ForbiddenException(ErrorMessageCatalog.FORBIDDEN);
        }
        return operator;
    }

    private void validateCreateRequest(AdminUserCreateRequest request) {
        // 步骤 1：请求体不能为空，避免后续字段读取空指针。
        if (request == null) {
            throw new BusinessException("ADMIN_USER_REQUEST_INVALID", ErrorMessageCatalog.ADMIN_USER_REQUEST_INVALID);
        }
        // 步骤 2：用户名、展示名和初始密码是创建用户的最小必填字段。
        if (StrUtil.hasBlank(request.username(), request.displayName(), request.password())) {
            throw new BusinessException("ADMIN_USER_REQUIRED_FIELDS_MISSING", ErrorMessageCatalog.ADMIN_USER_REQUIRED_FIELDS_MISSING);
        }
    }

    private UserStatus parseStatusFilter(String status) {
        // 步骤 1：状态筛选允许空值或 ALL，表示不过滤用户状态。
        String normalized = StrUtil.trimToEmpty(status).toUpperCase();
        if (StrUtil.isBlank(normalized) || "ALL".equals(normalized)) {
            return null;
        }
        // 步骤 2：其他值必须是可写入的用户状态枚举。
        return parseWritableStatus(normalized);
    }

    private UserStatus parseWritableStatus(String status) {
        // 步骤 1：状态写入必须显式提供目标值。
        String normalized = StrUtil.trimToEmpty(status).toUpperCase();
        if (StrUtil.isBlank(normalized)) {
            throw new BusinessException("ADMIN_USER_STATUS_INVALID", ErrorMessageCatalog.ADMIN_USER_STATUS_INVALID);
        }
        // 步骤 2：非法枚举值统一转换为管理端状态错误文案。
        try {
            return UserStatus.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("ADMIN_USER_STATUS_INVALID", ErrorMessageCatalog.ADMIN_USER_STATUS_INVALID);
        }
    }

    private UserType parseUserType(String userType) {
        // 步骤 1：新增或编辑时未指定用户类型，默认按普通用户处理。
        String normalized = StrUtil.trimToEmpty(userType).toUpperCase();
        if (StrUtil.isBlank(normalized)) {
            return UserType.USER;
        }
        // 步骤 2：非法枚举值统一转换为管理端用户类型错误文案。
        try {
            return UserType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("ADMIN_USER_TYPE_INVALID", ErrorMessageCatalog.ADMIN_USER_TYPE_INVALID);
        }
    }

    private String normalizeEmail(String email) {
        // 邮箱统一小写并把空白值规整为 null，确保唯一性检查稳定。
        return StrUtil.trimToNull(StrUtil.blankToDefault(email, "").toLowerCase());
    }

    private String normalizePhone(String phone) {
        // 手机号当前不做格式校验，只负责消除首尾空白。
        return StrUtil.trimToNull(phone);
    }

    private String normalizeAvatar(String avatarUrl) {
        // 头像地址当前不做远程可达性校验，只负责消除首尾空白。
        return StrUtil.trimToNull(avatarUrl);
    }
}
