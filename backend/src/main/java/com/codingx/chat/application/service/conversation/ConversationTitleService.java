package com.codingx.chat.application.service;

import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.port.AiChatClient;
import com.codingx.common.error.ErrorMessageCatalog;
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
     * AI 聊天客户端，用于在会话仍为默认标题时根据消息内容生成展示标题。
     */
    private final AiChatClient aiChatClient;

    /**
     * 根据当前会话消息生成标题，供左侧会话列表展示。
     * @param conversation 会话对象。
     * @param messages 会话消息。
     * @return 标题文本。
     */
    public String generateTitle(ChatConversation conversation, List<ChatMessage> messages) {
        if (!ErrorMessageCatalog.CHAT_CONVERSATION_DEFAULT_TITLE.equals(conversation.getTitle())) {
            // 步骤 1：用户或历史流程已有非默认标题时直接复用，避免模型覆盖人工命名。
            return conversation.getTitle();
        }
        // 步骤 2：默认标题才调用模型客户端生成短标题，降低不必要的模型调用次数。
        return aiChatClient.generateTitle(messages);
    }
}

