package com.codingx.admin.interfaces.controller;

import com.codingx.admin.application.service.AdminWorkspaceService;
import com.codingx.chat.interfaces.response.AdminChatConversationListItemResponse;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.common.model.ApiResponse;
import com.codingx.workspace.interfaces.response.AdminWorkspaceListItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供管理端工作空间只读管理接口，支持分页、搜索和运行目标筛选。
 */
@RestController
@RequestMapping("/api/admin/workspaces")
@RequiredArgsConstructor
public class AdminWorkspaceController {

    private final AdminWorkspaceService adminWorkspaceService;

    /**
     * 分页查询工作空间列表。
     * @param current 当前页码。
     * @param size 每页条数。
     * @param keyword 可选关键字。
     * @param runtimeTarget 可选运行目标。
     * @return 工作空间分页结果。
     */
    @GetMapping
    public ApiResponse<PageResult<AdminWorkspaceListItemResponse>> listWorkspaces(
        @RequestParam(defaultValue = "1") int current,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "ALL") String runtimeTarget
    ) {
        return ApiResponse.success(adminWorkspaceService.pageWorkspaces(current, size, keyword, runtimeTarget));
    }

    /**
     * 分页查询指定工作空间下的会话列表。
     * @param workspaceId 工作空间标识。
     * @param current 当前页码。
     * @param size 每页条数。
     * @param keyword 可选关键字。
     * @return 工作空间内会话分页结果。
     */
    @GetMapping("/{workspaceId}/conversations")
    public ApiResponse<PageResult<AdminChatConversationListItemResponse>> listWorkspaceConversations(
        @PathVariable Long workspaceId,
        @RequestParam(defaultValue = "1") int current,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(
            adminWorkspaceService.pageWorkspaceConversations(workspaceId, current, size, keyword)
        );
    }
}
