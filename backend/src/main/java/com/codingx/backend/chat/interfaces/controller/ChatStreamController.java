package com.codingx.backend.chat.interfaces.controller;

import com.codingx.backend.chat.infrastructure.stream.ChatSseRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat/conversations")
@RequiredArgsConstructor
public class ChatStreamController {

    private final ChatSseRegistry chatSseRegistry;

    @GetMapping("/{conversationId}/stream")
    public SseEmitter stream(@PathVariable Long conversationId) {
        return chatSseRegistry.register(conversationId);
    }
}