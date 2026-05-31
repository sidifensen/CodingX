package com.codingx.mcp.application.executor;

import com.codingx.common.error.ErrorMessageCatalog;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 负责按工具标识查找对应的 MCP 执行器。
 */
@Component
@RequiredArgsConstructor
public class ChatMcpToolRegistry {

    /** MCP 执行器集合，用于按工具标识定位具体执行实现。 */
    private final List<ChatMcpToolExecutor> executors;

    /**
     * 查找指定工具的执行器。
     * @param toolId 工具标识。
     * @return 命中的执行器。
     */
    public ChatMcpToolExecutor require(String toolId) {
        return executors.stream()
            .filter(executor -> executor.toolId().equals(toolId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(ErrorMessageCatalog.CHAT_MCP_TOOL_NOT_FOUND_PREFIX + toolId));
    }

    /**
     * 返回当前已注册的全部 MCP 执行器，供管理端做工具清单与健康探测。
     * @return 执行器列表。
     */
    public List<ChatMcpToolExecutor> all() {
        return List.copyOf(executors);
    }
}

