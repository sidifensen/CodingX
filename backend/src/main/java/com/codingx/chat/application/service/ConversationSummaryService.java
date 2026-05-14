package com.codingx.chat.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationSummary;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.repository.ChatConversationSummaryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 负责在满足阈值时生成并持久化会话摘要。
 */
@Service
public class ConversationSummaryService {

    private final ConversationDigestService conversationDigestService;
    private final ChatConversationSummaryRepository chatConversationSummaryRepository;

    /**
     * 注入摘要阈值判断器与摘要仓储。
     * @param conversationDigestService 摘要阈值判断器。
     * @param chatConversationSummaryRepository 摘要仓储。
     */
    public ConversationSummaryService(
        ConversationDigestService conversationDigestService,
        ChatConversationSummaryRepository chatConversationSummaryRepository
    ) {
        this.conversationDigestService = conversationDigestService;
        this.chatConversationSummaryRepository = chatConversationSummaryRepository;
    }

    /**
     * 会话消息达到阈值时生成并保存摘要。
     * @param conversation 会话对象。
     * @param history 当前消息历史。
     * @return 新增或更新后的摘要。
     */
    public Optional<ChatConversationSummary> refreshSummaryIfNeeded(ChatConversation conversation, List<ChatMessage> history) {
        if (!conversationDigestService.shouldSummarize(history)) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        String summaryContent = buildSummaryContent(history);
        Long lastMessageId = history.getLast().getId();
        ChatConversationSummary summary = chatConversationSummaryRepository.findLatestByConversationId(conversation.getId())
            .map(existing -> existing.toBuilder()
                .content(summaryContent)
                .lastMessageId(lastMessageId)
                .updatedAt(now)
                .build())
            .orElseGet(() -> ChatConversationSummary.builder()
                .id(IdUtil.getSnowflakeNextId())
                .conversationId(conversation.getId())
                .userId(conversation.getCreatedBy())
                .lastMessageId(lastMessageId)
                .content(summaryContent)
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build());
        chatConversationSummaryRepository.save(summary);
        return Optional.of(summary);
    }

    /**
     * 使用最近几条消息构造可回放的压缩摘要文本。
     * @param history 当前消息历史。
     * @return 摘要内容。
     */
    private String buildSummaryContent(List<ChatMessage> history) {
        return history.stream()
            .skip(Math.max(0, history.size() - 4))
            .map(message -> message.getRole().name().toLowerCase() + ": " + StrUtil.blankToDefault(message.getContent(), ""))
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
    }
}
