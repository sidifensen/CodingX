package com.codingx.common.support.ai;

/**
 * 统一模型流式输出回调，支持元信息、thinking、正文和终态事件。
 */
public interface AiStreamHandler {

    /**
     * 通知命中的 provider 与模型元信息。
     * @param provider provider 名称。
     * @param model 模型名称。
     */
    default void onMetadata(String provider, String model) {
    }

    /**
     * 推送思考增量。
     * @param delta 思考增量。
     */
    default void onThinkingDelta(String delta) {
    }

    /**
     * 推送正文增量。
     * @param delta 正文增量。
     */
    default void onContentDelta(String delta) {
    }

    /**
     * 推送模型请求执行的工具调用。
     * @param toolCall 工具调用。
     */
    default void onToolCall(AiToolCall toolCall) {
    }

    /**
     * 推送流式完成事件。
     */
    default void onComplete() {
    }

    /**
     * 推送流式错误事件。
     * @param throwable 异常对象。
     */
    default void onError(Throwable throwable) {
    }
}

