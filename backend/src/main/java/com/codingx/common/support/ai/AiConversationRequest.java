package com.codingx.common.support.ai;

import cn.hutool.core.collection.CollUtil;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.tool.application.service.ChatToolSpec;
import java.util.List;
import lombok.Builder;

/**
 * 统一模型层的对话请求对象，屏蔽上层业务与具体 provider 之间的入参差异。
 */
@Builder(toBuilder = true)
public record AiConversationRequest(
    List<ChatMessage> messages,
    List<ChatAttachment> attachments,
    List<ChatToolSpec> tools,
    String preferredModel,
    boolean stream,
    boolean thinkingEnabled
) {

    /**
     * 校验请求至少带有一条消息，避免 provider 层处理无效输入。
     */
    public AiConversationRequest {
        if (CollUtil.isEmpty(messages)) {
            throw new IllegalArgumentException(ErrorMessageCatalog.AI_CONVERSATION_MESSAGES_REQUIRED);
        }
    }
}

