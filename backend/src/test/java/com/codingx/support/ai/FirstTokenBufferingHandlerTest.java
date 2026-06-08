package com.codingx.common.support.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

    /**
     * 模型可能首包只返回 tool_call；这也是有效输出，fallback 等待器不能误判为无内容。
     *
     * @throws Exception 等待首事件失败时抛出。
     */
    @Test
    void treatsToolCallAsFirstContentEventAndReplaysAfterCommit() throws Exception {
        FirstTokenAwaiter awaiter = new FirstTokenAwaiter();
        List<AiToolCall> toolCalls = new ArrayList<>();
        FirstTokenBufferingHandler handler = new FirstTokenBufferingHandler(new AiStreamHandler() {
            @Override
            public void onToolCall(AiToolCall toolCall) {
                toolCalls.add(toolCall);
            }
        }, awaiter);

        handler.onToolCall(new AiToolCall("call-1", "shell_command", "{\"command\":\"Get-ChildItem\"}"));

        assertTrue(awaiter.await(10, TimeUnit.MILLISECONDS).isSuccess());
        assertEquals(List.of(), toolCalls);

        handler.commit();

        assertEquals(1, toolCalls.size());
        assertEquals("shell_command", toolCalls.getFirst().toolCode());
    }

    /**
     * tool_call 参数分片同样代表 provider 已开始有效输出，但首包确认前不能提前触发下游 UI。
     *
     * @throws Exception 等待首事件失败时抛出。
     */
    @Test
    void treatsToolCallDeltaAsFirstContentEventAndReplaysAfterCommit() throws Exception {
        FirstTokenAwaiter awaiter = new FirstTokenAwaiter();
        List<AiToolCallDelta> toolCallDeltas = new ArrayList<>();
        FirstTokenBufferingHandler handler = new FirstTokenBufferingHandler(new AiStreamHandler() {
            @Override
            public void onToolCallDelta(AiToolCallDelta toolCallDelta) {
                toolCallDeltas.add(toolCallDelta);
            }
        }, awaiter);

        handler.onToolCallDelta(new AiToolCallDelta(
            "call-write-1",
            "write",
            "{\"path\":\"rogue_snake.html\"",
            "{\"path\":\"rogue_snake.html\""
        ));

        assertTrue(awaiter.await(10, TimeUnit.MILLISECONDS).isSuccess());
        assertEquals(List.of(), toolCallDeltas);

        handler.commit();

        assertEquals(1, toolCallDeltas.size());
        assertEquals("write", toolCallDeltas.getFirst().toolCode());
        assertTrue(toolCallDeltas.getFirst().accumulatedArguments().contains("rogue_snake.html"));
    }
}
