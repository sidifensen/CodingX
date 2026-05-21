package com.codingx.chat.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.support.ai.AiConversationRequest;
import com.codingx.common.support.ai.AiModelDispatchService;
import com.codingx.common.support.ai.AiStreamHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责复用统一模型路由能力执行短文本 Prompt，并同步收集完整回答。
 */
@Service
@RequiredArgsConstructor
public class AiPromptExecutionService {

    private static final Long INTERNAL_CONVERSATION_ID = 0L;

    private final AiModelDispatchService aiModelDispatchService;

    /**
     * 使用 system prompt 与 user prompt 执行一次同步文本生成。
     * @param systemPrompt 系统提示词。
     * @param userPrompt 用户输入。
     * @return 完整模型输出文本。
     */
    public String complete(String systemPrompt, String userPrompt) {
        StringBuilder builder = new StringBuilder();
        final Throwable[] streamError = new Throwable[1];
        aiModelDispatchService.streamChat(
            AiConversationRequest.builder()
                .messages(List.of(
                    internalMessage(ChatMessageRole.SYSTEM, StrUtil.blankToDefault(systemPrompt, "")),
                    internalMessage(ChatMessageRole.USER, StrUtil.blankToDefault(userPrompt, ""))
                ))
                .stream(true)
                .thinkingEnabled(false)
                .build(),
            new AiStreamHandler() {
                @Override
                public void onContentDelta(String delta) {
                    builder.append(delta);
                }

                @Override
                public void onError(Throwable throwable) {
                    streamError[0] = throwable;
                }
            }
        );
        if (streamError[0] != null) {
            throw new IllegalStateException(ErrorMessageCatalog.CHAT_PROMPT_EXECUTION_FAILED, streamError[0]);
        }
        return builder.toString();
    }

    /**
     * 构造内部 Prompt 调用使用的临时消息对象，不进入业务持久化链路。
     * @param role 消息角色。
     * @param content 消息内容。
     * @return 临时消息。
     */
    private ChatMessage internalMessage(ChatMessageRole role, String content) {
        return ChatMessage.create(
            IdUtil.getSnowflakeNextId(),
            INTERNAL_CONVERSATION_ID,
            role,
            content,
            ChatMessageStatus.COMPLETED,
            null,
            null,
            null
        );
    }
}

