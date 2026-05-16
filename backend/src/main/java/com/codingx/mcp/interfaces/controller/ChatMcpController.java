package com.codingx.mcp.interfaces.controller;

import com.codingx.common.model.ApiResponse;
import com.codingx.mcp.application.service.ChatMcpQueryService;
import com.codingx.mcp.domain.model.ChatMcp;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供用户侧 MCP 查询接口。
 */
@RestController
@RequestMapping("/api/chat/mcps")
@RequiredArgsConstructor
public class ChatMcpController {

    private final ChatMcpQueryService chatMcpQueryService;

    /**
     * 查询当前启用 MCP 列表。
     * @return MCP 列表。
     */
    @GetMapping
    public ApiResponse<List<ChatMcp>> listEnabledMcps() {
        return ApiResponse.success(chatMcpQueryService.listEnabledMcps());
    }
}
