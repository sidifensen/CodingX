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

    /**
     * 技能主键。
     */
    @TableId("id")
    private Long id;

    /**
     * 技能编码。
     */
    @TableField("skill_code")
    private String skillCode;

    /**
     * 技能展示名称。
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 技能说明。
     */
    @TableField("description")
    private String description;

    /**
     * 技能分类。
     */
    @TableField("category")
    private String category;

    /**
     * 技能来源。
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 启用标记，1 表示启用。
     */
    @TableField("enabled")
    private Integer enabled;

    /**
     * 排序值。
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 技能包对象存储键。
     */
    @TableField("storage_key")
    private String storageKey;

    /**
     * 技能包存储格式。
     */
    @TableField("package_storage_format")
    private String packageStorageFormat;

    /**
     * 技能包展示名称。
     */
    @TableField("package_file_name")
    private String packageFileName;

    /**
     * 技能包总字节数。
     */
    @TableField("package_size")
    private Long packageSize;

    /**
     * 技能包内容摘要。
     */
    @TableField("package_checksum")
    private String packageChecksum;

    /**
     * 上传人用户标识。
     */
    @TableField("uploaded_by")
    private Long uploadedBy;

    /**
     * 上传时间。
     */
    @TableField("uploaded_at")
    private LocalDateTime uploadedAt;

    /**
     * 创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 删除标记，0 表示有效。
     */
    @TableField("deleted")
    private Integer deleted;
}

