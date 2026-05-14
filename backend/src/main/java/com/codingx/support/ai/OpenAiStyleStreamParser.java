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
            if (StrUtil.isBlank(line) || !line.startsWith("data:")) {
                continue;
            }
            String payload = StrUtil.trim(line.substring(5));
            if ("[DONE]".equals(payload)) {
                consumer.onDone();
                continue;
            }
            String delta = extractContentDelta(payload);
            if (StrUtil.isNotEmpty(delta)) {
                consumer.onContentDelta(delta);
            }
        }
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
         * 接收流式结束事件。
         */
        default void onDone() {
        }
    }
}
