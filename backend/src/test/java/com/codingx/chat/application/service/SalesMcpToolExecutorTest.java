package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.mcp.application.service.ChatMcpToolResult;
import com.codingx.mcp.application.service.SalesMcpToolExecutor;
import org.junit.jupiter.api.Test;

/**
 * 验证销售 MCP 工具执行器的核心行为。
 */
class SalesMcpToolExecutorTest {

    /**
     * 汇总查询应返回包含销售汇总标题的文本。
     */
    @Test
    void executeBuildsSummaryForQuestion() {
        SalesMcpToolExecutor executor = new SalesMcpToolExecutor();

        ChatMcpToolResult result = executor.execute("请帮我看一下本月华东销售总额");

        assertEquals("sales_query", result.toolId());
        assertTrue(result.content().contains("销售数据汇总"));
        assertTrue(result.content().contains("本月"));
        assertTrue(result.content().contains("华东"));
    }

    /**
     * 明确询问排名时应返回排名结果。
     */
    @Test
    void executeBuildsRankingWhenQuestionAsksRanking() {
        SalesMcpToolExecutor executor = new SalesMcpToolExecutor();

        ChatMcpToolResult result = executor.execute("给我本季度销售排名前五");

        assertEquals("sales_query", result.toolId());
        assertTrue(result.content().contains("销售排名"));
        assertTrue(result.content().contains("第1名"));
    }
}
