package com.codingx.chat.interfaces.controller;

import com.codingx.chat.application.service.AdminChatTraceService;
import com.codingx.chat.application.service.ConversationTraceView;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.common.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供聊天 Trace 后台管理接口。
 */
@RestController
@RequestMapping("/api/admin/chat/traces")
@RequiredArgsConstructor
public class AdminChatTraceController {

    private final AdminChatTraceService adminChatTraceService;

    @GetMapping
    public ApiResponse<List<ChatTraceRun>> listTraces() {
        return ApiResponse.success(adminChatTraceService.listRecentTraces());
    }

    @GetMapping("/{traceId}")
    public ApiResponse<ConversationTraceView> getTrace(@PathVariable String traceId) {
        return ApiResponse.success(adminChatTraceService.getTrace(traceId));
    }
}
