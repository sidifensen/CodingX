package com.codingx.common.support.ai;

import java.util.concurrent.CompletableFuture;

/**
 * 表示一次已经启动的流式模型会话，供路由层等待完成或主动取消。
 * @param cancelAction 取消动作。
 * @param completion 流完成信号。
 */
public record AiStreamSession(
    Runnable cancelAction,
    CompletableFuture<Void> completion
) {

    /**
     * 取消当前流式会话，避免 fallback 时旧流继续向下游推送事件。
     */
    public void cancel() {
        if (cancelAction != null) {
            cancelAction.run();
        }
    }
}

