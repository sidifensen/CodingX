package com.codingx.chat.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationSummary;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.repository.ChatConversationSummaryRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final RuntimeSettingService runtimeSettingService;

    /**
     * 注入摘要阈值判断器与摘要仓储。
     * @param conversationDigestService 摘要阈值判断器。
     * @param chatConversationSummaryRepository 摘要仓储。
     */
    public ConversationSummaryService(
        ConversationDigestService conversationDigestService,
        ChatConversationSummaryRepository chatConversationSummaryRepository,
        RuntimeSettingService runtimeSettingService
    ) {
        this.conversationDigestService = conversationDigestService;
        this.chatConversationSummaryRepository = chatConversationSummaryRepository;
        this.runtimeSettingService = runtimeSettingService;
    }

    /**
     * 会话消息达到阈值时生成并保存摘要。
     * @param conversation 会话对象。
     * @param history 当前消息历史。
     * @return 新增或更新后的摘要。
     */
    public Optional<ChatConversationSummary> refreshSummaryIfNeeded(ChatConversation conversation, List<ChatMessage> history) {
        // 步骤 1：先检查摘要开关和历史阈值，未达到条件时不触碰摘要表。
        if (!runtimeSettingService.summaryEnabled()) {
            return Optional.empty();
        }
        if (!conversationDigestService.shouldSummarize(history)) {
            return Optional.empty();
        }
        int keepMessages = resolveKeepMessages();
        int summarizeCount = history.size() - keepMessages;
        if (summarizeCount <= 0) {
            return Optional.empty();
        }
        List<ChatMessage> summarizeCandidates = history.subList(0, summarizeCount);
        if (summarizeCandidates.isEmpty()) {
            return Optional.empty();
        }
        // 步骤 2：计算本轮摘要覆盖点；已有摘要已覆盖到该消息时直接跳过，避免重复压缩。
        Long cutoffMessageId = summarizeCandidates.getLast().getId();
        LocalDateTime now = LocalDateTime.now();
        Optional<ChatConversationSummary> existingOptional = chatConversationSummaryRepository.findLatestByConversationId(conversation.getId());
        if (existingOptional.isPresent() && existingOptional.get().getLastMessageId() != null
            && existingOptional.get().getLastMessageId() >= cutoffMessageId) {
            return Optional.empty();
        }
        List<ChatMessage> incrementalMessages = resolveIncrementalMessages(existingOptional.orElse(null), summarizeCandidates);
        if (incrementalMessages.isEmpty()) {
            return Optional.empty();
        }
        // 步骤 3：只把新增待压缩消息合并进既有摘要，摘要为空时不写入无意义记录。
        String summaryContent = buildSummaryContent(existingOptional.map(ChatConversationSummary::getContent).orElse(""), incrementalMessages);
        if (StrUtil.isBlank(summaryContent)) {
            return Optional.empty();
        }
        // 步骤 4：存在摘要则更新覆盖点，否则新建摘要记录并继承会话归属用户。
        ChatConversationSummary summary = existingOptional
            .map(existing -> existing.toBuilder()
                .content(summaryContent)
                .lastMessageId(cutoffMessageId)
                .updatedAt(now)
                .build())
            .orElseGet(() -> ChatConversationSummary.builder()
                .id(IdUtil.getSnowflakeNextId())
                .conversationId(conversation.getId())
                .userId(conversation.getCreatedBy())
                .lastMessageId(cutoffMessageId)
                .content(summaryContent)
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build());
        chatConversationSummaryRepository.save(summary);
        return Optional.of(summary);
    }

    /**
     * 将完整历史裁剪为「摘要 + 最近窗口原文」的入模上下文。
     * @param conversationId 会话标识。
     * @param history 当前完整历史。
     * @return 裁剪后的会话上下文。
     */
    public List<ChatMessage> buildModelHistory(Long conversationId, List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        if (!runtimeSettingService.summaryEnabled()) {
            return new ArrayList<>(history);
        }
        Optional<ChatConversationSummary> summaryOptional = chatConversationSummaryRepository.findLatestByConversationId(conversationId)
            .filter(summary -> StrUtil.isNotBlank(summary.getContent()));
        if (summaryOptional.isEmpty()) {
            return trimToRecentWindow(history);
        }
        ChatConversationSummary summary = summaryOptional.get();
        List<ChatMessage> recentMessages = resolveRecentMessages(history, summary.getLastMessageId());
        List<ChatMessage> context = new ArrayList<>();
        context.add(buildSummarySystemMessage(conversationId, summary.getContent()));
        context.addAll(recentMessages);
        return context;
    }

    /**
     * 使用新增历史与既有摘要构造可回放的压缩摘要文本。
     * @param existingSummary 既有摘要。
     * @param incrementalMessages 新增待压缩消息。
     * @return 摘要内容。
     */
    private String buildSummaryContent(String existingSummary, List<ChatMessage> incrementalMessages) {
        String appended = incrementalMessages.stream()
            .map(message -> message.getRole().name().toLowerCase() + ": " + StrUtil.blankToDefault(message.getContent(), ""))
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
        String merged = StrUtil.isBlank(existingSummary)
            ? appended
            : existingSummary.trim() + "\n" + appended;
        int maxCharacters = Math.max(500, runtimeSettingService.summaryMaxCharacters());
        if (merged.length() <= maxCharacters) {
            return merged;
        }
        // 摘要过长时保留尾部近况，确保后续对话延续最近上下文。
        return StrUtil.sub(merged, merged.length() - maxCharacters, merged.length());
    }

    /**
     * 计算本次需要新增压缩的消息集合，避免重复汇总已压缩片段。
     * @param existingSummary 已有摘要。
     * @param summarizeCandidates 可压缩消息候选。
     * @return 本次增量消息。
     */
    private List<ChatMessage> resolveIncrementalMessages(ChatConversationSummary existingSummary, List<ChatMessage> summarizeCandidates) {
        if (existingSummary == null || existingSummary.getLastMessageId() == null) {
            return summarizeCandidates;
        }
        return summarizeCandidates.stream()
            .filter(message -> message.getId() != null && message.getId() > existingSummary.getLastMessageId())
            .toList();
    }

    /**
     * 基于摘要覆盖点筛选最近原文，并在异常场景回退到固定窗口。
     * @param history 完整历史。
     * @param lastSummarizedMessageId 摘要覆盖到的最后消息。
     * @return 需要原文保留的消息。
     */
    private List<ChatMessage> resolveRecentMessages(List<ChatMessage> history, Long lastSummarizedMessageId) {
        List<ChatMessage> filtered = history;
        if (lastSummarizedMessageId != null) {
            filtered = history.stream()
                .filter(message -> message.getId() != null && message.getId() > lastSummarizedMessageId)
                .toList();
        }
        if (filtered.isEmpty()) {
            return trimToRecentWindow(history);
        }
        return trimToRecentWindow(filtered);
    }

    /**
     * 仅保留最近窗口消息，防止上下文无限增长。
     * @param messages 待裁剪消息。
     * @return 裁剪结果。
     */
    private List<ChatMessage> trimToRecentWindow(List<ChatMessage> messages) {
        int keepMessages = resolveKeepMessages();
        if (messages.size() <= keepMessages) {
            return new ArrayList<>(messages);
        }
        return new ArrayList<>(messages.subList(messages.size() - keepMessages, messages.size()));
    }

    /**
     * 将摘要包装为系统消息，明确告诉模型该内容仅用于承接历史语义。
     * @param conversationId 会话标识。
     * @param summaryContent 摘要内容。
     * @return 系统消息。
     */
    private ChatMessage buildSummarySystemMessage(Long conversationId, String summaryContent) {
        return ChatMessage.create(
            IdUtil.getSnowflakeNextId(),
            conversationId,
            ChatMessageRole.SYSTEM,
            "以下是历史会话摘要，请仅作为上下文参考，不要逐字复述：\n" + summaryContent.trim(),
            ChatMessageStatus.COMPLETED,
            null,
            null,
            null
        );
    }

    /**
     * 将保留窗口从“轮次”转换为“消息条数”。
     * @return 保留消息条数。
     */
    private int resolveKeepMessages() {
        return Math.max(2, Math.max(1, runtimeSettingService.historyKeepTurns()) * 2);
    }
}
