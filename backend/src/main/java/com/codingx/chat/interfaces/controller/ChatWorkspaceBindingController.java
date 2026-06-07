package com.codingx.chat.interfaces.controller;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.service.ChatWorkspaceBindingService;
import com.codingx.chat.interfaces.request.BindRepositoryPathRequest;
import com.codingx.common.model.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供用户态仓库目录绑定接口，打通 Electron 选中目录与后端执行上下文。
 */
@RestController
@RequestMapping("/api/chat/workspace")
@RequiredArgsConstructor
public class ChatWorkspaceBindingController {

    /** 工作空间绑定服务，负责校验本地目录并写入当前用户绑定关系。 */
    private final ChatWorkspaceBindingService chatWorkspaceBindingService;

    /**
     * 绑定当前登录用户仓库路径。
     * @param request 绑定请求。
     * @return 绑定结果。
     */
    @PostMapping("/bind-repository")
    public ApiResponse<WorkspaceBindingResponse> bindRepository(@Valid @RequestBody BindRepositoryPathRequest request) {
        ChatWorkspaceBindingService.WorkspaceBindingResult bindingResult = chatWorkspaceBindingService.bindRepositoryPathForCurrentUser(
            request.repositoryPath()
        );
        String normalizedPath = StrUtil.blankToDefault(
            bindingResult.repositoryPath(),
            request.repositoryPath()
        );
        return ApiResponse.success(new WorkspaceBindingResponse(
            normalizedPath,
            String.valueOf(bindingResult.workspaceId()),
            StrUtil.blankToDefault(bindingResult.workspaceName(), ""),
            bindingResult.projectProfile(),
            bindingResult.activeMemoryCount()
        ));
    }

    /**
     * 用户端目录绑定响应，包含工作空间标识、最新项目画像和已生效记忆数量。
     *
     * @param repositoryPath 规范化仓库路径。
     * @param workspaceId 工作空间 ID 字符串，前端保持字符串避免 Long 精度丢失。
     * @param workspaceName 工作空间展示名称。
     * @param projectProfile 项目画像轻量视图，可为空。
     * @param activeMemoryCount 当前用户已生效长期记忆数量。
     */
    public record WorkspaceBindingResponse(
        String repositoryPath,
        String workspaceId,
        String workspaceName,
        ChatWorkspaceBindingService.ProjectProfileView projectProfile,
        int activeMemoryCount
    ) {
    }
}
