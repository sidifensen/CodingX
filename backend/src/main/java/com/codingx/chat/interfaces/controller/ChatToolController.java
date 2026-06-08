package com.codingx.chat.interfaces.controller;

import com.codingx.common.model.ApiResponse;
import com.codingx.tool.application.service.ChatToolExecutionResult;
import com.codingx.tool.application.service.ChatToolUserService;
import com.codingx.tool.interfaces.request.ChatToolInvokeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供用户态工具调用入口，承接前端调试与本地执行场景。
 */
@RestController
@RequestMapping("/api/chat/tools")
@RequiredArgsConstructor
public class ChatToolController {

    /** 用户态工具服务，负责按当前用户上下文执行工具并返回结构化结果。 */
    private final ChatToolUserService chatToolUserService;

    /**
     * 调用指定工具，返回工具执行结果与元数据。
     * @param toolCode 工具编码。
     * @param request 请求体。
     * @return 工具执行结果。
     */
    @PostMapping("/{toolCode}/invoke")
    public ApiResponse<ChatToolExecutionResult> invokeTool(
        @PathVariable String toolCode,
        @RequestBody(required = false) ChatToolInvokeRequest request
    ) {
        String question = request == null ? null : request.question();
        boolean confirmHighRisk = request != null && Boolean.TRUE.equals(request.confirmHighRisk());
        ChatToolExecutionResult result = chatToolUserService.invokeForCurrentUser(
            toolCode,
            question,
            confirmHighRisk,
            request == null ? null : request.workspaceId(),
            request == null ? null : request.repositoryPath()
        );
        return ApiResponse.success(result);
    }
}
