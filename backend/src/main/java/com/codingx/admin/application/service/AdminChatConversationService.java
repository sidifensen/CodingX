package com.codingx.admin.application.service;

import cn.hutool.core.collection.CollUtil;
import com.codingx.chat.application.service.ChatCapabilityMentionSupport;
import com.codingx.chat.application.service.ChatAttachmentService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatGoal;
import com.codingx.chat.domain.model.ChatGoalStep;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatGoalRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.interfaces.response.AdminChatConversationDetailResponse;
import com.codingx.chat.interfaces.response.AdminChatConversationDetailResponse.AdminChatGoalEventResponse;
import com.codingx.chat.interfaces.response.AdminChatConversationDetailResponse.AdminChatGoalRecordResponse;
import com.codingx.chat.interfaces.response.AdminChatConversationDetailResponse.AdminChatGoalStepResponse;
import com.codingx.chat.interfaces.response.AdminChatConversationListItemResponse;
import com.codingx.chat.interfaces.response.ChatAttachmentResponse;
import com.codingx.chat.interfaces.response.ChatMessageResponse;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供管理端会话查询服务，统一聚合会话列表与详情数据。
 */
@Service
@RequiredArgsConstructor
public class AdminChatConversationService {

    /** 会话仓储，用于管理端分页、详情和会话归属状态查询。 */
    private final ChatConversationRepository chatConversationRepository;
    /** 消息仓储，用于回放会话消息并组装管理端详情页。 */
    private final ChatMessageRepository chatMessageRepository;
    /** 附件服务，用于把消息附件转换为管理端可展示结构。 */
    private final ChatAttachmentService chatAttachmentService;
    /** 目标仓储，用于管理端会话详情只读聚合目标主表、步骤和事件。 */
    private final ChatGoalRepository chatGoalRepository;

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
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.CHAT_CONVERSATION_NOT_FOUND));
        List<ChatMessageResponse> messages = chatMessageRepository.findByConversationId(conversationId).stream()
            .map(this::toMessageResponse)
            .toList();
        List<AdminChatGoalRecordResponse> goals = buildGoalRecords(conversationId);
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
            .goals(goals)
            .build();
    }

    /**
     * 聚合会话目标三表数据，供管理端在单个会话详情内完成目标模式排障。
     * @param conversationId 会话标识。
     * @return 目标记录响应列表，没有目标时返回空列表。
     */
    private List<AdminChatGoalRecordResponse> buildGoalRecords(Long conversationId) {
        // 步骤 1：管理端需要查看所有历史目标，不能只读取 active goal。
        List<ChatGoal> goals = CollUtil.emptyIfNull(chatGoalRepository.findAllByConversationId(conversationId));
        if (goals.isEmpty()) {
            return List.of();
        }
        List<Long> goalIds = goals.stream().map(ChatGoal::getId).toList();
        // 步骤 2：批量读取步骤和事件后按目标 ID 分组，避免详情页为每个目标重复查询。
        Map<Long, List<ChatGoalStep>> stepsByGoalId = CollUtil.emptyIfNull(chatGoalRepository.findStepsByGoalIds(goalIds)).stream()
            .collect(Collectors.groupingBy(ChatGoalStep::getGoalId));
        Map<Long, List<ChatGoalRepository.ChatGoalEventRecord>> eventsByGoalId = CollUtil.emptyIfNull(
                chatGoalRepository.findEventsByConversationId(conversationId)
            ).stream()
            .collect(Collectors.groupingBy(ChatGoalRepository.ChatGoalEventRecord::goalId));
        // 步骤 3：每个目标单独挂载自己的步骤和事件，Long ID 统一字符串化给前端。
        return goals.stream()
            .map(goal -> toGoalRecordResponse(
                goal,
                stepsByGoalId.getOrDefault(goal.getId(), List.of()),
                eventsByGoalId.getOrDefault(goal.getId(), List.of())
            ))
            .toList();
    }

    /**
     * 将目标主表和下属步骤、事件转换为管理端只读响应。
     * @param goal 目标主表领域对象。
     * @param steps 目标步骤列表。
     * @param events 目标事件列表。
     * @return 管理端目标记录响应。
     */
    private AdminChatGoalRecordResponse toGoalRecordResponse(
        ChatGoal goal,
        List<ChatGoalStep> steps,
        List<ChatGoalRepository.ChatGoalEventRecord> events
    ) {
        return new AdminChatGoalRecordResponse(
            stringifyId(goal.getId()),
            stringifyId(goal.getConversationId()),
            stringifyId(goal.getUserId()),
            goal.getGoalKey(),
            goal.getTitle(),
            goal.getDescription(),
            goal.getStatus() == null ? null : goal.getStatus().name(),
            goal.getProgressSummary(),
            stringifyId(goal.getCreatedRunId()),
            stringifyId(goal.getUpdatedRunId()),
            goal.getCreatedAt(),
            goal.getUpdatedAt(),
            goal.getCompletedAt(),
            CollUtil.emptyIfNull(steps).stream().map(this::toGoalStepResponse).toList(),
            CollUtil.emptyIfNull(events).stream().map(this::toGoalEventResponse).toList()
        );
    }

    /**
     * 将目标步骤转换为管理端展示结构。
     * @param step 目标步骤领域对象。
     * @return 目标步骤响应。
     */
    private AdminChatGoalStepResponse toGoalStepResponse(ChatGoalStep step) {
        return new AdminChatGoalStepResponse(
            stringifyId(step.getId()),
            stringifyId(step.getGoalId()),
            step.getStepKey(),
            step.getTitle(),
            step.getStatus() == null ? null : step.getStatus().name(),
            step.getSortNo(),
            step.getDetail(),
            step.getStartedAt(),
            step.getCompletedAt(),
            step.getUpdatedAt()
        );
    }

    /**
     * 将目标事件转换为管理端展示结构，payloadJson 保留原文不解析。
     * @param event 目标事件记录。
     * @return 目标事件响应。
     */
    private AdminChatGoalEventResponse toGoalEventResponse(ChatGoalRepository.ChatGoalEventRecord event) {
        return new AdminChatGoalEventResponse(
            stringifyId(event.id()),
            stringifyId(event.goalId()),
            stringifyId(event.conversationId()),
            stringifyId(event.runId()),
            event.eventType(),
            event.payloadJson(),
            event.createdAt()
        );
    }

    /**
     * 将 Long ID 统一转换为字符串，避免管理端 JavaScript 读取 Snowflake ID 时发生精度丢失。
     * @param id Long ID，可为空。
     * @return 字符串 ID，空值返回 null。
     */
    private String stringifyId(Long id) {
        return id == null ? null : String.valueOf(id);
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
        // 管理端详情与用户端一致从消息正文解析技能，便于直接核对 content 持久化结果。
        List<String> skillCodes = ChatCapabilityMentionSupport.parseSkillCodes(message.getContent());
        return new ChatMessageResponse(
            message.getId(),
            message.getConversationId(),
            message.getRunId(),
            message.getRole(),
            message.getContent(),
            message.getThinkingContent(),
            message.getThinkingDuration(),
            message.getStatus(),
            message.getProvider(),
            message.getModel(),
            message.getErrorMessage(),
            message.getDeleted(),
            message.getCreatedAt(),
            message.getUpdatedAt(),
            attachments,
            skillCodes,
            null
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
