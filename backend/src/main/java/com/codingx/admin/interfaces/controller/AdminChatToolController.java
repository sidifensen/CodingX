package com.codingx.admin.interfaces.controller;

import com.codingx.tool.application.service.AdminChatToolService;
import com.codingx.tool.application.service.AdminChatToolService.ToolHealthView;
import com.codingx.tool.application.service.AdminChatToolService.ToolInvokeView;
import com.codingx.tool.domain.model.ChatTool;
import com.codingx.tool.interfaces.request.ChatToolInvokeRequest;
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
@RequestMapping("/api/admin/tools")
@RequiredArgsConstructor
public class AdminChatToolController {

    /** 工具管理服务，承接工具配置 CRUD、探测和手动调用逻辑。 */
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

    /**
     * 查询工具配置+执行器接入状态。
     * @return 工具健康视图列表。
     */
    @GetMapping("/health")
    public ApiResponse<List<ToolHealthView>> listToolHealthViews() {
        return ApiResponse.success(adminChatToolService.listToolHealthViews());
    }

    /**
     * 对指定工具执行一次探测。
     * @param toolCode 工具编码。
     * @return 探测结果。
     */
    @GetMapping("/{toolCode}/ping")
    public ApiResponse<ToolHealthView> pingTool(@PathVariable String toolCode) {
        return ApiResponse.success(adminChatToolService.pingTool(toolCode));
    }

    /**
     * 手工调用指定工具。
     * @param toolCode 工具编码。
     * @param request 调用参数。
     * @return 调用结果。
     */
    @PostMapping("/{toolCode}/invoke")
    public ApiResponse<ToolInvokeView> invokeTool(@PathVariable String toolCode, @RequestBody(required = false) ChatToolInvokeRequest request) {
        String question = request == null ? null : request.question();
        return ApiResponse.success(adminChatToolService.invokeTool(toolCode, question));
    }
}


