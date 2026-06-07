package com.codingx.chat.interfaces.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.common.model.ApiResponse;
import com.codingx.governance.application.service.LongTermMemoryService;
import com.codingx.governance.domain.model.GovernanceLongTermMemory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户侧长期记忆接口，负责候选查看和确认/拒绝操作。
 */
@RestController
@RequestMapping("/api/chat/memories")
@RequiredArgsConstructor
public class ChatMemoryController {

    /** 长期记忆服务，执行用户归属过滤、状态更新和记忆候选读取。 */
    private final LongTermMemoryService longTermMemoryService;

    /**
     * 查询当前用户可见的长期记忆。
     * @param workspaceId 工作空间 ID，可为空；为空时查询用户级或未归属空间记忆。
     * @param status 状态筛选，可为空或 ALL。
     * @return 当前用户可见长期记忆列表。
     */
    @GetMapping
    public ApiResponse<List<GovernanceLongTermMemory>> listMemories(
        @RequestParam(required = false) Long workspaceId,
        @RequestParam(required = false) String status
    ) {
        // 步骤 1：用户身份由 Sa-Token 提供，Controller 只做协议参数适配。
        Long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(longTermMemoryService.listUserMemories(userId, workspaceId, status));
    }

    /**
     * 更新当前用户拥有的长期记忆状态。
     * @param memoryId 记忆主键。
     * @param request 状态更新请求。
     * @return 更新后的长期记忆。
     */
    @PatchMapping("/{memoryId}/status")
    public ApiResponse<GovernanceLongTermMemory> updateMemoryStatus(
        @PathVariable Long memoryId,
        @RequestBody MemoryStatusUpdateRequest request
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(longTermMemoryService.updateUserMemoryStatus(memoryId, userId, request == null ? null : request.status()));
    }

    /**
     * 长期记忆状态更新请求。
     *
     * @param status 目标状态，允许 ACTIVE、REJECTED 或 PENDING。
     */
    public record MemoryStatusUpdateRequest(String status) {
    }
}
