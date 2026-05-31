package com.codingx.chat.domain.port;

import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.support.ai.AiToolCall;
import com.codingx.tool.application.service.ChatToolSpec;
import java.util.List;

/**
 * AI 聊天客户端端口，隔离聊天应用层与具体模型 provider、流式协议和工具调用协议。
 */
public interface AiChatClient {

    /**
     * 以流式方式请求模型生成回复。
     * @param history 会话上下文消息，按创建时间升序传入。
     * @param deepThinking 是否开启深度思考。
     * @param handler 模型流式回调处理器，用于接收元信息、增量、完成和错误。
     */
    void streamChat(List<ChatMessage> history, boolean deepThinking, StreamHandler handler);

    /**
     * 以可调用工具模式处理对话。
     * 默认回退普通流式接口，便于暂未适配工具调用的 provider 保持兼容。
     *
     * @param history 输入消息历史。
     * @param deepThinking 是否开启深度思考。
     * @param tools 当前模型可见工具 schema。
     * @param handler 支持工具调用事件的流处理器。
     */
    default void streamChatWithTools(
        List<ChatMessage> history,
        boolean deepThinking,
        List<ChatToolSpec> tools,
        ToolAwareStreamHandler handler
    ) {
        // 步骤 1：默认实现忽略工具 schema 并回退普通流式接口，保证未适配工具的 provider 仍可工作。
        streamChat(history, deepThinking, handler);
    }

    /**
     * 基于会话消息生成简短标题。
     * @param history 会话消息历史。
     * @return 标题文本。
     */
    default String generateTitle(List<ChatMessage> history) {
        // 步骤 1：标题默认取第一条用户消息，避免单独调用模型生成标题带来额外延迟。
        return history.stream()
            .filter(message -> message.getRole() == com.codingx.chat.domain.model.ChatMessageRole.USER)
            .findFirst()
            .map(ChatMessage::getContent)
            // 步骤 2：没有用户消息时使用统一默认标题，保证会话列表始终可展示。
            .orElse(ErrorMessageCatalog.CHAT_CONVERSATION_DEFAULT_TITLE);
    }

    /**
     * 普通模型流式回调契约。
     */
    interface StreamHandler {

        /**
         * 通知当前实际命中的 provider 与模型，便于消息持久化和运行态回放。
         * @param provider provider 名称。
         * @param model 模型名称。
         */
        default void onMetadata(String provider, String model) {
        }

        /**
         * 接收助手正文增量。
         * @param delta 助手回复正文增量。
         */
        void onDelta(String delta);

        /**
         * 推送思考增量，当前前端未消费时允许默认忽略。
         * @param delta 思考增量。
         */
        default void onThinkingDelta(String delta) {
        }

        /**
         * 通知当前模型流已正常完成。
         */
        void onComplete();

        /**
         * 通知当前模型流发生错误。
         * @param throwable provider 或路由层抛出的原始异常。
         */
        default void onError(Throwable throwable) {
        }
    }

    /**
     * 扩展普通流式处理器，接收模型请求执行的本地工具调用。
     */
    interface ToolAwareStreamHandler extends StreamHandler {

        /**
         * 接收模型工具调用请求。
         * @param toolCall 工具调用。
         */
        default void onToolCall(AiToolCall toolCall) {
        }
    }
}

