package com.codingx.backend.chat.infrastructure.stream;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ChatSseRegistry {

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(Long conversationId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(conversationId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(conversationId, emitter));
        emitter.onTimeout(() -> remove(conversationId, emitter));
        try {
            emitter.send(SseEmitter.event().name("chat-connected").data(Map.of("conversationId", conversationId)));
        } catch (IOException exception) {
            remove(conversationId, emitter);
        }
        return emitter;
    }

    public void publish(Long conversationId, String eventName, Object payload) {
        List<SseEmitter> current = emitters.get(conversationId);
        if (current == null) {
            return;
        }
        for (SseEmitter emitter : current) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException exception) {
                remove(conversationId, emitter);
            }
        }
    }

    private void remove(Long conversationId, SseEmitter emitter) {
        List<SseEmitter> current = emitters.get(conversationId);
        if (current == null) {
            return;
        }
        current.remove(emitter);
        if (current.isEmpty()) {
            emitters.remove(conversationId);
        }
    }
}