package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证 MCP 执行服务会通过注册表分发到对应工具执行器。
 */
class ChatMcpExecutionServiceTest {

    /**
     * 销售工具应通过注册表分发到真实执行器并返回销售汇总内容。
     */
    @Test
    void executeDispatchesToSalesExecutor() {
        ChatMcpExecutionService service = new ChatMcpExecutionService(
            new ChatMcpToolRegistry(List.of(new SalesMcpToolExecutor()))
        );

        ChatMcpToolResult result = service.execute("sales_query", "销售总额是多少");

        assertEquals("sales_query", result.toolId());
        assertTrue(result.content().contains("销售数据汇总"));
    }
}
