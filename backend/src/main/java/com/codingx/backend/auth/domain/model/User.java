package com.codingx.backend.auth.domain.model;
import cn.hutool.core.util.StrUtil;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Models the core domain state and behavior for User.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

    /**
     * Primary identifier.
     */
    private Long id;
    /**
     * Login username.
     */
    private String username;
    /**
     * Display name.
     */
    private String displayName;
    /**
     * Password hash.
     */
    private String passwordHash;
    /**
     * User type.
     */
    private UserType userType;
    /**
     * Current status value.
     */
    private UserStatus status;

    /**
     * Creates the data required by create and returns the result.
     * @param id input argument.
     * @param username input argument.
     * @param displayName input argument.
     * @param passwordHash input argument.
     * @param userType input argument.
     * @param status input argument.
     * @return processing result.
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
     * Ensures the preconditions required by ensureActive.
     */
    public void ensureActive() {
        if (status != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("User is disabled");
        }
    }
}
