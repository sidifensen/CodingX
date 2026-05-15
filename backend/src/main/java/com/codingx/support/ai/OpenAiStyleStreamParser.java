package com.codingx.support.ai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 解析 OpenAI 风格的 SSE 文本流，供不同 provider 共享增量拆解逻辑。
 */
public class OpenAiStyleStreamParser {

    /**
     * 按 data 行解析原始 SSE 文本，并将内容增量与结束事件回调给消费者。
     * @param rawStream 原始 SSE 文本。
     * @param consumer 流式事件消费者。
     */
    public void parse(String rawStream, StreamConsumer consumer) {
        for (String line : StrUtil.split(rawStream, '\n')) {
            consumeLine(line, consumer);
        }
    }

    /**
     * 以 chunk 方式增量消费上游 SSE 文本，并在完整行到达时立刻触发回调。
     * @param chunk 当前收到的原始文本块。
     * @param consumer 流式事件消费者。
     */
    public void parseChunk(String chunk, StringBuilder lineBuffer, StreamConsumer consumer) {
        if (StrUtil.isEmpty(chunk)) {
            return;
        }
        lineBuffer.append(chunk);
        int lineBreakIndex = findNextLineBreak(lineBuffer);
        while (lineBreakIndex >= 0) {
            String line = lineBuffer.substring(0, lineBreakIndex);
            deleteConsumedLine(lineBuffer, lineBreakIndex);
            consumeLine(line, consumer);
            lineBreakIndex = findNextLineBreak(lineBuffer);
        }
    }

    /**
     * 在流式读取结束时处理尾部残留数据，避免最后一行未换行时被吞掉。
     * @param consumer 流式事件消费者。
     */
    public void flush(StringBuilder lineBuffer, StreamConsumer consumer) {
        if (lineBuffer.isEmpty()) {
            return;
        }
        consumeLine(lineBuffer.toString(), consumer);
        lineBuffer.setLength(0);
    }

    /**
     * 从 OpenAI 风格 JSON 负载中提取正文增量。
     * @param payload JSON 负载。
     * @return 正文增量。
     */
    private String extractContentDelta(String payload) {
        try {
            JSONObject root = JSONUtil.parseObj(payload);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            JSONObject choice = choices.getJSONObject(0);
            JSONObject delta = choice.getJSONObject("delta");
            if (delta == null) {
                return null;
            }
            return StrUtil.nullToEmpty(delta.getStr("content"));
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 从 OpenAI 风格 JSON 负载中提取 thinking 增量。
     * @param payload JSON 负载。
     * @return thinking 增量。
     */
    private String extractThinkingDelta(String payload) {
        try {
            JSONObject root = JSONUtil.parseObj(payload);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            JSONObject choice = choices.getJSONObject(0);
            JSONObject delta = choice.getJSONObject("delta");
            if (delta == null) {
                return null;
            }
            return StrUtil.nullToEmpty(delta.getStr("reasoning_content"));
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 消费单行 SSE 文本，只处理 data 行，其他控制行与空行直接忽略。
     * @param line 原始单行文本。
     * @param consumer 流式事件消费者。
     */
    private void consumeLine(String line, StreamConsumer consumer) {
        if (StrUtil.isBlank(line) || !line.startsWith("data:")) {
            return;
        }
        String payload = StrUtil.trim(line.substring(5));
        if ("[DONE]".equals(payload)) {
            consumer.onDone();
            return;
        }
        String delta = extractContentDelta(payload);
        if (StrUtil.isNotEmpty(delta)) {
            consumer.onContentDelta(delta);
            return;
        }
        String thinkingDelta = extractThinkingDelta(payload);
        if (StrUtil.isNotEmpty(thinkingDelta)) {
            consumer.onThinkingDelta(thinkingDelta);
        }
    }

    /**
     * 计算缓冲区中下一条完整行的边界位置，兼容 LF 与 CRLF。
     * @param lineBuffer 当前增量缓冲区。
     * @return 行尾索引，若不存在完整行则返回 -1。
     */
    private int findNextLineBreak(StringBuilder lineBuffer) {
        for (int index = 0; index < lineBuffer.length(); index++) {
            if (lineBuffer.charAt(index) == '\n') {
                return index;
            }
        }
        return -1;
    }

    /**
     * 删除已经消费的整行，并顺带去掉 CRLF 场景中残留的回车字符。
     * @param lineBreakIndex 换行符索引。
     */
    private void deleteConsumedLine(StringBuilder lineBuffer, int lineBreakIndex) {
        lineBuffer.delete(0, lineBreakIndex + 1);
        if (!lineBuffer.isEmpty() && lineBuffer.charAt(0) == '\r') {
            lineBuffer.deleteCharAt(0);
        }
    }

    /**
     * 定义流式解析回调契约。
     */
    public interface StreamConsumer {

        /**
         * 接收正文增量。
         * @param delta 正文增量。
         */
        default void onContentDelta(String delta) {
        }

        /**
         * 接收 thinking 增量。
         * @param delta thinking 增量。
         */
        default void onThinkingDelta(String delta) {
        }

        /**
         * 接收流式结束事件。
         */
        default void onDone() {
        }
    }
}
