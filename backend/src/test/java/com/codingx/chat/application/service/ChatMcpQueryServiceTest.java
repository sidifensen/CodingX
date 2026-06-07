package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.codingx.mcp.application.service.ChatMcpQueryService;
import com.codingx.mcp.application.executor.ChatMcpToolExecutor;
import com.codingx.mcp.application.executor.ChatMcpToolRegistry;
import com.codingx.mcp.application.executor.ChatMcpToolResult;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证用户侧 MCP 查询会附带运行时可用状态，避免前端错误启用未接入执行器的工具。
 */
@ExtendWith(MockitoExtension.class)
class ChatMcpQueryServiceTest {

    @Mock
    private ChatMcpRepository chatMcpRepository;

    @Test
    void listEnabledMcpsMarksAvailabilityByRegisteredExecutors() {
        ChatMcpQueryService service = new ChatMcpQueryService(
            chatMcpRepository,
            new ChatMcpToolRegistry(List.of(
                new ChatMcpToolExecutor() {
                    @Override
                    public String toolId() {
                        return "weather_query";
                    }

                    @Override
                    public ChatMcpToolResult execute(String question) {
                        return new ChatMcpToolResult("weather_query", "ok", null);
                    }
                }
            ))
        );
        when(chatMcpRepository.findAllEnabled()).thenReturn(List.of(
            ChatMcp.builder().id(1L).mcpCode("weather_query").displayName("天气查询").enabled(1).build(),
            ChatMcp.builder().id(2L).mcpCode("unregistered_mcp").displayName("未接入 MCP").enabled(1).build()
        ));

        List<ChatMcp> result = service.listEnabledMcps();

        assertEquals(2, result.size());
        assertTrue(Boolean.TRUE.equals(result.get(0).getAvailable()));
        assertFalse(Boolean.TRUE.equals(result.get(1).getAvailable()));
    }
}
