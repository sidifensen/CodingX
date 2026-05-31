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

    /**
     * 旧版 parseChunk API 只传入 lineBuffer，这里用弱引用状态表为同一个缓冲区补齐 tool_call 累积状态。
     */
    private final Map<StringBuilder, StreamState> legacyStateByLineBuffer = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * 创建单条模型流专属解析状态，避免 Spring 单例解析器在并发请求之间串用 tool_call 片段。
     * @return 新的流式解析状态。
     */
    public StreamState newStreamState() {
        // 步骤 1：每条模型流必须持有独立行缓冲，避免并发 SSE chunk 串线。
        return new StreamState(new StringBuilder());
    }

    /**
     * 按 data 行解析原始 SSE 文本，并将内容增量与结束事件回调给消费者。
     * @param rawStream 原始 SSE 文本。
     * @param consumer 流式事件消费者。
     */
    public void parse(String rawStream, StreamConsumer consumer) {
        // 步骤 1：非增量解析也创建独立状态，保证 tool_calls 分片只在本次原始文本内累积。
        StreamState streamState = newStreamState();
        for (String line : StrUtil.split(rawStream, '\n')) {
            // 步骤 2：逐行消费 data 事件，非 data 行由 consumeLine 防御性过滤。
            consumeLine(line, streamState, consumer);
        }
        // 步骤 3：原始文本没有 [DONE] 时也要派发已拼完整的工具调用。
        emitToolCalls(streamState, consumer);
    }

    /**
     * 以 chunk 方式增量消费上游 SSE 文本，并在完整行到达时立刻触发回调。
     * @param chunk 当前收到的原始文本块。
     * @param consumer 流式事件消费者。
     */
    public void parseChunk(String chunk, StringBuilder lineBuffer, StreamConsumer consumer) {
        // 步骤 1：兼容旧调用方，通过 lineBuffer 映射到对应的流状态。
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
            // 空 chunk 不改变缓冲状态，直接忽略。
            return;
        }
        // 步骤 1：追加本次网络块，等待换行符出现后再解析完整 SSE 行。
        StringBuilder lineBuffer = streamState.lineBuffer();
        lineBuffer.append(chunk);
        int lineBreakIndex = findNextLineBreak(lineBuffer);
        while (lineBreakIndex >= 0) {
            // 步骤 2：每次只截取一行消费，剩余半行继续留在缓冲区等待后续 chunk。
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
        // 步骤 1：旧版 API 在流结束时需要先取回挂在 lineBuffer 上的状态。
        StreamState streamState = legacyStateFor(lineBuffer);
        flush(streamState, consumer);
        // 步骤 2：移除弱引用表中的状态，避免长生命周期 StringBuilder 残留解析上下文。
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
            // 步骤 1：没有残留行时仍需派发 tool_calls，因为部分 provider 不发送 [DONE]。
            emitToolCalls(streamState, consumer);
            return;
        }
        // 步骤 2：把最后一段未换行内容作为完整行处理，避免尾包内容被丢弃。
        consumeLine(lineBuffer.toString(), streamState, consumer);
        lineBuffer.setLength(0);
        // 步骤 3：流结束时统一派发工具调用并清空累积器。
        emitToolCalls(streamState, consumer);
    }

    /**
     * 从 OpenAI 风格 JSON 负载中提取正文增量。
     * @param payload JSON 负载。
     * @return 正文增量。
     */
    private String extractContentDelta(String payload) {
        try {
            // 步骤 1：优先从 choices[0].delta.content 抽取，兼容非流式 message.content 兜底。
            return extractChoiceText(firstChoice(payload), "content");
        } catch (Exception exception) {
            // 单行 JSON 异常不能中断整条流，交由后续行继续解析。
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
            // 步骤 1：OpenAI 兼容 provider 的 reasoning_content 作为 thinking 增量输出。
            return extractChoiceText(firstChoice(payload), "reasoning_content");
        } catch (Exception exception) {
            // thinking 字段解析失败不影响正文流，保持降级容错。
            return null;
        }
    }

    /**
     * 读取第一个 choice，兼容 OpenAI 兼容接口的标准响应形态。
     * @param payload JSON 负载。
     * @return 第一个 choice 对象。
     */
    private JSONObject firstChoice(String payload) {
        // 步骤 1：按 OpenAI 兼容协议解析根对象并读取 choices 数组。
        JSONObject root = JSONUtil.parseObj(payload);
        JSONArray choices = root.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            // 没有 choice 时返回 null，调用方按无增量处理。
            return null;
        }
        // 步骤 2：当前业务只消费首个 choice，避免多候选输出交叉污染一条回复。
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
        // 步骤 1：流式响应优先读取 delta 字段。
        String deltaValue = extractObjectText(choice.getJSONObject("delta"), fieldName);
        if (deltaValue != null) {
            return deltaValue;
        }
        // 步骤 2：兼容部分 provider 在 message 字段返回完整内容。
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
            // 字段不存在返回 null，保留“没有增量”和“空字符串增量”的区别。
            return null;
        }
        // 字段存在但值为 null 时转为空串，交由上层 StrUtil.isNotEmpty 过滤。
        return StrUtil.nullToEmpty(source.getStr(fieldName));
    }

    /**
     * 消费单行 SSE 文本，只处理 data 行，其他控制行与空行直接忽略。
     * @param line 原始单行文本。
     * @param consumer 流式事件消费者。
     */
    private void consumeLine(String line, StreamState streamState, StreamConsumer consumer) {
        if (StrUtil.isBlank(line) || !line.startsWith("data:")) {
            // SSE 注释、event 行、id 行和空行都不参与模型内容解析。
            return;
        }
        // 步骤 1：剥离 data: 前缀并识别流结束标记。
        String payload = StrUtil.trim(line.substring(5));
        if ("[DONE]".equals(payload)) {
            emitToolCalls(streamState, consumer);
            consumer.onDone();
            return;
        }
        // 步骤 2：先派发 thinking，再累积工具调用，最后派发正文，保持 provider 原始语义顺序。
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
                // 工具名缺失说明片段不完整或异常，不能派发给工具执行链路。
                continue;
            }
            // 步骤 1：callId 缺失时生成兜底标识，arguments 保留原始拼接 JSON 字符串。
            consumer.onToolCall(new AiToolCall(
                StrUtil.blankToDefault(accumulator.callId, "tool-call-" + System.nanoTime()),
                accumulator.toolCode,
                accumulator.arguments.toString()
            ));
        }
        // 步骤 2：派发后清空累积器，避免下一轮 flush 或 DONE 重复触发同一工具调用。
        streamState.toolCallAccumulators().clear();
    }

    /**
     * 从 OpenAI tool_calls delta 中累积工具名和参数片段。
     * @param payload JSON 负载。
     */
    private void extractToolCallDeltas(String payload, StreamState streamState) {
        try {
            // 步骤 1：只处理 delta.tool_calls，非工具调用行直接返回。
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
                // 步骤 2：按 OpenAI 返回的 index 聚合分片；缺少 index 时使用数组下标兜底。
                JSONObject toolCall = toolCalls.getJSONObject(i);
                int index = toolCall.getInt("index", i);
                ToolCallAccumulator accumulator = streamState.toolCallAccumulators()
                    .computeIfAbsent(index, ignored -> new ToolCallAccumulator());
                if (StrUtil.isNotBlank(toolCall.getStr("id"))) {
                    // id 可能只在首片出现，后续分片沿用已记录值。
                    accumulator.callId = toolCall.getStr("id");
                }
                JSONObject function = toolCall.getJSONObject("function");
                if (function == null) {
                    continue;
                }
                if (StrUtil.isNotBlank(function.getStr("name"))) {
                    // name 可能和 arguments 分片分开发送，因此需要单独累积。
                    accumulator.toolCode = function.getStr("name");
                }
                if (function.containsKey("arguments")) {
                    // arguments 是 JSON 字符串片段，必须按到达顺序拼接，不能提前解析。
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
        // 步骤 1：逐字符寻找 LF；CRLF 中的 CR 会在删除已消费行时处理。
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
        // 步骤 1：删除已消费行和 LF。
        lineBuffer.delete(0, lineBreakIndex + 1);
        if (!lineBuffer.isEmpty() && lineBuffer.charAt(0) == '\r') {
            // 步骤 2：兼容 CRLF 被拆分后的残留 CR，避免下一行以 \r 开头导致 data: 匹配失败。
            lineBuffer.deleteCharAt(0);
        }
    }

    private StreamState legacyStateFor(StringBuilder lineBuffer) {
        synchronized (legacyStateByLineBuffer) {
            // 步骤 1：同一个 lineBuffer 复用同一个状态，兼容旧 API 多次 chunk 调用。
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

        /**
         * 模型返回的工具调用 ID，可缺失；派发时会生成兜底 ID。
         */
        private String callId;

        /**
         * 模型返回的工具名称，对应后端工具编码；为空时不会派发工具调用。
         */
        private String toolCode;

        /**
         * 工具参数 JSON 字符串分片累积器，按 SSE 到达顺序拼接。
         */
        private final StringBuilder arguments = new StringBuilder();
    }

    /**
     * 单条 SSE 流的可变解析状态，调用方应为每次模型请求创建独立实例。
     */
    public static final class StreamState {

        /**
         * 当前流尚未形成完整行的文本缓冲区，跨网络 chunk 保留。
         */
        private final StringBuilder lineBuffer;

        /**
         * tool_call 分片累积器，key 为 OpenAI tool_calls[].index，value 为同一工具调用的拼接状态。
         */
        private final Map<Integer, ToolCallAccumulator> toolCallAccumulators = new LinkedHashMap<>();

        private StreamState(StringBuilder lineBuffer) {
            // 步骤 1：状态绑定调用方传入的行缓冲，旧 API 和新 API 都复用同一份解析进度。
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

