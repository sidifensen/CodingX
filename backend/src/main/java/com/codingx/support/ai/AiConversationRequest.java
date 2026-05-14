package com.codingx.support.ai;

import cn.hutool.core.collection.CollUtil;
import com.codingx.chat.domain.model.ChatMessage;
import java.util.List;
import lombok.Builder;

/**
 * 统一模型层的对话请求对象，屏蔽上层业务与具体 provider 之间的入参差异。
 */
@Builder(toBuilder = true)
public record AiConversationRequest(
    List<ChatMessage> messages,
    String preferredModel,
    boolean stream,
    boolean thinkingEnabled
) {

    /**
     * 校验请求至少带有一条消息，避免 provider 层处理无效输入。
     */
    public AiConversationRequest {
        if (CollUtil.isEmpty(messages)) {
            throw new IllegalArgumentException("AI conversation messages are required");
        }
    }
}
