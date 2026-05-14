package com.codingx.support.ai;

/**
 * 统一模型流式输出回调，供 provider 与路由层共享。
 */
public interface AiStreamHandler {

    /**
     * 推送正文增量。
     * @param delta 正文增量。
     */
    default void onContentDelta(String delta) {
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
