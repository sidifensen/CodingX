package com.codingx.auth.domain.model;
import cn.hutool.core.util.StrUtil;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 定义 User 的核心领域状态与行为。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

    /**
     * 主键标识。
     */
    private Long id;

    /**
     * 登录用户名。
     */
    private String username;

    /**
     * 展示名称。
     */
    private String displayName;

    /**
     * 密码哈希值。
     */
    private String passwordHash;

    /**
     * 用户类型。
     */
    private UserType userType;

    /**
     * 当前状态值。
     */
    private UserStatus status;

    /**
     * 用户邮箱。
     */
    private String email;

    /**
     * 用户手机号。
     */
    private String phone;

    /**
     * 用户头像地址。
     */
    private String avatarUrl;

    /**
     * 最近登录时间。
     */
    private LocalDateTime lastLoginAt;

    /**
     * 最近登录 IP。
     */
    private String lastLoginIp;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 创建 create 所需数据并返回结果。
     * @param id 输入参数。
     * @param username 输入参数。
     * @param displayName 输入参数。
     * @param passwordHash 输入参数。
     * @param userType 输入参数。
     * @param status 输入参数。
     * @return 输入参数。
     */
    public static User create(Long id, String username, String displayName, String passwordHash, UserType userType, UserStatus status) {
        if (id == null) {
            throw new IllegalArgumentException("User id must not be null");
        }
        if (StrUtil.hasBlank(username, displayName, passwordHash) || userType == null || status == null) {
            throw new IllegalArgumentException("User fields must not be blank");
        }
        return User.builder()
            .id(id)
            .username(username)
            .displayName(displayName)
            .passwordHash(passwordHash)
            .userType(userType)
            .status(status)
            .build();
    }

    /**
     * 更新用户基础资料，不影响鉴权字段与创建时间。
     * @param displayName 展示名称。
     * @param email 邮箱。
     * @param phone 手机号。
     * @param avatarUrl 头像地址。
     */
    public void updateProfile(String displayName, String email, String phone, String avatarUrl) {
        if (StrUtil.isBlank(displayName)) {
            throw new IllegalArgumentException("用户展示名称不能为空");
        }
        this.displayName = displayName;
        this.email = StrUtil.trimToNull(email);
        this.phone = StrUtil.trimToNull(phone);
        this.avatarUrl = StrUtil.trimToNull(avatarUrl);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新用户状态并刷新更新时间。
     * @param status 目标状态。
     */
    public void updateStatus(UserStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("用户状态不能为空");
        }
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新用户密码哈希并刷新更新时间。
     * @param passwordHash 新密码哈希。
     */
    public void updatePasswordHash(String passwordHash) {
        if (StrUtil.isBlank(passwordHash)) {
            throw new IllegalArgumentException("密码哈希不能为空");
        }
        this.passwordHash = passwordHash;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 记录最近登录上下文。
     * @param loginTime 登录时间。
     * @param loginIp 登录IP。
     */
    public void markLogin(LocalDateTime loginTime, String loginIp) {
        this.lastLoginAt = loginTime;
        this.lastLoginIp = StrUtil.trimToNull(loginIp);
        this.updatedAt = loginTime;
    }

    /**
     * 校验 ensureActive 需要的前置条件。
     */
    public void ensureActive() {
        if (status != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("User is disabled");
        }
    }
}
