package com.codingx.auth.domain.model;
import cn.hutool.core.util.StrUtil;
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
     * 校验 ensureActive 需要的前置条件。
     */
    public void ensureActive() {
        if (status != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("User is disabled");
        }
    }
}
