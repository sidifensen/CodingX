package com.codingx.support.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证首包缓冲处理器会在首 token 到达后再顺序释放缓存事件。
 */
class FirstTokenBufferingHandlerTest {

    /**
     * 首 token 到来前缓冲的增量不应提前对外发送。
     */
    @Test
    void flushesBufferedDeltasAfterFirstTokenArrives() {
        List<String> deltas = new ArrayList<>();
        List<String> thinkingDeltas = new ArrayList<>();
        List<String> terminals = new ArrayList<>();
        FirstTokenAwaiter awaiter = new FirstTokenAwaiter();
        FirstTokenBufferingHandler handler = new FirstTokenBufferingHandler(new AiStreamHandler() {
            @Override
            public void onThinkingDelta(String delta) {
                thinkingDeltas.add(delta);
            }

            @Override
            public void onContentDelta(String delta) {
                deltas.add(delta);
            }

            @Override
            public void onComplete() {
                terminals.add("done");
            }
        }, awaiter);

        handler.onThinkingDelta("pre-thinking");
        handler.onContentDelta("pre-1");
        handler.onContentDelta("pre-2");
        assertEquals(List.of(), deltas);

        handler.commit();
        handler.onContentDelta("first");
        handler.onComplete();

        assertEquals(List.of("pre-thinking"), thinkingDeltas);
        assertEquals(List.of("pre-1", "pre-2", "first"), deltas);
        assertEquals(List.of("done"), terminals);
    }

    /**
     * 首包前出错时不应提前把 thinking、内容或错误泄漏给下游。
     */
    @Test
    void buffersEventsBeforeCommitAndDoesNotLeakFailedProbeEvents() {
        List<String> deltas = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        FirstTokenBufferingHandler handler = new FirstTokenBufferingHandler(new AiStreamHandler() {
            @Override
            public void onContentDelta(String delta) {
                deltas.add(delta);
            }

            @Override
            public void onError(Throwable throwable) {
                errors.add(throwable.getMessage());
            }
        }, new FirstTokenAwaiter());

        handler.onThinkingDelta("thinking-before-first-token");
        handler.onContentDelta("buffered-before-commit");
        handler.onError(new IllegalStateException("probe failed"));

        assertEquals(List.of(), deltas);
        assertEquals(List.of(), errors);
    }
}
