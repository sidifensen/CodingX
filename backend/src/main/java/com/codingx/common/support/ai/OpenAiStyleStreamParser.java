package com.codingx.common.support.ai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 解析 OpenAI 风格的 SSE 文本流，供不同 provider 共享增量拆解逻辑。
 */
public class OpenAiStyleStreamParser {

    private final Map<StringBuilder, StreamState> legacyStateByLineBuffer = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * 创建单条模型流专属解析状态，避免 Spring 单例解析器在并发请求之间串用 tool_call 片段。
     * @return 新的流式解析状态。
     */
    public StreamState newStreamState() {
        return new StreamState(new StringBuilder());
    }

    /**
     * 按 data 行解析原始 SSE 文本，并将内容增量与结束事件回调给消费者。
     * @param rawStream 原始 SSE 文本。
     * @param consumer 流式事件消费者。
     */
    public void parse(String rawStream, StreamConsumer consumer) {
        StreamState streamState = newStreamState();
        for (String line : StrUtil.split(rawStream, '\n')) {
            consumeLine(line, streamState, consumer);
        }
        emitToolCalls(streamState, consumer);
    }

    /**
     * 以 chunk 方式增量消费上游 SSE 文本，并在完整行到达时立刻触发回调。
     * @param chunk 当前收到的原始文本块。
     * @param consumer 流式事件消费者。
     */
    public void parseChunk(String chunk, StringBuilder lineBuffer, StreamConsumer consumer) {
        parseChunk(chunk, legacyStateFor(lineBuffer), consumer);
    }

    /**
     * 以 chunk 方式增量消费上游 SSE 文本，并将工具调用片段累积在当前流专属状态中。
     * @param chunk 当前收到的原始文本块。
     * @param streamState 当前模型流的解析状态。
     * @param consumer 流式事件消费者。
     */
    public void parseChunk(String chunk, StreamState streamState, StreamConsumer consumer) {
        if (StrUtil.isEmpty(chunk)) {
            return;
        }
        StringBuilder lineBuffer = streamState.lineBuffer();
        lineBuffer.append(chunk);
        int lineBreakIndex = findNextLineBreak(lineBuffer);
        while (lineBreakIndex >= 0) {
            String line = lineBuffer.substring(0, lineBreakIndex);
            deleteConsumedLine(lineBuffer, lineBreakIndex);
            consumeLine(line, streamState, consumer);
            lineBreakIndex = findNextLineBreak(lineBuffer);
        }
    }

    /**
     * 在流式读取结束时处理尾部残留数据，避免最后一行未换行时被吞掉。
     * @param consumer 流式事件消费者。
     */
    public void flush(StringBuilder lineBuffer, StreamConsumer consumer) {
        StreamState streamState = legacyStateFor(lineBuffer);
        flush(streamState, consumer);
        legacyStateByLineBuffer.remove(lineBuffer);
    }

    /**
     * 在流式读取结束时处理当前流状态中的残留行与工具调用，避免无 [DONE] 尾包时丢失数据。
     * @param streamState 当前模型流的解析状态。
     * @param consumer 流式事件消费者。
     */
    public void flush(StreamState streamState, StreamConsumer consumer) {
        StringBuilder lineBuffer = streamState.lineBuffer();
        if (lineBuffer.isEmpty()) {
            emitToolCalls(streamState, consumer);
            return;
        }
        consumeLine(lineBuffer.toString(), streamState, consumer);
        lineBuffer.setLength(0);
        emitToolCalls(streamState, consumer);
    }

    /**
     * 从 OpenAI 风格 JSON 负载中提取正文增量。
     * @param payload JSON 负载。
     * @return 正文增量。
     */
    private String extractContentDelta(String payload) {
        try {
            return extractChoiceText(firstChoice(payload), "content");
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
            return extractChoiceText(firstChoice(payload), "reasoning_content");
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 读取第一个 choice，兼容 OpenAI 兼容接口的标准响应形态。
     * @param payload JSON 负载。
     * @return 第一个 choice 对象。
     */
    private JSONObject firstChoice(String payload) {
        JSONObject root = JSONUtil.parseObj(payload);
        JSONArray choices = root.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        return choices.getJSONObject(0);
    }

    /**
     * 按兼容策略从 delta 或 message 两种结构中抽取文本字段。
     * @param choice 首个 choice 对象。
     * @param fieldName 字段名。
     * @return 文本字段值。
     */
    private String extractChoiceText(JSONObject choice, String fieldName) {
        if (choice == null) {
            return null;
        }
        String deltaValue = extractObjectText(choice.getJSONObject("delta"), fieldName);
        if (deltaValue != null) {
            return deltaValue;
        }
        return extractObjectText(choice.getJSONObject("message"), fieldName);
    }

    /**
     * 从指定对象中读取文本字段，保留空字符串语义供上层过滤。
     * @param source 数据对象。
     * @param fieldName 字段名。
     * @return 字段文本或 null。
     */
    private String extractObjectText(JSONObject source, String fieldName) {
        if (source == null || !source.containsKey(fieldName)) {
            return null;
        }
        return StrUtil.nullToEmpty(source.getStr(fieldName));
    }

    /**
     * 消费单行 SSE 文本，只处理 data 行，其他控制行与空行直接忽略。
     * @param line 原始单行文本。
     * @param consumer 流式事件消费者。
     */
    private void consumeLine(String line, StreamState streamState, StreamConsumer consumer) {
        if (StrUtil.isBlank(line) || !line.startsWith("data:")) {
            return;
        }
        String payload = StrUtil.trim(line.substring(5));
        if ("[DONE]".equals(payload)) {
            emitToolCalls(streamState, consumer);
            consumer.onDone();
            return;
        }
        String thinkingDelta = extractThinkingDelta(payload);
        if (StrUtil.isNotEmpty(thinkingDelta)) {
            consumer.onThinkingDelta(thinkingDelta);
        }
        extractToolCallDeltas(payload, streamState);
        String delta = extractContentDelta(payload);
        if (StrUtil.isNotEmpty(delta)) {
            consumer.onContentDelta(delta);
        }
    }

    /**
     * 在模型流结束时派发已经拼完整的工具调用，并清空累积器避免污染下一轮请求。
     * @param consumer 流式事件消费者。
     */
    private void emitToolCalls(StreamState streamState, StreamConsumer consumer) {
        for (ToolCallAccumulator accumulator : streamState.toolCallAccumulators().values()) {
            if (StrUtil.isBlank(accumulator.toolCode)) {
                continue;
            }
            consumer.onToolCall(new AiToolCall(
                StrUtil.blankToDefault(accumulator.callId, "tool-call-" + System.nanoTime()),
                accumulator.toolCode,
                accumulator.arguments.toString()
            ));
        }
        streamState.toolCallAccumulators().clear();
    }

    /**
     * 从 OpenAI tool_calls delta 中累积工具名和参数片段。
     * @param payload JSON 负载。
     */
    private void extractToolCallDeltas(String payload, StreamState streamState) {
        try {
            JSONObject choice = firstChoice(payload);
            if (choice == null) {
                return;
            }
            JSONObject delta = choice.getJSONObject("delta");
            if (delta == null) {
                return;
            }
            JSONArray toolCalls = delta.getJSONArray("tool_calls");
            if (toolCalls == null || toolCalls.isEmpty()) {
                return;
            }
            for (int i = 0; i < toolCalls.size(); i++) {
                JSONObject toolCall = toolCalls.getJSONObject(i);
                int index = toolCall.getInt("index", i);
                ToolCallAccumulator accumulator = streamState.toolCallAccumulators()
                    .computeIfAbsent(index, ignored -> new ToolCallAccumulator());
                if (StrUtil.isNotBlank(toolCall.getStr("id"))) {
                    accumulator.callId = toolCall.getStr("id");
                }
                JSONObject function = toolCall.getJSONObject("function");
                if (function == null) {
                    continue;
                }
                if (StrUtil.isNotBlank(function.getStr("name"))) {
                    accumulator.toolCode = function.getStr("name");
                }
                if (function.containsKey("arguments")) {
                    accumulator.arguments.append(StrUtil.nullToEmpty(function.getStr("arguments")));
                }
            }
        } catch (Exception ignored) {
            // 无法解析工具片段时忽略当前行，保留正文流式解析稳定性。
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

    private StreamState legacyStateFor(StringBuilder lineBuffer) {
        synchronized (legacyStateByLineBuffer) {
            return legacyStateByLineBuffer.computeIfAbsent(lineBuffer, StreamState::new);
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
         * 接收模型请求的工具调用。
         * @param toolCall 工具调用。
         */
        default void onToolCall(AiToolCall toolCall) {
        }

        /**
         * 接收流式结束事件。
         */
        default void onDone() {
        }
    }

    /**
     * 流式 tool_calls 可能分片返回，先按 index 累积，结束时统一派发。
     */
    private static final class ToolCallAccumulator {

        private String callId;
        private String toolCode;
        private final StringBuilder arguments = new StringBuilder();
    }

    /**
     * 单条 SSE 流的可变解析状态，调用方应为每次模型请求创建独立实例。
     */
    public static final class StreamState {

        private final StringBuilder lineBuffer;
        private final Map<Integer, ToolCallAccumulator> toolCallAccumulators = new LinkedHashMap<>();

        private StreamState(StringBuilder lineBuffer) {
            this.lineBuffer = lineBuffer;
        }

        private StringBuilder lineBuffer() {
            return lineBuffer;
        }

        private Map<Integer, ToolCallAccumulator> toolCallAccumulators() {
            return toolCallAccumulators;
        }
    }
}

