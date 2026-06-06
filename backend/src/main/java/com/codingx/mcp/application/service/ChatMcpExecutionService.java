package com.codingx.mcp.application.service;

import com.codingx.mcp.application.executor.ChatMcpProgressListener;
import com.codingx.mcp.application.executor.ChatMcpToolRegistry;
import com.codingx.mcp.application.executor.ChatMcpToolResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 负责统一执行聊天链路中的 MCP 工具调用。
 */
@Service
public class ChatMcpExecutionService {

    /**
     * MCP 工具注册表，负责按工具标识定位真实执行器。
     */
    private final ChatMcpToolRegistry chatMcpToolRegistry;

    /**
     * 外部 MCP Server 运行时，负责执行 mcp__{server}__{tool} 命名空间工具。
     */
    private final McpServerRuntimeService mcpServerRuntimeService;

    /**
     * Spring 生产构造器，同时注入内置 MCP 注册表和外部 MCP 运行时。
     * @param chatMcpToolRegistry 内置 MCP 工具注册表。
     * @param mcpServerRuntimeService 外部 MCP Server 运行时。
     */
    @Autowired
    public ChatMcpExecutionService(
        ChatMcpToolRegistry chatMcpToolRegistry,
        McpServerRuntimeService mcpServerRuntimeService
    ) {
        this.chatMcpToolRegistry = chatMcpToolRegistry;
        this.mcpServerRuntimeService = mcpServerRuntimeService;
    }

    /**
     * 测试兼容构造器，旧单测只验证内置 MCP 分发时不需要外部运行时。
     * @param chatMcpToolRegistry 内置 MCP 工具注册表。
     */
    public ChatMcpExecutionService(ChatMcpToolRegistry chatMcpToolRegistry) {
        this(chatMcpToolRegistry, null);
    }

    /**
     * 按工具标识执行一次 MCP 调用。
     * @param toolId 工具标识。
     * @param question 用户问题。
     * @return 工具结果。
     */
    public ChatMcpToolResult execute(String toolId, String question) {
        if (McpToolNameSupport.isNamespacedToolName(toolId)) {
            return requireExternalRuntime().execute(toolId, question);
        }
        // 步骤 1：按工具标识强制查找执行器，未注册时由注册表抛出统一异常。
        // 步骤 2：直接调用无进度版本，保持旧调用方行为不变。
        return chatMcpToolRegistry.require(toolId).execute(question);
    }

    /**
     * 按工具标识执行一次 MCP 调用，并把工具侧真实阶段透传给回调方。
     * @param toolId 工具标识。
     * @param question 用户问题。
     * @param progressListener 进度监听器。
     * @return 工具结果。
     */
    public ChatMcpToolResult execute(
        String toolId,
        String question,
        ChatMcpProgressListener progressListener
    ) {
        if (McpToolNameSupport.isNamespacedToolName(toolId)) {
            return requireExternalRuntime().execute(toolId, question);
        }
        // 步骤 1：调用方未传监听器时使用空实现，避免工具执行器重复判空。
        ChatMcpProgressListener safeListener = progressListener == null
            ? ChatMcpProgressListener.noop()
            : progressListener;
        // 步骤 2：查找真实执行器并透传进度监听器，工具内部按真实阶段回调。
        return chatMcpToolRegistry.require(toolId).execute(question, safeListener);
    }

    private McpServerRuntimeService requireExternalRuntime() {
        if (mcpServerRuntimeService == null) {
            throw new IllegalStateException("外部 MCP 运行时未初始化");
        }
        return mcpServerRuntimeService;
    }
}

