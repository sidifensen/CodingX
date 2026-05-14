package com.codingx.auth.infrastructure.persistence.dataobject;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义 UserDO 的数据库字段映射。
 */
@Data
@TableName("sys_user")
public class UserDO {

    /**
     * 主键标识。
     */
    @TableId("id")
    private Long id;

    /**
     * 登录用户名。
     */
    @TableField("username")
    private String username;

    /**
     * 展示名称。
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 密码哈希值。
     */
    @TableField("password_hash")
    private String passwordHash;

    /**
     * 用户类型。
     */
    @TableField("user_type")
    private String userType;

    /**
     * 当前状态值。
     */
    @TableField("status")
    private String status;

    /**
     * 创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 最后更新时间。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记。
     */
    @TableField("deleted")
    private Integer deleted;
}
