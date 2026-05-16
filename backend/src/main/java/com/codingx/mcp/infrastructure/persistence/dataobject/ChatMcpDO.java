package com.codingx.mcp.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 聊天 MCP 配置表数据对象。
 */
@Data
@TableName("chat_mcp")
public class ChatMcpDO {

    @TableId("id")
    private Long id;

    @TableField("mcp_code")
    private String mcpCode;

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
