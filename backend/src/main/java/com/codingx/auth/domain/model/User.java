package com.codingx.auth.domain.model;
import cn.hutool.core.util.StrUtil;
import com.codingx.common.error.ErrorMessageCatalog;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 用户领域对象，封装登录身份、管理端资料和状态流转规则。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

    /**
     * 用户主键标识。
     */
    private Long id;

    /**
     * 登录用户名，全局唯一。
     */
    private String username;

    /**
     * 展示名称，用于前端会话和管理端列表显示。
     */
    private String displayName;

    /**
     * 密码哈希值，禁止保存明文密码。
     */
    private String passwordHash;

    /**
     * 用户类型，决定是否可访问管理端能力。
     */
    private UserType userType;

    /**
     * 当前状态值，ACTIVE 才允许登录。
     */
    private UserStatus status;

    /**
     * 用户邮箱，管理端维护，可为空。
     */
    private String email;

    /**
     * 用户手机号，管理端维护，可为空。
     */
    private String phone;

    /**
     * 用户头像地址，管理端维护，可为空。
     */
    private String avatarUrl;

    /**
     * 最近登录时间，用于管理端审计展示。
     */
    private LocalDateTime lastLoginAt;

    /**
     * 最近登录 IP，用于管理端审计展示。
     */
    private String lastLoginIp;

    /**
     * 用户创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 用户资料或认证状态最后更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 创建具备登录能力的用户领域对象。
     * @param id 用户主键。
     * @param username 登录用户名。
     * @param displayName 展示名称。
     * @param passwordHash 密码哈希值。
     * @param userType 用户类型。
     * @param status 用户状态。
     * @return 用户领域对象。
     */
    public static User create(Long id, String username, String displayName, String passwordHash, UserType userType, UserStatus status) {
        // 步骤 1：用户主键必须存在，避免后续登录会话无法绑定身份。
        if (id == null) {
            throw new IllegalArgumentException(ErrorMessageCatalog.AUTH_USER_ID_REQUIRED);
        }
        // 步骤 2：登录名、展示名、密码哈希、类型和状态是认证闭环的最小必填字段。
        if (StrUtil.hasBlank(username, displayName, passwordHash) || userType == null || status == null) {
            throw new IllegalArgumentException(ErrorMessageCatalog.AUTH_USER_FIELDS_REQUIRED);
        }
        // 步骤 3：只构造核心认证字段，扩展资料由仓储映射或管理端用例继续补齐。
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
        // 步骤 1：展示名称是管理端和前端都需要展示的必填资料。
        if (StrUtil.isBlank(displayName)) {
            throw new IllegalArgumentException(ErrorMessageCatalog.AUTH_USER_DISPLAY_NAME_REQUIRED);
        }
        // 步骤 2：可选联系方式统一 trim 为 null，避免空字符串在数据库和接口间反复传播。
        this.displayName = displayName;
        this.email = StrUtil.trimToNull(email);
        this.phone = StrUtil.trimToNull(phone);
        this.avatarUrl = StrUtil.trimToNull(avatarUrl);
        // 步骤 3：资料变更后刷新更新时间，便于管理端审计。
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新用户状态并刷新更新时间。
     * @param status 目标状态。
     */
    public void updateStatus(UserStatus status) {
        // 步骤 1：状态不能为空，避免用户进入不可解释的认证状态。
        if (status == null) {
            throw new IllegalArgumentException(ErrorMessageCatalog.AUTH_USER_STATUS_REQUIRED);
        }
        // 步骤 2：写入目标状态并刷新更新时间。
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新用户密码哈希并刷新更新时间。
     * @param passwordHash 新密码哈希。
     */
    public void updatePasswordHash(String passwordHash) {
        // 步骤 1：密码哈希不能为空，禁止把空密码状态写入用户聚合。
        if (StrUtil.isBlank(passwordHash)) {
            throw new IllegalArgumentException(ErrorMessageCatalog.AUTH_USER_PASSWORD_HASH_REQUIRED);
        }
        // 步骤 2：仅保存哈希值，并刷新更新时间供管理端审计。
        this.passwordHash = passwordHash;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 记录最近登录上下文。
     * @param loginTime 登录时间。
     * @param loginIp 登录IP。
     */
    public void markLogin(LocalDateTime loginTime, String loginIp) {
        // 步骤 1：记录登录时间作为最近登录审计字段。
        this.lastLoginAt = loginTime;
        // 步骤 2：来源 IP 可为空，空白值统一规整为 null。
        this.lastLoginIp = StrUtil.trimToNull(loginIp);
        // 步骤 3：登录审计变更也刷新更新时间。
        this.updatedAt = loginTime;
    }

    /**
     * 校验用户是否允许创建登录会话。
     */
    public void ensureActive() {
        // 步骤 1：只有 ACTIVE 用户可以登录，禁用和待审核用户都拒绝创建会话。
        if (status != UserStatus.ACTIVE) {
            throw new IllegalArgumentException(ErrorMessageCatalog.AUTH_USER_DISABLED);
        }
    }
}
