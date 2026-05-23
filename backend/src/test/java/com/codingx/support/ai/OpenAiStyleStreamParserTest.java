package com.codingx.common.support.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.common.support.ai.AiToolCall;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证 OpenAI 风格流式解析器能够稳定拆解内容增量与结束标记。
 */
class OpenAiStyleStreamParserTest {

    /**
     * 解析器需要忽略空行并按顺序吐出内容增量与完成事件。
     */
    @Test
    void parseExtractsDeltaAndDoneEvents() {
        String rawStream = """
            event: message
            data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}

            data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}

            data: [DONE]
            """;
        OpenAiStyleStreamParser parser = new OpenAiStyleStreamParser();
        List<String> events = new ArrayList<>();

        parser.parse(rawStream, new OpenAiStyleStreamParser.StreamConsumer() {
            @Override
            public void onContentDelta(String delta) {
                events.add("delta:" + delta);
            }

            @Override
            public void onDone() {
                events.add("done");
            }
        });

        assertEquals(List.of("delta:Hello", "delta: world", "done"), events);
    }

    /**
     * 没有 content 的片段不应污染增量输出。
     */
    @Test
    void parseIgnoresNonContentPayloads() {
        String rawStream = """
            data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"thinking\"}}]}
            data: {\"choices\":[{\"delta\":{}}]}
            data: [DONE]
            """;
        OpenAiStyleStreamParser parser = new OpenAiStyleStreamParser();
        List<String> deltas = new ArrayList<>();
        List<String> terminals = new ArrayList<>();

        parser.parse(rawStream, new OpenAiStyleStreamParser.StreamConsumer() {
            @Override
            public void onContentDelta(String delta) {
                deltas.add(delta);
            }

            @Override
            public void onDone() {
                terminals.add("done");
            }
        });

        assertTrue(deltas.isEmpty());
        assertEquals(List.of("done"), terminals);
    }

    /**
     * 兼容解析要求 reasoning_content 既能来自 delta，也能来自 message。
     */
    @Test
    void parseExtractsThinkingFromDeltaAndMessagePayloads() {
        String rawStream = """
            data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"先判断问题类型\"}}]}
            data: {\"choices\":[{\"message\":{\"reasoning_content\":\"再组织最终答案\"}}]}
            data: [DONE]
            """;
        OpenAiStyleStreamParser parser = new OpenAiStyleStreamParser();
        List<String> thinkingDeltas = new ArrayList<>();

        parser.parse(rawStream, new OpenAiStyleStreamParser.StreamConsumer() {
            @Override
            public void onThinkingDelta(String delta) {
                thinkingDeltas.add(delta);
            }
        });

        assertEquals(List.of("先判断问题类型", "再组织最终答案"), thinkingDeltas);
    }

    /**
     * 有些 provider 会在同一个 choice 中同时返回思考和正文，两类增量都不能丢。
     */
    @Test
    void parseDispatchesThinkingAndContentFromSamePayload() {
        String rawStream = """
            data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"分析中\",\"content\":\"答案\"}}]}
            data: [DONE]
            """;
        OpenAiStyleStreamParser parser = new OpenAiStyleStreamParser();
        List<String> events = new ArrayList<>();

        parser.parse(rawStream, new OpenAiStyleStreamParser.StreamConsumer() {
            @Override
            public void onContentDelta(String delta) {
                events.add("content:" + delta);
            }

            @Override
            public void onThinkingDelta(String delta) {
                events.add("thinking:" + delta);
            }
        });

        assertEquals(List.of("thinking:分析中", "content:答案"), events);
    }

    /**
     * OpenAI 兼容流返回 tool_calls 增量时，解析器应还原工具名与参数，供后端执行本地工具。
     */
    @Test
    void parseExtractsToolCallDelta() {
        String rawStream = """
            data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"shell_command\",\"arguments\":\"{\\\"command\\\":\"}}]}}]}
            data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"Get-ChildItem\\\"}\"}}]}}]}
            data: [DONE]
            """;
        OpenAiStyleStreamParser parser = new OpenAiStyleStreamParser();
        List<AiToolCall> toolCalls = new ArrayList<>();

        parser.parse(rawStream, new OpenAiStyleStreamParser.StreamConsumer() {
            @Override
            public void onToolCall(AiToolCall toolCall) {
                toolCalls.add(toolCall);
            }
        });

        assertEquals(1, toolCalls.size());
        assertEquals("call-1", toolCalls.getFirst().callId());
        assertEquals("shell_command", toolCalls.getFirst().toolCode());
        assertEquals("{\"command\":\"Get-ChildItem\"}", toolCalls.getFirst().arguments());
    }

    /**
     * 同一个解析器 Bean 会被多个 provider 流共享，工具参数累积必须按流隔离，不能串到另一个请求。
     */
    @Test
    void parseChunkKeepsToolCallStateIsolatedPerStreamBuffer() {
        OpenAiStyleStreamParser parser = new OpenAiStyleStreamParser();
        StringBuilder firstStream = new StringBuilder();
        StringBuilder secondStream = new StringBuilder();
        List<AiToolCall> secondStreamToolCalls = new ArrayList<>();

        parser.parseChunk(
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"id\":\"call-a\",\"function\":{\"name\":\"shell_command\",\"arguments\":\"{\\\"command\\\":\"}}]}}]}\n",
            firstStream,
            new OpenAiStyleStreamParser.StreamConsumer() {
            }
        );
        parser.parseChunk(
            "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"id\":\"call-b\",\"function\":{\"name\":\"test_sync_tool\",\"arguments\":\"{\\\"message\\\":\\\"ok\\\"}\"}}]}}]}\n",
            secondStream,
            new OpenAiStyleStreamParser.StreamConsumer() {
                @Override
                public void onToolCall(AiToolCall toolCall) {
                    secondStreamToolCalls.add(toolCall);
                }
            }
        );
        parser.parseChunk(
            "data: [DONE]\n",
            secondStream,
            new OpenAiStyleStreamParser.StreamConsumer() {
                @Override
                public void onToolCall(AiToolCall toolCall) {
                    secondStreamToolCalls.add(toolCall);
                }
            }
        );

        assertEquals(1, secondStreamToolCalls.size());
        assertEquals("call-b", secondStreamToolCalls.getFirst().callId());
        assertEquals("test_sync_tool", secondStreamToolCalls.getFirst().toolCode());
        assertEquals("{\"message\":\"ok\"}", secondStreamToolCalls.getFirst().arguments());
    }
}
