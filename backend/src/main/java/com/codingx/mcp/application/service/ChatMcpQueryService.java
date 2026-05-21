package com.codingx.mcp.application.service;

import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供用户侧 MCP 只读查询能力。
 */
@Service
@RequiredArgsConstructor
public class ChatMcpQueryService {

    private final ChatMcpRepository chatMcpRepository;
    private final ChatMcpToolRegistry chatMcpToolRegistry;

    /**
     * 查询当前启用 MCP 列表，并标记每项是否具备可执行能力。
     * 关键约束：用户端据此禁用“无执行器”的 MCP，避免被错误开启。
     * @return 启用 MCP 列表（附带 available 标记）。
     */
    public List<ChatMcp> listEnabledMcps() {
        Set<String> registeredToolIds = chatMcpToolRegistry.all().stream()
            .map(ChatMcpToolExecutor::toolId)
            .collect(Collectors.toSet());
        return chatMcpRepository.findAllEnabled().stream()
            .filter(mcp -> mcp != null && mcp.getMcpCode() != null)
            .map(mcp -> mcp.toBuilder().available(registeredToolIds.contains(mcp.getMcpCode())).build())
            .toList();
    }
}
