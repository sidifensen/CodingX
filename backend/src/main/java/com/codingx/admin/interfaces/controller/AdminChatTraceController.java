package com.codingx.chat.interfaces.controller;

import com.codingx.chat.application.service.AdminChatTraceService;
import com.codingx.chat.application.service.AdminTraceRunPageResultView;
import com.codingx.chat.application.service.ConversationTraceView;
import com.codingx.common.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * 分页查询链路运行记录，支持 traceId 精确过滤。
     *
     * @param current 当前页码（从 1 开始）。
     * @param size 每页条数。
     * @param traceId 可选 traceId 过滤条件。
     * @return 分页结果。
     */
    @GetMapping
    public ApiResponse<AdminTraceRunPageResultView> listTraces(
        @RequestParam(defaultValue = "1") int current,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String traceId
    ) {
        return ApiResponse.success(adminChatTraceService.pageTraces(current, size, traceId));
    }

    /**
     * 查询单条链路详情（根记录 + 节点集合）。
     *
     * @param traceId 链路标识。
     * @return 聚合详情。
     */
    @GetMapping("/{traceId}")
    public ApiResponse<ConversationTraceView> getTrace(@PathVariable String traceId) {
        return ApiResponse.success(adminChatTraceService.getTrace(traceId));
    }
}
