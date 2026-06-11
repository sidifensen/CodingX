package com.codingx.chat.infrastructure.ai;

import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.port.AiChatClient;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.application.service.ChatAttachmentService;
import com.codingx.common.support.ai.AiConversationRequest;
import com.codingx.common.support.ai.AiModelDispatchService;
import com.codingx.common.support.ai.AiStreamHandler;
import com.codingx.common.support.ai.AiToolCall;
import com.codingx.common.support.ai.AiToolCallDelta;
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

    /** 目标模式首包后模型流空闲超时窗口：连续静默超过该时长才判定挂起；流持续产出工具参数时不会被掐断。 */
    private static final long PLAN_MODE_STREAM_COMPLETION_TIMEOUT_MS = 90_000L;

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
            .streamCompletionTimeoutOverrideMs(resolveStreamCompletionTimeoutOverrideMs(history))
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
            public void onToolCallDelta(AiToolCallDelta toolCallDelta) {
                handler.onToolCallDelta(toolCallDelta);
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

    /**
     * 目标模式使用独立的流式空闲超时：连续静默 90 秒才判定 provider 挂起，防止目标长期停留 ACTIVE 且 run 长时间 RUNNING；
     * 健康长流（如大型 HTML 的 write 工具参数）只要持续产出事件就不会被该窗口掐断。
     * @param history 本次送入模型的历史，应用层会在目标模式下追加系统约束。
     * @return 请求级流式空闲超时；空值表示沿用全局路由配置。
     */
    private Long resolveStreamCompletionTimeoutOverrideMs(List<ChatMessage> history) {
        if (history == null) {
            return null;
        }
        boolean planMode = history.stream()
            .filter(message -> message.getRole() == ChatMessageRole.SYSTEM)
            .map(ChatMessage::getContent)
            .anyMatch(content -> content != null && content.contains("# 规划/目标模式"));
        return planMode ? PLAN_MODE_STREAM_COMPLETION_TIMEOUT_MS : null;
    }
}


