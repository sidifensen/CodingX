package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.mcp.application.service.ChatMcpToolResult;
import com.codingx.mcp.application.service.TicketMcpToolExecutor;
import org.junit.jupiter.api.Test;

/**
 * 验证工单 MCP 工具执行器的核心行为。
 */
class TicketMcpToolExecutorTest {

    /**
     * 汇总查询应返回工单概览。
     */
    @Test
    void executeBuildsSummaryByDefault() {
        TicketMcpToolExecutor executor = new TicketMcpToolExecutor();

        ChatMcpToolResult result = executor.execute("华东区待处理工单有多少");

        assertEquals("ticket_query", result.toolId());
        assertTrue(result.content().contains("客户工单汇总概览"));
    }

    /**
     * 查询列表时应返回工单列表标题。
     */
    @Test
    void executeBuildsListWhenQuestionAsksList() {
        TicketMcpToolExecutor executor = new TicketMcpToolExecutor();

        ChatMcpToolResult result = executor.execute("给我列出紧急工单列表");

        assertEquals("ticket_query", result.toolId());
        assertTrue(result.content().contains("工单列表"));
    }
}
