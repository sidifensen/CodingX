package com.codingx.chat.interfaces.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.chat.application.service.ChatMemoryViewService;
import com.codingx.chat.interfaces.response.ChatLongTermMemoryResponse;
import com.codingx.common.model.ApiResponse;
import com.codingx.governance.application.service.LongTermMemoryService;
import com.codingx.governance.domain.model.GovernanceLongTermMemory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户侧长期记忆接口，负责查看和启停当前用户自己的长期记忆。
 */
@RestController
@RequestMapping("/api/chat/memories")
@RequiredArgsConstructor
public class ChatMemoryController {

    /** 长期记忆服务，执行用户归属过滤、状态更新和记忆读取。 */
    private final LongTermMemoryService longTermMemoryService;
    /** 长期记忆视图服务，负责为用户端响应补齐工作空间展示名。 */
    private final ChatMemoryViewService chatMemoryViewService;

    /**
     * 查询当前用户可见的长期记忆。
     * @param workspaceId 工作空间 ID，可为空；为空时查询用户级或未归属空间记忆。
     * @param status 状态筛选，可为空或 ALL。
     * @param includeAllWorkspaces 是否查询当前用户全部工作空间记忆，仅供记忆管理页使用。
     * @return 当前用户可见长期记忆列表。
     */
    @GetMapping
    public ApiResponse<List<ChatLongTermMemoryResponse>> listMemories(
        @RequestParam(required = false) Long workspaceId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "false") boolean includeAllWorkspaces
    ) {
        // 步骤 1：用户身份由 Sa-Token 提供，Controller 只做协议参数适配。
        Long userId = StpUtil.getLoginIdAsLong();
        List<GovernanceLongTermMemory> memories = longTermMemoryService.listUserMemories(userId, workspaceId, status, includeAllWorkspaces);
        return ApiResponse.success(chatMemoryViewService.toMemoryResponses(memories, userId));
    }

    /**
     * 更新当前用户拥有的长期记忆状态。
     * @param memoryId 记忆主键。
     * @param request 状态更新请求。
     * @return 更新后的长期记忆。
     */
    @PatchMapping("/{memoryId}/status")
    public ApiResponse<ChatLongTermMemoryResponse> updateMemoryStatus(
        @PathVariable Long memoryId,
        @RequestBody MemoryStatusUpdateRequest request
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        GovernanceLongTermMemory memory = longTermMemoryService.updateUserMemoryStatus(memoryId, userId, request == null ? null : request.status());
        return ApiResponse.success(chatMemoryViewService.toMemoryResponse(memory, userId));
    }

    /**
     * 更新当前用户拥有的长期记忆正文。
     * @param memoryId 记忆主键。
     * @param request 正文更新请求。
     * @return 更新后的长期记忆。
     */
    @PatchMapping("/{memoryId}")
    public ApiResponse<ChatLongTermMemoryResponse> updateMemoryContent(
        @PathVariable Long memoryId,
        @RequestBody MemoryContentUpdateRequest request
    ) {
        // 步骤 1：Controller 只读取登录用户和请求正文，归属校验与关键词刷新下沉到服务层。
        Long userId = StpUtil.getLoginIdAsLong();
        GovernanceLongTermMemory memory = longTermMemoryService.updateUserMemoryContent(memoryId, userId, request == null ? null : request.content());
        return ApiResponse.success(chatMemoryViewService.toMemoryResponse(memory, userId));
    }

    /**
     * 删除当前用户拥有的长期记忆。
     * @param memoryId 记忆主键。
     * @return 删除结果。
     */
    @DeleteMapping("/{memoryId}")
    public ApiResponse<Void> deleteMemory(@PathVariable Long memoryId) {
        // 步骤 1：删除采用逻辑删除，服务层会校验当前用户是否拥有该记忆。
        Long userId = StpUtil.getLoginIdAsLong();
        longTermMemoryService.deleteUserMemory(memoryId, userId);
        return ApiResponse.successMessage("删除成功");
    }

    /**
     * 长期记忆状态更新请求。
     *
     * @param status 目标状态，允许 ACTIVE 或 REJECTED。
     */
    public record MemoryStatusUpdateRequest(String status) {
    }

    /**
     * 长期记忆正文更新请求。
     *
     * @param content 新记忆正文，不能为空。
     */
    public record MemoryContentUpdateRequest(String content) {
    }
}
