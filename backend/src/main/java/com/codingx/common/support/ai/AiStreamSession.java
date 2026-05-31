package com.codingx.common.support.ai;

import java.util.concurrent.CompletableFuture;

/**
 * 表示一次已经启动的流式模型会话，供路由层等待完成或主动取消。
 * @param cancelAction 取消动作。
 * @param completion 流完成信号。
 */
public record AiStreamSession(
    Runnable cancelAction, // 当前流式请求的取消动作，可为空；fallback 或中断时由路由层触发。
    CompletableFuture<Void> completion // 流式请求完成信号，正常完成或异常结束都会通过该 Future 反馈给路由层。
) {

    /**
     * 取消当前流式会话，避免 fallback 时旧流继续向下游推送事件。
     */
    public void cancel() {
        if (cancelAction != null) {
            // 步骤 1：仅在 provider 提供取消动作时执行，兼容无法主动取消的实现。
            cancelAction.run();
        }
    }
}

