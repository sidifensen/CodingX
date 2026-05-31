package com.codingx.admin.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageFeedback;
import com.codingx.chat.domain.model.ChatMessageReference;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatMessageFeedbackRepository;
import com.codingx.chat.domain.repository.ChatMessageReferenceRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.interfaces.response.AdminChatMessageFeedbackDetailResponse;
import com.codingx.chat.interfaces.response.AdminChatMessageFeedbackListItemResponse;
import com.codingx.chat.interfaces.response.AdminChatMessageReferenceResponse;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供管理端反馈查询服务，统一聚合反馈、消息与来源证据。
 */
@Service
@RequiredArgsConstructor
public class AdminChatFeedbackService {

    /** 反馈仓储，用于分页查询、详情定位和反馈记录校验。 */
    private final ChatMessageFeedbackRepository chatMessageFeedbackRepository;
    /** 消息引用仓储，用于按反馈关联消息回溯搜索来源证据。 */
    private final ChatMessageReferenceRepository chatMessageReferenceRepository;
    /** 消息仓储，用于补齐反馈所属消息内容和角色信息。 */
    private final ChatMessageRepository chatMessageRepository;
    /** 会话仓储，用于补齐反馈所属会话标题和上下文信息。 */
    private final ChatConversationRepository chatConversationRepository;

    /**
     * 分页查询反馈列表，支持关键字与投票方向过滤。
     * @param current 当前页码（从 1 开始）。
     * @param size 每页条数。
     * @param keyword 查询关键字。
     * @param vote 投票值过滤。
     * @return 管理端反馈分页结果。
     */
    public PageResult<AdminChatMessageFeedbackListItemResponse> pageFeedback(int current, int size, String keyword, Integer vote) {
        PageResult<ChatMessageFeedback> page = chatMessageFeedbackRepository.pageQuery(current, size, keyword, vote);
        return PageResult.<AdminChatMessageFeedbackListItemResponse>builder()
            .records(page.records().stream().map(this::toListItem).toList())
            .total(page.total())
            .size(page.size())
            .current(page.current())
            .pages(page.pages())
            .build();
    }

    /**
     * 查询反馈详情并聚合消息内容、会话标题，供管理端定位上下文。
     * @param feedbackId 反馈主键。
     * @return 聚合详情。
     */
    public AdminChatMessageFeedbackDetailResponse getFeedbackDetail(Long feedbackId) {
        ChatMessageFeedback feedback = requireFeedback(feedbackId);
        ChatMessage message = chatMessageRepository.findById(feedback.getMessageId())
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.CHAT_FEEDBACK_MESSAGE_NOT_FOUND));
        ChatConversation conversation = chatConversationRepository.findById(feedback.getConversationId())
            .orElse(null);
        return AdminChatMessageFeedbackDetailResponse.builder()
            .id(feedback.getId())
            .messageId(feedback.getMessageId())
            .conversationId(feedback.getConversationId())
            .conversationTitle(conversation == null ? null : StrUtil.blankToDefault(conversation.getTitle(), null))
            .messageRole(message.getRole().name())
            .messageContent(message.getContent())
            .userId(feedback.getUserId())
            .vote(feedback.getVote())
            .reason(feedback.getReason())
            .comment(feedback.getComment())
            .createdAt(feedback.getCreatedAt())
            .updatedAt(feedback.getUpdatedAt())
            .build();
    }

    /**
     * 基于反馈记录回溯该消息的引用来源列表。
     * @param feedbackId 反馈主键。
     * @return 来源列表。
     */
    public List<AdminChatMessageReferenceResponse> listReferencesByFeedback(Long feedbackId) {
        ChatMessageFeedback feedback = requireFeedback(feedbackId);
        return chatMessageReferenceRepository.findByMessageId(feedback.getMessageId()).stream()
            .map(this::toReferenceResponse)
            .toList();
    }

    private ChatMessageFeedback requireFeedback(Long feedbackId) {
        return chatMessageFeedbackRepository.findById(feedbackId)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.CHAT_FEEDBACK_NOT_FOUND));
    }

    private AdminChatMessageFeedbackListItemResponse toListItem(ChatMessageFeedback feedback) {
        return AdminChatMessageFeedbackListItemResponse.builder()
            .id(feedback.getId())
            .messageId(feedback.getMessageId())
            .conversationId(feedback.getConversationId())
            .userId(feedback.getUserId())
            .vote(feedback.getVote())
            .reason(feedback.getReason())
            .comment(feedback.getComment())
            .createdAt(feedback.getCreatedAt())
            .updatedAt(feedback.getUpdatedAt())
            .build();
    }

    private AdminChatMessageReferenceResponse toReferenceResponse(ChatMessageReference reference) {
        return AdminChatMessageReferenceResponse.builder()
            .id(reference.getId())
            .runId(reference.getRunId())
            .messageId(reference.getMessageId())
            .conversationId(reference.getConversationId())
            .sourceType(reference.getSourceType())
            .title(reference.getTitle())
            .url(reference.getUrl())
            .siteName(reference.getSiteName())
            .snippet(reference.getSnippet())
            .rankNo(reference.getRankNo())
            .createdAt(reference.getCreatedAt())
            .build();
    }
}

