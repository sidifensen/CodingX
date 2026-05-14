package com.codingx.support.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
