package com.codingx.support.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 在首包确认前缓冲 thinking/content/complete 事件，避免 fallback 时泄漏半截脏流。
 */
public class FirstTokenBufferingHandler implements AiStreamHandler {

    private final AiStreamHandler delegate;
    private final FirstTokenAwaiter awaiter;
    private final Object lock = new Object();
    private final List<BufferedEvent> bufferedEvents = new ArrayList<>();
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
        awaiter.markContent();
        bufferOrDispatch(BufferedEvent.thinking(delta));
    }

    @Override
    public void onContentDelta(String delta) {
        awaiter.markContent();
        bufferOrDispatch(BufferedEvent.content(delta));
    }

    @Override
    public void onComplete() {
        awaiter.markComplete();
        bufferOrDispatch(BufferedEvent.complete());
    }

    @Override
    public void onError(Throwable throwable) {
        awaiter.markError(throwable);
        bufferOrDispatch(BufferedEvent.error(throwable));
    }

    /**
     * 首包确认后提交所有缓冲事件，并切换到直通模式。
     */
    public void commit() {
        List<BufferedEvent> snapshot;
        synchronized (lock) {
            if (committed) {
                return;
            }
            committed = true;
            if (bufferedEvents.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(bufferedEvents);
            bufferedEvents.clear();
        }
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
                bufferedEvents.add(event);
            }
        }
        if (dispatchNow) {
            dispatch(event);
        }
    }

    /**
     * 将缓冲事件回放给下游。
     * @param event 缓冲事件。
     */
    private void dispatch(BufferedEvent event) {
        switch (event.type()) {
            case THINKING -> delegate.onThinkingDelta(event.delta());
            case CONTENT -> delegate.onContentDelta(event.delta());
            case COMPLETE -> delegate.onComplete();
            case ERROR -> delegate.onError(event.error());
        }
    }

    /**
     * 缓冲事件。
     * @param type 事件类型。
     * @param delta 事件内容。
     */
    private record BufferedEvent(Type type, String delta, Throwable error) {

        private static BufferedEvent thinking(String delta) {
            return new BufferedEvent(Type.THINKING, delta, null);
        }

        private static BufferedEvent content(String delta) {
            return new BufferedEvent(Type.CONTENT, delta, null);
        }

        private static BufferedEvent complete() {
            return new BufferedEvent(Type.COMPLETE, null, null);
        }

        private static BufferedEvent error(Throwable error) {
            return new BufferedEvent(Type.ERROR, null, error);
        }
    }

    /**
     * 可缓冲事件类型。
     */
    private enum Type {
        THINKING,
        CONTENT,
        COMPLETE,
        ERROR
    }
}
