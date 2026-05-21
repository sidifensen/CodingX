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

    private final UserRepository userRepository;
    private final AuthSessionGateway authSessionGateway;
    private final PasswordHasher passwordHasher;
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
        ensureAdminOperator();
        UserStatus statusFilter = parseStatusFilter(status);
        return userRepository.pageUsers(current, size, statusFilter, StrUtil.trimToEmpty(keyword));
    }

    /**
     * 查询用户详情。
     * @param userId 用户 ID。
     * @return 用户详情。
     */
    public User getUserDetail(Long userId) {
        ensureAdminOperator();
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.ADMIN_USER_NOT_FOUND));
    }

    /**
     * 新增用户。
     * @param request 新增请求。
     * @return 新增后用户。
     */
    public User createUser(AdminUserCreateRequest request) {
        ensureAdminOperator();
        validateCreateRequest(request);
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail, null)) {
            throw new BusinessException("ADMIN_USER_EMAIL_DUPLICATED", ErrorMessageCatalog.ADMIN_USER_EMAIL_DUPLICATED);
        }
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
        userRepository.save(user);
        // 用户创建后立即补齐默认云端空间，确保后续“无 workspaceId 会话”有稳定归属。
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
        ensureAdminOperator();
        User existing = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.ADMIN_USER_NOT_FOUND));
        if (StrUtil.isBlank(request.displayName())) {
            throw new BusinessException("ADMIN_USER_DISPLAY_NAME_REQUIRED", ErrorMessageCatalog.ADMIN_USER_DISPLAY_NAME_REQUIRED);
        }
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail, userId)) {
            throw new BusinessException("ADMIN_USER_EMAIL_DUPLICATED", ErrorMessageCatalog.ADMIN_USER_EMAIL_DUPLICATED);
        }
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
        userRepository.save(existing);
        return existing;
    }

    /**
     * 更新用户状态（ACTIVE/DISABLED/PENDING）。
     * @param userId 用户 ID。
     * @param status 目标状态。
     */
    public void updateUserStatus(Long userId, String status) {
        ensureAdminOperator();
        User existing = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.ADMIN_USER_NOT_FOUND));
        existing.updateStatus(parseWritableStatus(status));
        userRepository.save(existing);
    }

    /**
     * 审核通过待审核用户。
     * @param userId 用户 ID。
     */
    public void approveUser(Long userId) {
        ensureAdminOperator();
        User existing = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.ADMIN_USER_NOT_FOUND));
        if (existing.getStatus() != UserStatus.PENDING) {
            throw new BusinessException("ADMIN_USER_APPROVE_STATUS_INVALID", ErrorMessageCatalog.ADMIN_USER_APPROVE_STATUS_INVALID);
        }
        existing.updateStatus(UserStatus.ACTIVE);
        userRepository.save(existing);
    }

    /**
     * 重置用户密码。
     * @param userId 用户 ID。
     * @param newPassword 新密码明文。
     */
    public void resetPassword(Long userId, String newPassword) {
        ensureAdminOperator();
        if (StrUtil.isBlank(newPassword)) {
            throw new BusinessException("ADMIN_USER_PASSWORD_REQUIRED", ErrorMessageCatalog.ADMIN_USER_PASSWORD_REQUIRED);
        }
        User existing = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.ADMIN_USER_NOT_FOUND));
        existing.updatePasswordHash(passwordHasher.hash(newPassword.trim()));
        userRepository.save(existing);
    }

    private User ensureAdminOperator() {
        Long operatorId = authSessionGateway.currentLoginId();
        User operator = userRepository.findById(operatorId)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.CURRENT_USER_NOT_FOUND));
        if (operator.getUserType() != UserType.ADMIN) {
            throw new ForbiddenException(ErrorMessageCatalog.FORBIDDEN);
        }
        return operator;
    }

    private void validateCreateRequest(AdminUserCreateRequest request) {
        if (request == null) {
            throw new BusinessException("ADMIN_USER_REQUEST_INVALID", ErrorMessageCatalog.ADMIN_USER_REQUEST_INVALID);
        }
        if (StrUtil.hasBlank(request.username(), request.displayName(), request.password())) {
            throw new BusinessException("ADMIN_USER_REQUIRED_FIELDS_MISSING", ErrorMessageCatalog.ADMIN_USER_REQUIRED_FIELDS_MISSING);
        }
    }

    private UserStatus parseStatusFilter(String status) {
        String normalized = StrUtil.trimToEmpty(status).toUpperCase();
        if (StrUtil.isBlank(normalized) || "ALL".equals(normalized)) {
            return null;
        }
        return parseWritableStatus(normalized);
    }

    private UserStatus parseWritableStatus(String status) {
        String normalized = StrUtil.trimToEmpty(status).toUpperCase();
        if (StrUtil.isBlank(normalized)) {
            throw new BusinessException("ADMIN_USER_STATUS_INVALID", ErrorMessageCatalog.ADMIN_USER_STATUS_INVALID);
        }
        try {
            return UserStatus.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("ADMIN_USER_STATUS_INVALID", ErrorMessageCatalog.ADMIN_USER_STATUS_INVALID);
        }
    }

    private UserType parseUserType(String userType) {
        String normalized = StrUtil.trimToEmpty(userType).toUpperCase();
        if (StrUtil.isBlank(normalized)) {
            return UserType.USER;
        }
        try {
            return UserType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("ADMIN_USER_TYPE_INVALID", ErrorMessageCatalog.ADMIN_USER_TYPE_INVALID);
        }
    }

    private String normalizeEmail(String email) {
        return StrUtil.trimToNull(StrUtil.blankToDefault(email, "").toLowerCase());
    }

    private String normalizePhone(String phone) {
        return StrUtil.trimToNull(phone);
    }

    private String normalizeAvatar(String avatarUrl) {
        return StrUtil.trimToNull(avatarUrl);
    }
}
