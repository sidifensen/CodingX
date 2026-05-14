package com.codingx.chat.interfaces.controller;
import com.codingx.chat.infrastructure.stream.ChatSseRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 负责处理 ChatStreamController 的 HTTP 请求并调用应用服务。
 */
@RestController
@RequestMapping("/api/chat/conversations")
@RequiredArgsConstructor
public class ChatStreamController {

    /**
     * ChatSseRegistry 依赖。
     */
    private final ChatSseRegistry chatSseRegistry;

    /**
     * 以流式方式处理 stream 的结果。
     * @param conversationId 输入参数。
     * @return 输入参数。
     */
    @GetMapping("/{conversationId}/stream")
    public SseEmitter stream(@PathVariable Long conversationId) {
        return chatSseRegistry.register(conversationId);
    }
}
