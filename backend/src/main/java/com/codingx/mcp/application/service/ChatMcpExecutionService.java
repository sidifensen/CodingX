package com.codingx.mcp.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责统一执行聊天链路中的 MCP 工具调用。
 */
@Service
@RequiredArgsConstructor
public class ChatMcpExecutionService {

    private final ChatMcpToolRegistry chatMcpToolRegistry;

    /**
     * 按工具标识执行一次 MCP 调用。
     * @param toolId 工具标识。
     * @param question 用户问题。
     * @return 工具结果。
     */
    public ChatMcpToolResult execute(String toolId, String question) {
        return chatMcpToolRegistry.require(toolId).execute(question);
    }
}

