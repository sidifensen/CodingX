package com.codingx.tool.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 聊天工具配置表数据对象。
 */
@Data
@TableName("tool")
public class ChatToolDO {

    /**
     * 工具配置主键。
     */
    @TableId("id")
    private Long id;

    /**
     * 工具编码，模型工具调用和管理端配置都通过该编码定位工具。
     */
    @TableField("tool_code")
    private String toolCode;

    /**
     * 工具展示名称。
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 工具能力说明，供管理端和用户侧展示。
     */
    @TableField("description")
    private String description;

    /**
     * 工具分类，用于管理端筛选和前端分组展示。
     */
    @TableField("category")
    private String category;

    /**
     * 工具来源类型，区分内置工具和外部扩展工具。
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 启用状态，1 表示聊天运行时可调用。
     */
    @TableField("enabled")
    private Integer enabled;

    /**
     * 排序号，数值越小越靠前。
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 最近更新时间。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记，1 表示已删除。
     */
    @TableField("deleted")
    private Integer deleted;
}
