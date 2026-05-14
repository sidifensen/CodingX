package com.codingx.chat.application.service;

import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.service.AiChatClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责为新建或默认标题的会话生成可读标题。
 */
@Service
@RequiredArgsConstructor
public class ConversationTitleService {

    /**
     * 模型客户端依赖。
     */
    private final AiChatClient aiChatClient;

    /**
     * 根据当前会话消息生成标题，供左侧会话列表展示。
     * @param conversation 会话对象。
     * @param messages 会话消息。
     * @return 标题文本。
     */
    public String generateTitle(ChatConversation conversation, List<ChatMessage> messages) {
        if (!"New Conversation".equals(conversation.getTitle())) {
            return conversation.getTitle();
        }
        return aiChatClient.generateTitle(messages);
    }
}
