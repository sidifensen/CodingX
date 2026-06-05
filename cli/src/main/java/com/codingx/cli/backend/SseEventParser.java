package com.codingx.cli.backend;

import cn.hutool.core.util.StrUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 解析后端 SSE 文本流，按空行切分事件块并保留多行 data 内容。
 */
class SseEventParser {

    /**
     * 消费整个 SSE 输入流；每解析出一个事件块就立即回调。
     *
     * @param reader SSE 文本读取器。
     * @param consumer 事件消费者。
     * @throws IOException 读取流失败时抛出，由调用方转成 CLI 可见错误。
     */
    void parse(BufferedReader reader, Consumer<SseEvent> consumer) throws IOException {
        String eventName = "message";
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                emit(eventName, data).ifPresent(consumer);
                eventName = "message";
                data.setLength(0);
                continue;
            }
            if (line.startsWith(":")) {
                continue;
            }
            if (line.startsWith("event:")) {
                eventName = StrUtil.blankToDefault(line.substring("event:".length()).trim(), "message");
                continue;
            }
            if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).trim());
            }
        }
        emit(eventName, data).ifPresent(consumer);
    }

    /**
     * 只在 data 非空时创建事件，避免 keep-alive 空块污染 transcript。
     */
    private Optional<SseEvent> emit(String eventName, StringBuilder data) {
        if (data.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SseEvent(eventName, data.toString()));
    }
}
