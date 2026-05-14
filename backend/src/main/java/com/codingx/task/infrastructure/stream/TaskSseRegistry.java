package com.codingx.task.infrastructure.stream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 负责 TaskSseRegistry 的流式事件发布。
 */
@Component
public class TaskSseRegistry {

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 执行 register 定义的处理逻辑。
     * @param taskId 输入参数。
     * @return 输入参数。
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
     * 发布 publish 处理的更新内容。
     * @param taskId 输入参数。
     * @param eventName 输入参数。
     * @param payload 输入参数。
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
     * 执行 remove 定义的处理逻辑。
     * @param taskId 输入参数。
     * @param emitter 输入参数。
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
