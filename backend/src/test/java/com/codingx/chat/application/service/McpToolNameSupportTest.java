package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.mcp.application.service.McpToolNameSupport;
import org.junit.jupiter.api.Test;

/**
 * 验证外部 MCP 工具名的模型可见命名空间格式。
 */
class McpToolNameSupportTest {

    /**
     * 外部工具必须使用 mcp__{server}__{tool} 形式，避免和本地工具或内置 MCP 编码冲突。
     */
    @Test
    void shouldBuildAndParseNamespacedToolName() {
        String namespacedName = McpToolNameSupport.buildToolName("github", "search_repos");

        assertEquals("mcp__github__search_repos", namespacedName);
        assertTrue(McpToolNameSupport.isNamespacedToolName(namespacedName));
        McpToolNameSupport.NamespacedToolName parsed = McpToolNameSupport.parseToolName(namespacedName);
        assertEquals("github", parsed.mcpCode());
        assertEquals("search_repos", parsed.toolName());
    }

    /**
     * 非 MCP 命名空间工具名应明确返回 false，避免执行服务误把本地工具路由到 MCP。
     */
    @Test
    void shouldRejectPlainToolNames() {
        assertFalse(McpToolNameSupport.isNamespacedToolName("weather_query"));
        assertFalse(McpToolNameSupport.isNamespacedToolName("mcp__missing"));
    }
}
