package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证 MCP 执行服务会通过注册表分发到对应工具执行器。
 */
class ChatMcpExecutionServiceTest {

    /**
     * 销售工具应返回固定 mock 数据，便于当前阶段先跑通主链路。
     */
    @Test
    void executeReturnsMockSalesToolResult() {
        ChatMcpExecutionService service = new ChatMcpExecutionService(new ChatMcpToolRegistry(List.of(new MockSalesMcpToolExecutor())));

        ChatMcpToolResult result = service.execute("sales_query", "销售总额是多少");

        assertEquals("sales_query", result.toolId());
        org.junit.jupiter.api.Assertions.assertTrue(result.content().contains("销售总额"));
    }
}
