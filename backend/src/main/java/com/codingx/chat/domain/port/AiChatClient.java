package com.codingx.chat.domain.port;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.support.ai.AiToolCall;
import com.codingx.tool.application.service.ChatToolSpec;
import java.util.List;

/**
 * 定义 AiChatClient 的领域服务契约。
 */
public interface AiChatClient {

    /**
     * 以流式方式处理 streamChat 的结果。
     * @param history 输入参数。
     * @param deepThinking 是否开启深度思考。
     * @param handler 输入参数。
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
        streamChat(history, deepThinking, handler);
    }

    /**
     * 基于会话消息生成简短标题。
     * @param history 会话消息历史。
     * @return 标题文本。
     */
    default String generateTitle(List<ChatMessage> history) {
        return history.stream()
            .filter(message -> message.getRole() == com.codingx.chat.domain.model.ChatMessageRole.USER)
            .findFirst()
            .map(ChatMessage::getContent)
            .orElse(ErrorMessageCatalog.CHAT_CONVERSATION_DEFAULT_TITLE);
    }

    /**
     * 定义 StreamHandler 的领域服务契约。
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
         * 执行 onDelta 定义的处理逻辑。
         * @param delta 输入参数。
         */
        void onDelta(String delta);

        /**
         * 推送思考增量，当前前端未消费时允许默认忽略。
         * @param delta 思考增量。
         */
        default void onThinkingDelta(String delta) {
        }

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

