package com.codingx.chat.interfaces.controller;

import com.codingx.chat.application.service.ChatWorkspaceQueryService;
import com.codingx.chat.domain.model.ChatExecutionStep;
import com.codingx.chat.domain.model.ChatMessageArtifact;
import com.codingx.chat.domain.model.ChatMessageReference;
import com.codingx.chat.domain.model.ChatSkill;
import com.codingx.common.model.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供聊天工作区右栏所需的回放接口。
 */
@RestController
@RequestMapping("/api/chat/conversations")
@RequiredArgsConstructor
public class ChatWorkspaceController {

    private final ChatWorkspaceQueryService chatWorkspaceQueryService;

    @GetMapping("/{conversationId}/steps")
    public ApiResponse<List<ChatExecutionStep>> listSteps(@PathVariable Long conversationId) {
        return ApiResponse.success(chatWorkspaceQueryService.listSteps(conversationId));
    }

    @GetMapping("/{conversationId}/references")
    public ApiResponse<List<ChatMessageReference>> listReferences(@PathVariable Long conversationId) {
        return ApiResponse.success(chatWorkspaceQueryService.listReferences(conversationId));
    }

    @GetMapping("/{conversationId}/artifacts")
    public ApiResponse<List<ChatMessageArtifact>> listArtifacts(@PathVariable Long conversationId) {
        return ApiResponse.success(chatWorkspaceQueryService.listArtifacts(conversationId));
    }

    @GetMapping("/{conversationId}/current-skills")
    public ApiResponse<List<ChatSkill>> listCurrentSkills(@PathVariable Long conversationId) {
        return ApiResponse.success(chatWorkspaceQueryService.listCurrentSkills(conversationId));
    }
}
