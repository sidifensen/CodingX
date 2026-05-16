package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义聊天技能配置表的数据对象映射。
 * 当前沿用存量 `chat_mcp` 物理表，避免迁移期间中断线上数据。
 */
@Data
@TableName("chat_mcp")
public class ChatSkillDO {

    @TableId("id")
    private Long id;

    @TableField("mcp_code")
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

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("deleted")
    private Integer deleted;
}
