package com.codingx.chat.interfaces.controller;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.service.ChatWorkspaceBindingService;
import com.codingx.chat.interfaces.request.BindRepositoryPathRequest;
import com.codingx.common.model.ApiResponse;
import jakarta.validation.Valid;
import java.util.Map;
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

    private final ChatWorkspaceBindingService chatWorkspaceBindingService;

    /**
     * 绑定当前登录用户仓库路径。
     * @param request 绑定请求。
     * @return 绑定结果。
     */
    @PostMapping("/bind-repository")
    public ApiResponse<Map<String, String>> bindRepository(@Valid @RequestBody BindRepositoryPathRequest request) {
        ChatWorkspaceBindingService.WorkspaceBindingResult bindingResult = chatWorkspaceBindingService.bindRepositoryPathForCurrentUser(
            request.repositoryPath()
        );
        String normalizedPath = StrUtil.blankToDefault(
            bindingResult.repositoryPath(),
            request.repositoryPath()
        );
        return ApiResponse.success(Map.of(
            "repositoryPath", normalizedPath,
            "workspaceId", String.valueOf(bindingResult.workspaceId()),
            "workspaceName", StrUtil.blankToDefault(bindingResult.workspaceName(), "")
        ));
    }
}
