package com.codingx.chat.domain.service;
import com.codingx.chat.domain.model.ChatMessage;
import java.util.List;

/**
 * 定义 AiChatClient 的领域服务契约。
 */
public interface AiChatClient {

    /**
     * 以流式方式处理 streamChat 的结果。
     * @param history 输入参数。
     * @param handler 输入参数。
     */
    void streamChat(List<ChatMessage> history, StreamHandler handler);

    /**
     * 定义 StreamHandler 的领域服务契约。
     */
    interface StreamHandler {

        /**
         * 执行 onDelta 定义的处理逻辑。
         * @param delta 输入参数。
         */
        void onDelta(String delta);

        /**
         * 执行 onComplete 定义的处理逻辑。
         */
        void onComplete();

        /**
         * 执行 onError 定义的处理逻辑。
         * @param throwable 输入参数。
         */
        default void onError(Throwable throwable) {
        }
    }
}
