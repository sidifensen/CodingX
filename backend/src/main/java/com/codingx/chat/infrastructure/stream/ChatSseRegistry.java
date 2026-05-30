package com.codingx.chat.infrastructure.stream;
import com.codingx.common.support.web.ClientAbortExceptionDetector;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 负责 ChatSseRegistry 的流式事件发布。
 */
@Component
public class ChatSseRegistry {

    /**
     * 单个运行中会话保留的最近 SSE 事件数量，覆盖页面切走再回来期间的实时续接窗口。
     */
    private static final int MAX_BUFFERED_EVENTS_PER_CONVERSATION = 200;

    /**
     * 按会话隔离 SSE 连接和断线缓冲；同一会话内注册、发布、完成必须顺序化，避免重连瞬间乱序。
     */
    private final Map<Long, ConversationStreamState> streamStates = new ConcurrentHashMap<>();

    /**
     * 为会话创建新的 SSE 连接，并回放断线期间积累的运行期事件。
     * @param conversationId 会话标识。
     * @return 已注册的 SSE 连接。
     */
    public SseEmitter register(Long conversationId) {
        return register(conversationId, new SseEmitter(0L));
    }

    /**
     * 注册指定 emitter；包内可见仅用于测试重连顺序，不暴露额外生产接口。
     * @param conversationId 会话标识。
     * @param emitter 待注册连接。
     * @return 已注册的 SSE 连接。
     */
    SseEmitter register(Long conversationId, SseEmitter emitter) {
        ConversationStreamState state =
            streamStates.computeIfAbsent(conversationId, key -> new ConversationStreamState());
        emitter.onCompletion(() -> remove(conversationId, emitter));
        emitter.onError(exception -> remove(conversationId, emitter));
        emitter.onTimeout(() -> remove(conversationId, emitter));
        synchronized (state) {
            if (replayBufferedEvents(state, conversationId, emitter)) {
                state.emitters().add(emitter);
            }
        }
        return emitter;
    }

    /**
     * 发布运行期 SSE 事件；即使当前没有页面订阅，也要先写入短期缓冲供后续重连回放。
     * @param conversationId 会话标识。
     * @param eventName 事件名。
     * @param payload 事件载荷。
     */
    public void publish(Long conversationId, String eventName, Object payload) {
        if (conversationId == null || eventName == null) {
            return;
        }
        ConversationStreamState state =
            streamStates.computeIfAbsent(conversationId, key -> new ConversationStreamState());
        synchronized (state) {
            bufferEvent(state, eventName, payload);
            for (SseEmitter emitter : state.emitters()) {
                sendEvent(conversationId, emitter, eventName, payload);
            }
        }
    }

    /**
     * 向单个 emitter 发送事件；客户端断开只清理连接，不影响后台任务继续执行。
     * @param conversationId 会话标识。
     * @param emitter 目标连接。
     * @param eventName 事件名。
     * @param payload 事件载荷。
     */
    private void sendEvent(Long conversationId, SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException exception) {
            remove(conversationId, emitter);
        } catch (RuntimeException exception) {
            if (!ClientAbortExceptionDetector.isClientAbort(exception)) {
                throw exception;
            }
            // Spring 可能把已断开的响应流包装为运行时异常，业务上只需清理该连接。
            remove(conversationId, emitter);
        }
    }

    /**
     * 缓冲运行期事件，保证用户离开页面后再回到运行中会话时能补收到断线期间输出。
     * @param state 当前会话流状态。
     * @param eventName 事件名。
     * @param payload 事件载荷。
     */
    private void bufferEvent(ConversationStreamState state, String eventName, Object payload) {
        state.eventBuffer().addLast(new BufferedSseEvent(eventName, payload));
        while (state.eventBuffer().size() > MAX_BUFFERED_EVENTS_PER_CONVERSATION) {
            state.eventBuffer().pollFirst();
        }
    }

    /**
     * 新订阅建立时回放当前运行任务的缓冲事件；失败只移除该连接，避免污染任务状态。
     * @param state 当前会话流状态。
     * @param conversationId 会话标识。
     * @param emitter 新注册连接。
     * @return 是否可以继续加入 live 连接列表。
     */
    private boolean replayBufferedEvents(
        ConversationStreamState state,
        Long conversationId,
        SseEmitter emitter
    ) {
        if (state.eventBuffer().isEmpty()) {
            return true;
        }
        for (BufferedSseEvent event : state.eventBuffer()) {
            try {
                emitter.send(SseEmitter.event().name(event.eventName()).data(event.payload()));
            } catch (IOException exception) {
                remove(conversationId, emitter);
                return false;
            } catch (RuntimeException exception) {
                if (!ClientAbortExceptionDetector.isClientAbort(exception)) {
                    throw exception;
                }
                // Spring 可能把已断开的响应流包装为运行时异常，业务上只需清理该连接。
                remove(conversationId, emitter);
                return false;
            }
        }
        return true;
    }

    /**
     * 主动完成某个会话的所有 SSE 连接，避免 done 后连接悬挂。
     * @param conversationId 会话标识。
     */
    public void complete(Long conversationId) {
        ConversationStreamState state = streamStates.remove(conversationId);
        if (state == null) {
            return;
        }
        List<SseEmitter> current;
        synchronized (state) {
            state.eventBuffer().clear();
            current = List.copyOf(state.emitters());
            state.emitters().clear();
        }
        for (SseEmitter emitter : current) {
            emitter.complete();
        }
    }

    /**
     * 移除已失效连接；若会话仍有缓冲事件，保留状态供后续页面重连回放。
     * @param conversationId 会话标识。
     * @param emitter 失效连接。
     */
    private void remove(Long conversationId, SseEmitter emitter) {
        ConversationStreamState state = streamStates.get(conversationId);
        if (state == null) {
            return;
        }
        synchronized (state) {
            state.emitters().remove(emitter);
            if (state.emitters().isEmpty() && state.eventBuffer().isEmpty()) {
                streamStates.remove(conversationId, state);
            }
        }
    }

    /**
     * 单个会话的流状态，作为同步锁使用，保证重连回放与实时发布按同一顺序进入客户端。
     * @param emitters 当前 live SSE 连接。
     * @param eventBuffer 运行中短期事件缓冲。
     */
    private record ConversationStreamState(
        CopyOnWriteArrayList<SseEmitter> emitters,
        ConcurrentLinkedDeque<BufferedSseEvent> eventBuffer
    ) {

        private ConversationStreamState() {
            this(new CopyOnWriteArrayList<>(), new ConcurrentLinkedDeque<>());
        }
    }

    /**
     * 运行期 SSE 事件快照，仅在内存中保存到任务完成，用于重连续流。
     * @param eventName 事件名。
     * @param payload 事件载荷。
     */
    private record BufferedSseEvent(String eventName, Object payload) {
    }
}
