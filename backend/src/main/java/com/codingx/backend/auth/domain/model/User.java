package com.codingx.backend.auth.domain.model;

import cn.hutool.core.util.StrUtil;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

    private Long id;
    private String username;
    private String displayName;
    private String passwordHash;
    private UserType userType;
    private UserStatus status;

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

    public void ensureActive() {
        if (status != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("User is disabled");
        }
    }
}