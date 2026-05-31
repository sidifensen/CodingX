package com.codingx.chat.infrastructure.ai;

import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.port.AiChatClient;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.application.service.ChatAttachmentService;
import com.codingx.common.support.ai.AiConversationRequest;
import com.codingx.common.support.ai.AiModelDispatchService;
import com.codingx.common.support.ai.AiStreamHandler;
import com.codingx.common.support.ai.AiToolCall;
import com.codingx.tool.application.service.ChatToolSpec;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 通过新的模型路由层适配旧的聊天领域接口，避免上层应用服务一次性大改。
 */
@Component
@Primary
public class RoutingAiChatClient implements AiChatClient {

    /** 模型调度服务，用于把旧领域接口请求路由到当前可用 provider/model。 */
    private final AiModelDispatchService aiModelDispatchService;
    /** 附件服务，用于根据历史消息解析模型请求中的附件内容。 */
    private final ChatAttachmentService chatAttachmentService;

    /**
     * 注入模型路由服务，统一承接所有流式对话请求。
     * @param aiModelDispatchService 模型路由服务。
     */
    public RoutingAiChatClient(
        AiModelDispatchService aiModelDispatchService,
        ChatAttachmentService chatAttachmentService
    ) {
        this.aiModelDispatchService = aiModelDispatchService;
        this.chatAttachmentService = chatAttachmentService;
    }

    @Override
    public void streamChat(List<ChatMessage> history, boolean deepThinking, StreamHandler handler) {
        streamChatWithTools(history, deepThinking, List.of(), adaptToolAwareHandler(handler));
    }

    @Override
    public void streamChatWithTools(
        List<ChatMessage> history,
        boolean deepThinking,
        List<ChatToolSpec> tools,
        ToolAwareStreamHandler handler
    ) {
        AiConversationRequest request = AiConversationRequest.builder()
            .messages(history)
            .attachments(resolveAttachments(history))
            .tools(tools == null ? List.of() : tools)
            .stream(true)
            .thinkingEnabled(deepThinking)
            .build();
        aiModelDispatchService.streamChat(request, new AiStreamHandler() {
            @Override
            public void onMetadata(String provider, String model) {
                handler.onMetadata(provider, model);
            }

            @Override
            public void onThinkingDelta(String delta) {
                handler.onThinkingDelta(delta);
            }

            @Override
            public void onContentDelta(String delta) {
                handler.onDelta(delta);
            }

            @Override
            public void onToolCall(AiToolCall toolCall) {
                handler.onToolCall(toolCall);
            }

            @Override
            public void onComplete() {
                handler.onComplete();
            }

            @Override
            public void onError(Throwable throwable) {
                handler.onError(throwable);
            }
        });
    }

    /**
     * 将旧式 StreamHandler 包装成工具感知处理器，旧调用方会自然忽略工具事件。
     * @param handler 旧式处理器。
     * @return 工具感知处理器。
     */
    private ToolAwareStreamHandler adaptToolAwareHandler(StreamHandler handler) {
        return new ToolAwareStreamHandler() {
            @Override
            public void onMetadata(String provider, String model) {
                handler.onMetadata(provider, model);
            }

            @Override
            public void onDelta(String delta) {
                handler.onDelta(delta);
            }

            @Override
            public void onThinkingDelta(String delta) {
                handler.onThinkingDelta(delta);
            }

            @Override
            public void onComplete() {
                handler.onComplete();
            }

            @Override
            public void onError(Throwable throwable) {
                handler.onError(throwable);
            }
        };
    }

    /**
     * 收集历史消息中已绑定的附件，用于模型路由判断视觉能力并构造多模态请求体。
     * @param history 会话历史消息。
     * @return 附件列表。
     */
    private List<ChatAttachment> resolveAttachments(List<ChatMessage> history) {
        return history.stream()
            .flatMap(message -> chatAttachmentService.listByMessageId(message.getId()).stream())
            .toList();
    }
}


