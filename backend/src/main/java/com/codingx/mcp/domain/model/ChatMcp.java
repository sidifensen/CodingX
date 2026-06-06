package com.codingx.mcp.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示一条可在聊天链路中启用的 MCP 配置。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMcp {

    /**
     * MCP 配置主键。
     */
    private Long id;

    /**
     * MCP 编码，聊天运行时和执行器注册表通过该编码绑定能力。
     */
    private String mcpCode;

    /**
     * MCP 展示名称。
     */
    private String displayName;

    /**
     * MCP 能力说明，供管理端和用户侧展示。
     */
    private String description;

    /**
     * MCP 分类，用于管理端筛选和用户侧分组展示。
     */
    private String category;

    /**
     * MCP 来源类型，区分内置能力和外部扩展。
     */
    private String sourceType;

    /**
     * 外部 MCP 传输类型，支持 http、sse、stdio；内置 MCP 可为空。
     */
    private String transportType;

    /**
     * stdio MCP 启动命令，非 stdio 配置可为空。
     */
    private String command;

    /**
     * stdio MCP 启动参数 JSON 数组，可为空。
     */
    private String argsJson;

    /**
     * stdio MCP 环境变量 JSON 对象，可为空；管理端返回时需要脱敏。
     */
    private String envJson;

    /**
     * http/sse MCP 远程端点地址，可为空。
     */
    private String endpointUrl;

    /**
     * http/sse MCP 请求头 JSON 对象，可为空；管理端返回时需要脱敏。
     */
    private String headersJson;

    /**
     * 最近一次工具发现生成的工具 schema 快照 JSON，可为空。
     */
    private String toolSchemaJson;

    /**
     * 外部 MCP 健康状态，例如 UNKNOWN、AVAILABLE、ERROR。
     */
    private String healthStatus;

    /**
     * 最近一次连接或发现成功时间。
     */
    private LocalDateTime lastConnectedAt;

    /**
     * 最近一次连接、发现或调用失败的中文错误摘要。
     */
    private String lastErrorMessage;

    /**
     * 启用状态，1 表示允许聊天运行时使用。
     */
    private Integer enabled;

    /**
     * 用户侧运行时可用状态（非持久化字段）。
     * true 表示当前服务实例存在对应执行器，可被用户开启。
     */
    private Boolean available;

    /**
     * 排序号，数值越小越靠前。
     */
    private Integer sortNo;

    /**
     * 配置创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 配置最近更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记，1 表示已删除。
     */
    private Integer deleted;
}
