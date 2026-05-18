package com.codingx.mcp.application.service;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 提供销售汇总数据的本地 mock 工具执行器，便于在未接真实 MCP server 前跑通主链路。
 */
@Component
@ConditionalOnProperty(prefix = "app.chat.mcp.mock-sales", name = "enabled", havingValue = "true")
public class MockSalesMcpToolExecutor implements ChatMcpToolExecutor {

    @Override
    public String toolId() {
        return "sales_query";
    }

    @Override
    public ChatMcpToolResult execute(String question) {
        String content;
        if (question.contains("总额")) {
            content = "销售总额为 1280 万元，本月环比增长 8%。";
        } else if (question.contains("销售量")) {
            content = "销售量为 3420 单，较上周增长 5%。";
        } else {
            content = "销售看板显示，本期销售表现稳定，核心指标保持增长。";
        }
        return new ChatMcpToolResult(toolId(), content, Map.of("question", question, "source", "mock-sales-dashboard"));
    }
}

