package com.codingx.backend.auth.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sys_user")
public class UserDO {

    @TableId("id")
    private Long id;

    @TableField("username")
    private String username;

    @TableField("display_name")
    private String displayName;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("user_type")
    private String userType;

    @TableField("status")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("deleted")
    private Integer deleted;
}