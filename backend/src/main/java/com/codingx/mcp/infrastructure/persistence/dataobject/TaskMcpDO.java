package com.codingx.mcp.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务与 MCP 绑定表数据对象。
 */
@Data
@TableName("task_mcp")
public class TaskMcpDO {

    @TableId("id")
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("mcp_code")
    private String mcpCode;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
