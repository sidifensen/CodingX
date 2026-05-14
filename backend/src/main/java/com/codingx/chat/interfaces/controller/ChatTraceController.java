package com.codingx.chat.interfaces.controller;

import com.codingx.chat.application.service.ConversationTraceQueryService;
import com.codingx.chat.application.service.ConversationTraceView;
import com.codingx.common.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供聊天链路 Trace 的最小查询接口，供真实联调核对根记录与节点完整性。
 */
@RestController
@RequestMapping("/api/chat/traces")
@RequiredArgsConstructor
public class ChatTraceController {

    private final ConversationTraceQueryService conversationTraceQueryService;

    /**
     * 根据 traceId 返回根链路与节点集合。
     * @param traceId 链路标识。
     * @return Trace 聚合视图。
     */
    @GetMapping("/{traceId}")
    public ApiResponse<ConversationTraceView> getTrace(@PathVariable String traceId) {
        return ApiResponse.success(conversationTraceQueryService.getTrace(traceId));
    }
}
