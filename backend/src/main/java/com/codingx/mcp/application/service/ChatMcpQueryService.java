package com.codingx.mcp.application.service;

import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供用户侧 MCP 只读查询能力。
 */
@Service
@RequiredArgsConstructor
public class ChatMcpQueryService {

    private final ChatMcpRepository chatMcpRepository;

    /**
     * 查询当前启用 MCP 列表。
     * @return 启用 MCP 列表。
     */
    public List<ChatMcp> listEnabledMcps() {
        return chatMcpRepository.findAllEnabled();
    }
}
