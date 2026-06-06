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
@TableName("mcp")
public class ChatMcpDO {

    /**
     * MCP 配置主键。
     */
    @TableId("id")
    private Long id;

    /**
     * MCP 编码，聊天运行时按该编码引用外部能力。
     */
    @TableField("mcp_code")
    private String mcpCode;

    /**
     * MCP 展示名称。
     */
    @TableField("display_name")
    private String displayName;

    /**
     * MCP 能力说明，供管理端和用户侧展示。
     */
    @TableField("description")
    private String description;

    /**
     * MCP 分类，用于管理端筛选和用户侧分组。
     */
    @TableField("category")
    private String category;

    /**
     * MCP 来源类型，区分内置配置和外部扩展配置。
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 外部 MCP 传输类型，支持 http、sse、stdio；内置 MCP 可为空。
     */
    @TableField("transport_type")
    private String transportType;

    /**
     * stdio MCP 启动命令，非 stdio 配置可为空。
     */
    @TableField("command")
    private String command;

    /**
     * stdio MCP 启动参数 JSON 数组，可为空。
     */
    @TableField("args_json")
    private String argsJson;

    /**
     * stdio MCP 环境变量 JSON 对象，可为空。
     */
    @TableField("env_json")
    private String envJson;

    /**
     * http/sse MCP 远程端点地址，可为空。
     */
    @TableField("endpoint_url")
    private String endpointUrl;

    /**
     * http/sse MCP 请求头 JSON 对象，可为空。
     */
    @TableField("headers_json")
    private String headersJson;

    /**
     * 最近一次工具发现生成的工具 schema 快照 JSON，可为空。
     */
    @TableField("tool_schema_json")
    private String toolSchemaJson;

    /**
     * 外部 MCP 健康状态，例如 UNKNOWN、AVAILABLE、ERROR。
     */
    @TableField("health_status")
    private String healthStatus;

    /**
     * 最近一次连接或发现成功时间。
     */
    @TableField("last_connected_at")
    private LocalDateTime lastConnectedAt;

    /**
     * 最近一次连接、发现或调用失败的中文错误摘要。
     */
    @TableField("last_error_message")
    private String lastErrorMessage;

    /**
     * 启用状态，1 表示聊天运行时可使用。
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
