package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.chat.interfaces.response.AdminChatConversationDetailResponse;
import com.codingx.chat.interfaces.response.AdminChatConversationListItemResponse;
import com.codingx.chat.interfaces.response.ChatAttachmentResponse;
import com.codingx.chat.interfaces.response.ChatMessageResponse;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供管理端会话查询服务，统一聚合会话列表与详情数据。
 */
@Service
@RequiredArgsConstructor
public class AdminChatConversationService {

    private final ChatConversationRepository chatConversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatAttachmentService chatAttachmentService;
    private final ChatSkillRepository chatSkillRepository;

    /**
     * 分页查询会话列表，支持标题/ID 关键字过滤。
     * @param current 当前页码（从 1 开始）。
     * @param size 每页条数。
     * @param keyword 关键字。
     * @return 会话分页结果。
     */
    public PageResult<AdminChatConversationListItemResponse> pageConversations(int current, int size, String keyword) {
        int normalizedCurrent = Math.max(1, current);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        List<ChatConversation> allRecords = chatConversationRepository.findAll(keyword);
        long total = allRecords.size();
        long pages = total == 0 ? 1 : (total + normalizedSize - 1L) / normalizedSize;
        int fromIndex = Math.min((normalizedCurrent - 1) * normalizedSize, allRecords.size());
        int toIndex = Math.min(fromIndex + normalizedSize, allRecords.size());
        List<AdminChatConversationListItemResponse> records = allRecords.subList(fromIndex, toIndex).stream()
            .map(this::toListItem)
            .toList();
        return PageResult.<AdminChatConversationListItemResponse>builder()
            .records(records)
            .total(total)
            .size((long) normalizedSize)
            .current((long) normalizedCurrent)
            .pages(pages)
            .build();
    }

    /**
     * 查询会话详情并聚合消息列表。
     * @param conversationId 会话标识。
     * @return 会话详情。
     */
    public AdminChatConversationDetailResponse getConversationDetail(Long conversationId) {
        ChatConversation conversation = chatConversationRepository.findById(conversationId)
            .orElseThrow(() -> new NotFoundException("会话不存在"));
        List<ChatMessageResponse> messages = chatMessageRepository.findByConversationId(conversationId).stream()
            .map(this::toMessageResponse)
            .toList();
        return AdminChatConversationDetailResponse.builder()
            .id(conversation.getId())
            .title(conversation.getTitle())
            .createdBy(conversation.getCreatedBy())
            .status(conversation.getStatus())
            .statusLabel(resolveStatusLabel(conversation.getStatus()))
            .lastMessageAt(conversation.getLastMessageAt())
            .lastRunId(conversation.getLastRunId())
            .createdAt(conversation.getCreatedAt())
            .updatedAt(conversation.getUpdatedAt())
            .messages(messages)
            .build();
    }

    /**
     * 将会话领域对象转换为管理端列表响应。
     * @param conversation 会话领域对象。
     * @return 列表项响应。
     */
    private AdminChatConversationListItemResponse toListItem(ChatConversation conversation) {
        return AdminChatConversationListItemResponse.builder()
            .id(conversation.getId())
            .title(conversation.getTitle())
            .createdBy(conversation.getCreatedBy())
            .status(conversation.getStatus())
            .statusLabel(resolveStatusLabel(conversation.getStatus()))
            .lastMessageAt(conversation.getLastMessageAt())
            .lastRunId(conversation.getLastRunId())
            .createdAt(conversation.getCreatedAt())
            .updatedAt(conversation.getUpdatedAt())
            .build();
    }

    /**
     * 将会话消息转换为管理端消息响应，并补齐附件列表。
     * @param message 会话消息。
     * @return 消息响应。
     */
    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        List<ChatAttachmentResponse> attachments = chatAttachmentService.listByMessageId(message.getId()).stream()
            .map(attachment -> new ChatAttachmentResponse(
                attachment.getId(),
                attachment.getConversationId(),
                attachment.getMessageId(),
                attachment.getAttachmentType(),
                attachment.getFileName(),
                attachment.getFileExt(),
                attachment.getMimeType(),
                attachment.getFileSize(),
                attachment.getPreviewUrl(),
                attachment.getContentSummary(),
                attachment.getStatus(),
                attachment.getCreatedAt()
            ))
            .toList();
        // 管理端详情需与用户端一致返回消息技能绑定，便于排查“技能显示丢失”问题。
        List<String> skillCodes = message.getRunId() == null
            ? List.of()
            : chatSkillRepository.findByTaskId(message.getRunId()).stream()
                .map(ChatSkill::getSkillCode)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        return new ChatMessageResponse(
            message.getId(),
            message.getConversationId(),
            message.getRole(),
            message.getContent(),
            message.getThinkingContent(),
            message.getThinkingDuration(),
            message.getStatus(),
            message.getProvider(),
            message.getModel(),
            message.getErrorMessage(),
            message.getCreatedAt(),
            attachments,
            skillCodes
        );
    }

    /**
     * 生成会话状态中文文案，统一前端展示语义。
     * @param status 会话状态枚举。
     * @return 状态文案。
     */
    private String resolveStatusLabel(ChatConversationStatus status) {
        if (status == ChatConversationStatus.ACTIVE) {
            return "活跃";
        }
        if (status == ChatConversationStatus.ARCHIVED) {
            return "已归档";
        }
        return "未知";
    }
}
