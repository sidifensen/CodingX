package com.codingx.backend.auth.infrastructure.persistence.dataobject;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Maps the persistence fields used by UserDO.
 */
@Data
@TableName("sys_user")
public class UserDO {

    /**
     * Primary identifier.
     */
    @TableId("id")
    private Long id;

    /**
     * Login username.
     */
    @TableField("username")
    private String username;

    /**
     * Display name.
     */
    @TableField("display_name")
    private String displayName;

    /**
     * Password hash.
     */
    @TableField("password_hash")
    private String passwordHash;

    /**
     * User type.
     */
    @TableField("user_type")
    private String userType;

    /**
     * Current status value.
     */
    @TableField("status")
    private String status;

    /**
     * Creation timestamp.
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * Last update timestamp.
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * Logical deletion flag.
     */
    @TableField("deleted")
    private Integer deleted;
}
