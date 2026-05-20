package com.codingx.admin.interfaces.controller;

import com.codingx.common.model.ApiResponse;
import com.codingx.mcp.application.service.AdminChatMcpConfigService;
import com.codingx.mcp.domain.model.ChatMcp;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供管理端 MCP 配置增删改查接口。
 */
@RestController
@RequestMapping("/api/admin/mcps")
@RequiredArgsConstructor
public class AdminChatMcpConfigController {

    private final AdminChatMcpConfigService adminChatMcpConfigService;

    /**
     * 查询全部 MCP 配置。
     * @return MCP 列表。
     */
    @GetMapping
    public ApiResponse<List<ChatMcp>> listMcps() {
        return ApiResponse.success(adminChatMcpConfigService.listAll());
    }

    /**
     * 创建 MCP 配置。
     * @param request 请求参数。
     * @return 新增后的 MCP。
     */
    @PostMapping
    public ApiResponse<ChatMcp> createMcp(@RequestBody ChatMcp request) {
        return ApiResponse.success(adminChatMcpConfigService.create(request));
    }

    /**
     * 更新 MCP 配置。
     * @param id 主键。
     * @param request 请求参数。
     * @return 更新后的 MCP。
     */
    @PutMapping("/{id}")
    public ApiResponse<ChatMcp> updateMcp(@PathVariable Long id, @RequestBody ChatMcp request) {
        return ApiResponse.success(adminChatMcpConfigService.update(id, request));
    }

    /**
     * 删除 MCP 配置。
     * @param id 主键。
     * @return 删除结果。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteMcp(@PathVariable Long id) {
        adminChatMcpConfigService.delete(id);
        return ApiResponse.successMessage("删除成功");
    }
}
