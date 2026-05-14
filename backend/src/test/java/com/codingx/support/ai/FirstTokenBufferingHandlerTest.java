package com.codingx.support.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证首包缓冲处理器会在首 token 到达后再顺序释放缓存内容。
 */
class FirstTokenBufferingHandlerTest {

    /**
     * 首 token 到来前缓冲的增量不应提前对外发送。
     */
    @Test
    void flushesBufferedDeltasAfterFirstTokenArrives() {
        List<String> deltas = new ArrayList<>();
        List<String> terminals = new ArrayList<>();
        FirstTokenBufferingHandler handler = new FirstTokenBufferingHandler(new AiStreamHandler() {
            @Override
            public void onContentDelta(String delta) {
                deltas.add(delta);
            }

            @Override
            public void onComplete() {
                terminals.add("done");
            }
        });

        handler.bufferDelta("pre-1");
        handler.bufferDelta("pre-2");
        assertEquals(List.of(), deltas);

        handler.onContentDelta("first");
        handler.onComplete();

        assertEquals(List.of("pre-1", "pre-2", "first"), deltas);
        assertEquals(List.of("done"), terminals);
    }
}
