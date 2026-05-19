package com.codingx.skill.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义聊天技能配置表的数据对象映射。
 * 技能配置与 MCP 配置独立存储，避免两者数据相互污染。
 */
@Data
@TableName("skill")
public class ChatSkillDO {

    @TableId("id")
    private Long id;

    @TableField("skill_code")
    private String skillCode;

    @TableField("display_name")
    private String displayName;

    @TableField("description")
    private String description;

    @TableField("category")
    private String category;

    @TableField("source_type")
    private String sourceType;

    @TableField("enabled")
    private Integer enabled;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField("storage_key")
    private String storageKey;

    @TableField("package_storage_format")
    private String packageStorageFormat;

    @TableField("package_file_name")
    private String packageFileName;

    @TableField("package_size")
    private Long packageSize;

    @TableField("package_checksum")
    private String packageChecksum;

    @TableField("uploaded_by")
    private Long uploadedBy;

    @TableField("uploaded_at")
    private LocalDateTime uploadedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("deleted")
    private Integer deleted;
}

