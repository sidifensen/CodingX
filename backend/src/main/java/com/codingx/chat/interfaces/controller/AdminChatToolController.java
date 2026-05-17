package com.codingx.chat.interfaces.controller;

import com.codingx.chat.application.service.AdminChatToolService;
import com.codingx.chat.domain.model.ChatTool;
import com.codingx.common.model.ApiResponse;
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
 * 提供管理端工具配置增删改查接口。
 */
@RestController
@RequestMapping("/api/admin/chat/tools")
@RequiredArgsConstructor
public class AdminChatToolController {

    private final AdminChatToolService adminChatToolService;

    /**
     * 查询全部工具配置。
     * @return 工具配置列表。
     */
    @GetMapping
    public ApiResponse<List<ChatTool>> listTools() {
        return ApiResponse.success(adminChatToolService.listAll());
    }

    /**
     * 创建工具配置。
     * @param request 请求参数。
     * @return 新增后的工具配置。
     */
    @PostMapping
    public ApiResponse<ChatTool> createTool(@RequestBody ChatTool request) {
        return ApiResponse.success(adminChatToolService.create(request));
    }

    /**
     * 更新工具配置。
     * @param id 主键。
     * @param request 请求参数。
     * @return 更新后的工具配置。
     */
    @PutMapping("/{id}")
    public ApiResponse<ChatTool> updateTool(@PathVariable Long id, @RequestBody ChatTool request) {
        return ApiResponse.success(adminChatToolService.update(id, request));
    }

    /**
     * 删除工具配置。
     * @param id 主键。
     * @return 删除结果。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTool(@PathVariable Long id) {
        adminChatToolService.delete(id);
        return ApiResponse.successMessage("删除成功");
    }
}