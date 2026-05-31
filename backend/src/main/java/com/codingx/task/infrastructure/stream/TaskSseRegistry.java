package com.codingx.task.infrastructure.stream;
import com.codingx.common.support.web.ClientAbortExceptionDetector;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 任务 SSE 连接注册表，维护任务标识与浏览器连接的映射。
 */
@Component
public class TaskSseRegistry {

    /** 任务标识到当前订阅连接列表的映射，使用并发容器支持运行时线程推送。 */
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 注册指定任务的 SSE 连接。
     * @param taskId 任务标识。
     * @return SSE 连接。
     */
    public SseEmitter register(Long taskId) {
        // 步骤 1：创建不过期连接并加入当前任务连接列表，支持同一任务多浏览器订阅。
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        // 步骤 2：注册完成、异常和超时清理回调，避免连接泄漏。
        emitter.onCompletion(() -> remove(taskId, emitter));
        emitter.onError(exception -> remove(taskId, emitter));
        emitter.onTimeout(() -> remove(taskId, emitter));
        try {
            // 步骤 3：首包发送连接确认事件，让前端确认订阅已建立。
            emitter.send(SseEmitter.event().name("task-connected").data(Map.of("taskId", taskId)));
        } catch (IOException exception) {
            remove(taskId, emitter);
        } catch (RuntimeException exception) {
            if (!ClientAbortExceptionDetector.isClientAbort(exception)) {
                throw exception;
            }
            // 首包发送时客户端已断开，清理连接即可，任务本身不应失败。
            remove(taskId, emitter);
        }
        return emitter;
    }

    /**
     * 发布 publish 处理的更新内容。
     * @param taskId 任务标识。
     * @param eventName SSE 事件名称。
     * @param payload 事件载荷。
     */
    public void publish(Long taskId, String eventName, Object payload) {
        // 步骤 1：没有订阅者时直接返回，任务运行不依赖浏览器连接存在。
        List<SseEmitter> current = emitters.get(taskId);
        if (current == null) {
            return;
        }
        // 步骤 2：逐个连接发送事件，单个连接断开只清理自身，不影响其他订阅者。
        for (SseEmitter emitter : current) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException exception) {
                remove(taskId, emitter);
            } catch (RuntimeException exception) {
                if (!ClientAbortExceptionDetector.isClientAbort(exception)) {
                    throw exception;
                }
                // 浏览器刷新或取消订阅只影响这条 SSE 连接，不影响任务运行态。
                remove(taskId, emitter);
            }
        }
    }

    /**
     * 移除指定任务下的 SSE 连接。
     * @param taskId 任务标识。
     * @param emitter 待移除连接。
     */
    private void remove(Long taskId, SseEmitter emitter) {
        // 步骤 1：任务没有连接列表时直接返回，兼容重复清理回调。
        List<SseEmitter> current = emitters.get(taskId);
        if (current == null) {
            return;
        }
        // 步骤 2：移除当前连接；列表为空时删除任务键，避免注册表长期持有空列表。
        current.remove(emitter);
        if (current.isEmpty()) {
            emitters.remove(taskId);
        }
    }
}
