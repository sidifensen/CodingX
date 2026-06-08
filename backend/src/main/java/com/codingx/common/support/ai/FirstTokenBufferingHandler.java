package com.codingx.common.support.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 在首包确认前缓冲 thinking/content/tool/complete 事件，避免 fallback 时泄漏半截脏流。
 */
public class FirstTokenBufferingHandler implements AiStreamHandler {

    /**
     * 首包确认成功后接收真实事件的下游处理器，通常负责写 SSE 响应或持久化事件。
     */
    private final AiStreamHandler delegate;

    /**
     * 首包等待器，用于把 provider 的首个内容、完成或错误事件反馈给路由层。
     */
    private final FirstTokenAwaiter awaiter;

    /**
     * 缓冲区互斥锁，保护 committed 与 bufferedEvents 在 provider 回调线程之间的一致性。
     */
    private final Object lock = new Object();

    /**
     * 首包确认前暂存的流式事件，fallback 时不会向前端泄漏失败候选的半截内容。
     */
    private final List<BufferedEvent> bufferedEvents = new ArrayList<>();

    /**
     * 是否已经提交到下游；true 后新增事件进入直通模式，不再写入缓冲区。
     */
    private volatile boolean committed;

    /**
     * 使用下游处理器与首包等待器构造缓冲包装器。
     * @param delegate 下游处理器。
     * @param awaiter 首包等待器。
     */
    public FirstTokenBufferingHandler(AiStreamHandler delegate, FirstTokenAwaiter awaiter) {
        this.delegate = delegate;
        this.awaiter = awaiter;
    }

    @Override
    public void onThinkingDelta(String delta) {
        // 步骤 1：thinking 片段也视为有效首包，说明 provider 已经开始产生可见内容。
        awaiter.markContent();
        // 步骤 2：首包确认前先缓冲，确认后再直通下游。
        bufferOrDispatch(BufferedEvent.thinking(delta));
    }

    @Override
    public void onContentDelta(String delta) {
        // 步骤 1：正文片段是最常见的首包成功信号。
        awaiter.markContent();
        // 步骤 2：根据提交状态决定缓存或派发，避免 fallback 污染最终响应。
        bufferOrDispatch(BufferedEvent.content(delta));
    }

    @Override
    public void onToolCall(AiToolCall toolCall) {
        // 步骤 1：工具调用也代表模型产生了有效输出，可结束首包等待。
        awaiter.markContent();
        // 步骤 2：工具调用在首包确认前必须缓冲，避免失败候选触发真实工具执行。
        bufferOrDispatch(BufferedEvent.toolCall(toolCall));
    }

    @Override
    public void onToolCallDelta(AiToolCallDelta toolCallDelta) {
        // 步骤 1：工具参数进度说明 provider 已开始返回有效工具输出，可结束首包等待。
        awaiter.markContent();
        // 步骤 2：首包确认前只缓冲参数进度，避免失败候选提前污染最终 UI。
        bufferOrDispatch(BufferedEvent.toolCallDelta(toolCallDelta));
    }

    @Override
    public void onComplete() {
        // 步骤 1：无内容完成会唤醒路由层，由路由层判定是否 fallback。
        awaiter.markComplete();
        // 步骤 2：完成事件同样进入缓冲，只有首包成功后才允许透传。
        bufferOrDispatch(BufferedEvent.complete());
    }

    @Override
    public void onError(Throwable throwable) {
        // 步骤 1：记录首包前错误，路由层可据此标记失败并切换候选。
        awaiter.markError(throwable);
        // 步骤 2：错误事件先缓冲，首包后错误才会透传给最终 handler。
        bufferOrDispatch(BufferedEvent.error(throwable));
    }

    /**
     * 首包确认后提交所有缓冲事件，并切换到直通模式。
     */
    public void commit() {
        List<BufferedEvent> snapshot;
        synchronized (lock) {
            if (committed) {
                // 已提交后重复调用直接返回，避免同一批缓冲事件被回放两次。
                return;
            }
            committed = true;
            if (bufferedEvents.isEmpty()) {
                // 没有首包前事件时只切换直通模式，后续事件会直接派发。
                return;
            }
            // 步骤 1：复制缓冲快照后清空原列表，缩短锁持有时间，避免回调下游时阻塞 provider 线程。
            snapshot = new ArrayList<>(bufferedEvents);
            bufferedEvents.clear();
        }
        // 步骤 2：在锁外按原始顺序回放事件，保持 SSE 输出顺序且避免下游回调造成死锁。
        for (BufferedEvent event : snapshot) {
            dispatch(event);
        }
    }

    /**
     * 根据是否已提交决定缓冲还是立刻派发事件。
     * @param event 待处理事件。
     */
    private void bufferOrDispatch(BufferedEvent event) {
        boolean dispatchNow;
        synchronized (lock) {
            dispatchNow = committed;
            if (!dispatchNow) {
                // 首包确认前只记录事件，不能触达下游响应或工具执行链路。
                bufferedEvents.add(event);
            }
        }
        if (dispatchNow) {
            // 首包确认后进入直通模式，新事件立即派发给下游处理器。
            dispatch(event);
        }
    }

    /**
     * 将缓冲事件回放给下游。
     * @param event 缓冲事件。
     */
    private void dispatch(BufferedEvent event) {
        // 步骤 1：根据事件类型恢复原始回调，保证缓冲包装器对下游透明。
        switch (event.type()) {
            case THINKING -> delegate.onThinkingDelta(event.delta());
            case CONTENT -> delegate.onContentDelta(event.delta());
            case TOOL_CALL -> delegate.onToolCall(event.toolCall());
            case TOOL_CALL_DELTA -> delegate.onToolCallDelta(event.toolCallDelta());
            case COMPLETE -> delegate.onComplete();
            case ERROR -> delegate.onError(event.error());
        }
    }

    /**
     * 缓冲事件。
     * @param type 事件类型。
     * @param delta 事件内容。
     * @param toolCall 工具调用。
     * @param error 错误对象，仅 ERROR 事件有值。
     */
    private record BufferedEvent(
        Type type, // 事件类型，决定提交后回放到下游的回调方法。
        String delta, // thinking 或正文增量内容，非文本事件为空。
        AiToolCall toolCall, // 工具调用事件载荷，非工具调用事件为空。
        AiToolCallDelta toolCallDelta, // 工具参数进度载荷，非参数进度事件为空。
        Throwable error // 错误事件载荷，非错误事件为空。
    ) {

        private static BufferedEvent thinking(String delta) {
            // thinking 与 content 都属于可见内容，但需要保留独立事件类型。
            return new BufferedEvent(Type.THINKING, delta, null, null, null);
        }

        private static BufferedEvent content(String delta) {
            // 正文增量只携带 delta，不携带工具调用或错误对象。
            return new BufferedEvent(Type.CONTENT, delta, null, null, null);
        }

        private static BufferedEvent toolCall(AiToolCall toolCall) {
            // 工具调用必须整体缓冲，避免首包失败候选提前触发工具执行。
            return new BufferedEvent(Type.TOOL_CALL, null, toolCall, null, null);
        }

        private static BufferedEvent toolCallDelta(AiToolCallDelta toolCallDelta) {
            // 工具参数进度也必须缓冲到首包确认后，保持 fallback 语义一致。
            return new BufferedEvent(Type.TOOL_CALL_DELTA, null, null, toolCallDelta, null);
        }

        private static BufferedEvent complete() {
            // 完成事件不携带额外载荷，只表达 provider 流已经结束。
            return new BufferedEvent(Type.COMPLETE, null, null, null, null);
        }

        private static BufferedEvent error(Throwable error) {
            // 错误事件保留原始异常，便于路由层和下游日志读取根因。
            return new BufferedEvent(Type.ERROR, null, null, null, error);
        }
    }

    /**
     * 可缓冲事件类型。
     */
    private enum Type {
        THINKING,
        CONTENT,
        TOOL_CALL,
        TOOL_CALL_DELTA,
        COMPLETE,
        ERROR
    }
}

