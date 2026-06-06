package com.codingx.mcp.application.service;

import cn.hutool.core.util.StrUtil;

/**
 * 外部 MCP 工具命名空间工具类。
 * <p>
 * 业务意图：模型可见工具需要和本地工具、内置 MCP 编码共享同一个 function tools 列表，因此外部 MCP
 * 统一使用 {@code mcp__{mcpCode}__{toolName}} 格式，避免不同服务暴露同名工具时发生执行路由冲突。
 */
public final class McpToolNameSupport {

    /** 外部 MCP 工具名前缀。 */
    private static final String PREFIX = "mcp__";

    /** 命名空间分隔符，固定和 Claude Code MCP 工具名保持一致。 */
    private static final String SEPARATOR = "__";

    private McpToolNameSupport() {
    }

    /**
     * 构造模型可见的外部 MCP 工具名。
     *
     * @param mcpCode MCP 服务编码。
     * @param toolName MCP 服务原始工具名。
     * @return 命名空间工具名。
     */
    public static String buildToolName(String mcpCode, String toolName) {
        return PREFIX + StrUtil.trimToEmpty(mcpCode) + SEPARATOR + StrUtil.trimToEmpty(toolName);
    }

    /**
     * 判断工具名是否符合外部 MCP 命名空间格式。
     *
     * @param toolName 待判断工具名。
     * @return true 表示可按外部 MCP 工具解析。
     */
    public static boolean isNamespacedToolName(String toolName) {
        if (StrUtil.isBlank(toolName) || !toolName.startsWith(PREFIX)) {
            return false;
        }
        String remaining = toolName.substring(PREFIX.length());
        int separatorIndex = remaining.indexOf(SEPARATOR);
        return separatorIndex > 0 && separatorIndex < remaining.length() - SEPARATOR.length();
    }

    /**
     * 解析外部 MCP 命名空间工具名。
     *
     * @param toolName 模型请求的命名空间工具名。
     * @return MCP 编码和 MCP 原始工具名。
     */
    public static NamespacedToolName parseToolName(String toolName) {
        if (!isNamespacedToolName(toolName)) {
            throw new IllegalArgumentException("MCP 工具名格式不正确：" + StrUtil.blankToDefault(toolName, ""));
        }
        String remaining = toolName.substring(PREFIX.length());
        int separatorIndex = remaining.indexOf(SEPARATOR);
        return new NamespacedToolName(
            remaining.substring(0, separatorIndex),
            remaining.substring(separatorIndex + SEPARATOR.length())
        );
    }

    /**
     * 外部 MCP 工具名拆解结果。
     *
     * @param mcpCode MCP 服务编码。
     * @param toolName MCP 服务原始工具名。
     */
    public record NamespacedToolName(
        String mcpCode, // MCP 服务编码。
        String toolName // MCP 服务原始工具名。
    ) {
    }
}
