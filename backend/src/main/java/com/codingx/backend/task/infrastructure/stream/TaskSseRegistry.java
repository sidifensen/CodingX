package com.codingx.backend.task.infrastructure.stream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Publishes streaming updates for TaskSseRegistry.
 */
@Component
public class TaskSseRegistry {

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * Executes the logic defined by register.
     * @param taskId input argument.
     * @return processing result.
     */
    public SseEmitter register(Long taskId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(taskId, emitter));
        emitter.onTimeout(() -> remove(taskId, emitter));
        try {
            emitter.send(SseEmitter.event().name("task-connected").data(Map.of("taskId", taskId)));
        } catch (IOException exception) {
            remove(taskId, emitter);
        }
        return emitter;
    }

    /**
     * Publishes the update handled by publish.
     * @param taskId input argument.
     * @param eventName input argument.
     * @param payload input argument.
     */
    public void publish(Long taskId, String eventName, Object payload) {
        List<SseEmitter> current = emitters.get(taskId);
        if (current == null) {
            return;
        }
        for (SseEmitter emitter : current) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException exception) {
                remove(taskId, emitter);
            }
        }
    }

    /**
     * Executes the logic defined by remove.
     * @param taskId input argument.
     * @param emitter input argument.
     */
    private void remove(Long taskId, SseEmitter emitter) {
        List<SseEmitter> current = emitters.get(taskId);
        if (current == null) {
            return;
        }
        current.remove(emitter);
        if (current.isEmpty()) {
            emitters.remove(taskId);
        }
    }
}
