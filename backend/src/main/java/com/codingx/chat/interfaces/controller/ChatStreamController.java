package com.codingx.chat.interfaces.controller;
import cn.dev33.satoken.stp.StpUtil;
import com.codingx.chat.application.command.CreateConversationCommand;
import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatStreamExecutionService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.infrastructure.stream.ChatSseRegistry;
import java.util.Map;
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
     * 会话应用服务依赖。
     */
    private final ChatConversationApplicationService chatConversationApplicationService;

    /**
     * 聊天应用服务依赖。
     */
    private final ChatStreamExecutionService chatStreamExecutionService;

    /**
     * ChatSseRegistry 依赖。
     */
    private final ChatSseRegistry chatSseRegistry;

    /**
     * 建立单次 SSE 聊天入口，并在同一请求内完成注册与消息发送。
     * @param question 用户问题。
     * @param conversationId 会话标识，可为空。
     * @param deepThinking 是否启用深度思考。
     * @return SSE emitter。
     */
    @GetMapping("/stream")
    public SseEmitter streamChat(
        @RequestParam String question,
        @RequestParam(required = false) Long conversationId,
        @RequestParam(required = false) Boolean deepThinking
    ) {
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        Long actualConversationId = resolveConversationId(conversationId, userId);
        boolean deepThinkingEnabled = Boolean.TRUE.equals(deepThinking);
        SseEmitter emitter = chatSseRegistry.register(actualConversationId);
        chatSseRegistry.publish(actualConversationId, "meta", Map.of(
            "conversationId", actualConversationId,
            "deepThinking", deepThinkingEnabled
        ));
        chatStreamExecutionService.dispatch(new SendChatMessageCommand(actualConversationId, question), userId);
        return emitter;
    }

    /**
     * 兼容旧的按会话订阅流接口，避免阶段切换时现有调用完全失效。
     * @param conversationId 输入参数。
     * @return 输入参数。
     */
    @GetMapping("/conversations/{conversationId}/stream")
    public SseEmitter stream(@PathVariable Long conversationId) {
        StpUtil.checkLogin();
        return chatSseRegistry.register(conversationId);
    }

    /**
     * 在新建会话和复用既有会话之间解析最终会话标识。
     * @param conversationId 传入会话标识。
     * @param userId 当前用户标识。
     * @return 最终会话标识。
     */
    private Long resolveConversationId(Long conversationId, Long userId) {
        if (conversationId != null) {
            return conversationId;
        }
        ChatConversation conversation = chatConversationApplicationService.createConversation(new CreateConversationCommand(null), userId);
        return conversation.getId();
    }
}
