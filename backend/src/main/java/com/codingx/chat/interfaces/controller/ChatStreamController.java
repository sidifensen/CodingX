package com.codingx.chat.interfaces.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.chat.application.service.chat.ChatStreamRequestApplicationService;
import com.codingx.chat.application.service.chat.ChatStreamRequestApplicationService.StreamChatRequest;
import com.codingx.common.idempotent.IdempotentSubmit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 负责处理 ChatStreamController 的 HTTP 请求并调用应用服务。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatStreamController {

    /**
     * 聊天流请求应用服务，承接参数解析、会话解析、能力选择、SSE 注册和后台派发。
     */
    private final ChatStreamRequestApplicationService chatStreamRequestApplicationService;

    /**
     * 建立单次 SSE 聊天入口，并在同一请求内完成注册与消息发送。
     * @param question 用户问题。
     * @param conversationId 会话标识，可为空。
     * @param deepThinking 是否启用深度思考。
     * @param mcpCodes 显式传入的 MCP 编码，可为空。
     * @return SSE emitter。
     */
    @GetMapping("/stream")
    @IdempotentSubmit(
        key = "T(cn.dev33.satoken.stp.StpUtil).getLoginIdAsLong() + ':' + (#conversationId == null ? 'new' : #conversationId)",
        message = "当前会话处理中，请稍后再发起新的对话",
        code = "CHAT_STREAM_DUPLICATE",
        waitTimeMs = 0,
        leaseTimeMs = 30000
    )
    public SseEmitter streamChat(
        @RequestParam String question,
        @RequestParam(required = false) Long conversationId,
        @RequestParam(required = false) Long workspaceId,
        @RequestParam(required = false) Boolean deepThinking,
        @RequestParam(required = false) String mcpCodes,
        @RequestParam(required = false) String skillCodes,
        @RequestParam(required = false) String skillPaths,
        @RequestParam(required = false) String expertCode,
        @RequestParam(required = false) String runtimeTarget,
        @RequestParam(required = false) String repositoryPath,
        @RequestParam(required = false) String messages,
        @RequestParam(required = false) String attachmentIds
    ) {
        // 步骤 1：Controller 仅做登录入口校验，业务权限和参数解析下沉到应用服务。
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        // 步骤 2：把 HTTP 查询参数封装成应用服务请求快照，避免 Controller 编排业务规则。
        return chatStreamRequestApplicationService.openStream(
            new StreamChatRequest(
                question,
                conversationId,
                workspaceId,
                deepThinking,
                mcpCodes,
                skillCodes,
                skillPaths,
                expertCode,
                runtimeTarget,
                repositoryPath,
                messages,
                attachmentIds
            ),
            userId
        );
    }

    /**
     * 兼容旧调用签名，未显式传 MCP 参数时自动回退到默认 MCP 集合。
     * @param question 用户问题。
     * @param conversationId 会话标识。
     * @param deepThinking 是否深度思考。
     * @return SSE emitter。
     */
    public SseEmitter streamChat(String question, Long conversationId, Boolean deepThinking) {
        return streamChat(question, conversationId, null, deepThinking, null, null, null, null, null, null, null, null);
    }

    /**
     * 兼容旧的完整参数调用签名；旧调用方不传 runtimeTarget 时继续按云端持久化处理。
     * @param question 用户问题。
     * @param conversationId 会话标识。
     * @param workspaceId 工作空间标识。
     * @param deepThinking 是否深度思考。
     * @param mcpCodes 显式 MCP 编码。
     * @param skillCodes 显式技能编码。
     * @param expertCode 专家编码。
     * @param repositoryPath 当前仓库路径。
     * @param messages 结构化消息 JSON。
     * @param attachmentIds 附件主键列表。
     * @return SSE emitter。
     */
    public SseEmitter streamChat(
        String question,
        Long conversationId,
        Long workspaceId,
        Boolean deepThinking,
        String mcpCodes,
        String skillCodes,
        String expertCode,
        String repositoryPath,
        String messages,
        String attachmentIds
    ) {
        return streamChat(
            question,
            conversationId,
            workspaceId,
            deepThinking,
            mcpCodes,
            skillCodes,
            null,
            expertCode,
            null,
            repositoryPath,
            messages,
            attachmentIds
        );
    }

    /**
     * 兼容旧调用签名，允许显式 MCP 与技能编码。
     * @param question 用户问题。
     * @param conversationId 会话标识。
     * @param deepThinking 是否深度思考。
     * @param mcpCodes 显式 MCP 编码。
     * @param skillCodes 显式技能编码。
     * @return SSE emitter。
     */
    public SseEmitter streamChat(
        String question,
        Long conversationId,
        Boolean deepThinking,
        String mcpCodes,
        String skillCodes
    ) {
        return streamChat(question, conversationId, null, deepThinking, mcpCodes, skillCodes, null, null, null, null, null, null);
    }

    /**
     * 兼容传入结构化消息但未显式提供 repositoryPath 的调用签名。
     * @param question 用户问题。
     * @param conversationId 会话标识。
     * @param deepThinking 是否深度思考。
     * @param mcpCodes 显式 MCP 编码。
     * @param skillCodes 显式技能编码。
     * @param messages 结构化消息 JSON。
     * @return SSE emitter。
     */
    public SseEmitter streamChat(
        String question,
        Long conversationId,
        Boolean deepThinking,
        String mcpCodes,
        String skillCodes,
        String messages
    ) {
        return streamChat(question, conversationId, null, deepThinking, mcpCodes, skillCodes, null, null, null, null, messages, null);
    }

    /**
     * 兼容显式传入 repositoryPath 与结构化 messages 但未传附件参数的调用签名。
     * @param question 用户问题。
     * @param conversationId 会话标识。
     * @param deepThinking 是否深度思考。
     * @param mcpCodes 显式 MCP 编码。
     * @param skillCodes 显式技能编码。
     * @param repositoryPath 当前仓库路径。
     * @param messages 结构化消息 JSON。
     * @return SSE emitter。
     */
    public SseEmitter streamChat(
        String question,
        Long conversationId,
        Boolean deepThinking,
        String mcpCodes,
        String skillCodes,
        String repositoryPath,
        String messages
    ) {
        return streamChat(question, conversationId, null, deepThinking, mcpCodes, skillCodes, null, null, null, repositoryPath, messages, null);
    }

    /**
     * 兼容旧的按会话订阅流接口，避免阶段切换时现有调用完全失效。
     * @param conversationId 会话标识。
     * @return 已注册的 SSE emitter。
     */
    @GetMapping("/conversations/{conversationId}/stream")
    public SseEmitter stream(@PathVariable Long conversationId) {
        // 步骤 1：Controller 只做登录入口校验，订阅权限校验交给应用服务。
        StpUtil.checkLogin();
        return chatStreamRequestApplicationService.subscribeConversation(conversationId, StpUtil.getLoginIdAsLong());
    }
}
