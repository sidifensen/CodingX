package com.codingx.mcp.interfaces.controller;

import com.codingx.mcp.application.service.AdminChatMcpService;
import com.codingx.mcp.application.service.AdminChatMcpService.McpToolHealthView;
import com.codingx.common.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供管理端 MCP 工具清单与探测接口。
 */
@RestController
@RequestMapping("/api/admin/chat/mcps/tools")
@RequiredArgsConstructor
public class AdminChatMcpController {

    private final AdminChatMcpService adminChatMcpService;

    /**
     * 返回当前后端注册的 MCP 工具列表。
     * @return 工具列表。
     */
    @GetMapping
    public ApiResponse<List<McpToolHealthView>> listMcpTools() {
        return ApiResponse.success(adminChatMcpService.listTools());
    }

    /**
     * 对指定工具执行探测。
     * @param toolId 工具标识。
     * @return 探测结果。
     */
    @GetMapping("/{toolId}/ping")
    public ApiResponse<McpToolHealthView> pingMcpTool(@PathVariable String toolId) {
        return ApiResponse.success(adminChatMcpService.pingTool(toolId));
    }
}

