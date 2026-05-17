package com.codingx.auth.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.codingx.auth.domain.model.UserStatus;
import com.codingx.auth.infrastructure.persistence.dataobject.UserDO;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 验证用户模型与持久化对象具备管理端用户治理所需字段。
 */
class UserSchemaCompatibilityTest {

    /**
     * 用户状态枚举应支持待审核，满足管理端筛选与审核通过动作。
     */
    @Test
    void userStatusSupportsPending() {
        assertEquals(UserStatus.PENDING, UserStatus.valueOf("PENDING"));
    }

    /**
     * UserDO 应包含用户管理页展示所需的治理字段映射。
     */
    @Test
    void userDoContainsAdminManagementFields() {
        Set<String> fieldNames = Arrays.stream(UserDO.class.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toSet());

        assertNotNull(fieldNames);
        assertEquals(true, fieldNames.contains("email"));
        assertEquals(true, fieldNames.contains("phone"));
        assertEquals(true, fieldNames.contains("avatarUrl"));
        assertEquals(true, fieldNames.contains("lastLoginAt"));
        assertEquals(true, fieldNames.contains("lastLoginIp"));
    }
}