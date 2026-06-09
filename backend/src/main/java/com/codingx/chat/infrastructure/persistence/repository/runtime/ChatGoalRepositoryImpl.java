package com.codingx.chat.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codingx.chat.domain.model.ChatGoal;
import com.codingx.chat.domain.model.ChatGoalStatus;
import com.codingx.chat.domain.model.ChatGoalStep;
import com.codingx.chat.domain.model.ChatGoalStepStatus;
import com.codingx.chat.domain.repository.ChatGoalRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatGoalDO;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatGoalEventDO;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatGoalStepDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatGoalEventMapper;
import com.codingx.chat.infrastructure.persistence.mapper.ChatGoalMapper;
import com.codingx.chat.infrastructure.persistence.mapper.ChatGoalStepMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现聊天目标仓储的 MyBatis 持久化逻辑，负责目标、步骤和事件三张表的转换与读写。
 */
@Repository
@RequiredArgsConstructor
public class ChatGoalRepositoryImpl implements ChatGoalRepository {

    /** 目标主表 Mapper，用于读写 chat_goal。 */
    private final ChatGoalMapper chatGoalMapper;
    /** 目标步骤 Mapper，用于读写 chat_goal_step。 */
    private final ChatGoalStepMapper chatGoalStepMapper;
    /** 目标事件 Mapper，用于追加 chat_goal_event。 */
    private final ChatGoalEventMapper chatGoalEventMapper;

    /**
     * 查询当前会话的 ACTIVE 目标，按更新时间倒序兜底处理历史重复数据。
     */
    @Override
    public Optional<ChatGoal> findActiveByConversationIdAndUserId(Long conversationId, Long userId) {
        return chatGoalMapper.selectList(new LambdaQueryWrapper<ChatGoalDO>()
                .eq(ChatGoalDO::getConversationId, conversationId)
                .eq(ChatGoalDO::getUserId, userId)
                .eq(ChatGoalDO::getStatus, ChatGoalStatus.ACTIVE.name())
                .eq(ChatGoalDO::getDeleted, 0)
                .orderByDesc(ChatGoalDO::getUpdatedAt)
                .orderByDesc(ChatGoalDO::getId))
            .stream()
            .findFirst()
            .map(this::toDomain);
    }

    /**
     * 按目标 ID、会话和用户读取目标，避免工具调用跨会话命中同名目标。
     */
    @Override
    public Optional<ChatGoal> findByIdAndConversationIdAndUserId(Long goalId, Long conversationId, Long userId) {
        return Optional.ofNullable(chatGoalMapper.selectOne(new LambdaQueryWrapper<ChatGoalDO>()
                .eq(ChatGoalDO::getId, goalId)
                .eq(ChatGoalDO::getConversationId, conversationId)
                .eq(ChatGoalDO::getUserId, userId)
                .eq(ChatGoalDO::getDeleted, 0)
                .last("LIMIT 1")))
            .map(this::toDomain);
    }

    /**
     * 按稳定键读取目标，同名 goalKey 只在当前会话和当前用户范围内有效。
     */
    @Override
    public Optional<ChatGoal> findByGoalKeyAndConversationIdAndUserId(String goalKey, Long conversationId, Long userId) {
        return Optional.ofNullable(chatGoalMapper.selectOne(new LambdaQueryWrapper<ChatGoalDO>()
                .eq(ChatGoalDO::getGoalKey, goalKey)
                .eq(ChatGoalDO::getConversationId, conversationId)
                .eq(ChatGoalDO::getUserId, userId)
                .eq(ChatGoalDO::getDeleted, 0)
                .orderByDesc(ChatGoalDO::getUpdatedAt)
                .orderByDesc(ChatGoalDO::getId)
                .last("LIMIT 1")))
            .map(this::toDomain);
    }

    /**
     * 管理端读取会话下全部未删除目标，包含 ACTIVE 和终态目标，便于排查完整目标历史。
     */
    @Override
    public List<ChatGoal> findAllByConversationId(Long conversationId) {
        return chatGoalMapper.selectList(new LambdaQueryWrapper<ChatGoalDO>()
                .eq(ChatGoalDO::getConversationId, conversationId)
                .eq(ChatGoalDO::getDeleted, 0)
                .orderByDesc(ChatGoalDO::getUpdatedAt)
                .orderByDesc(ChatGoalDO::getId))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    /**
     * 保存目标主表，已存在目标按主键更新，新增目标插入。
     */
    @Override
    public void saveGoal(ChatGoal goal) {
        ChatGoalDO dataObject = toDataObject(goal);
        if (chatGoalMapper.selectById(goal.getId()) == null) {
            chatGoalMapper.insert(dataObject);
            return;
        }
        chatGoalMapper.updateById(dataObject);
    }

    /**
     * 查询目标的未删除步骤快照，供服务组装 active goal 响应。
     */
    @Override
    public List<ChatGoalStep> findStepsByGoalId(Long goalId) {
        return chatGoalStepMapper.selectList(new LambdaQueryWrapper<ChatGoalStepDO>()
                .eq(ChatGoalStepDO::getGoalId, goalId)
                .eq(ChatGoalStepDO::getDeleted, 0)
                .orderByAsc(ChatGoalStepDO::getSortNo)
                .orderByAsc(ChatGoalStepDO::getId))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    /**
     * 批量查询目标步骤快照，供管理端会话详情一次性聚合目标明细。
     */
    @Override
    public List<ChatGoalStep> findStepsByGoalIds(List<Long> goalIds) {
        if (goalIds == null || goalIds.isEmpty()) {
            return List.of();
        }
        return chatGoalStepMapper.selectList(new LambdaQueryWrapper<ChatGoalStepDO>()
                .in(ChatGoalStepDO::getGoalId, goalIds)
                .eq(ChatGoalStepDO::getDeleted, 0)
                .orderByAsc(ChatGoalStepDO::getGoalId)
                .orderByAsc(ChatGoalStepDO::getSortNo)
                .orderByAsc(ChatGoalStepDO::getId))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    /**
     * 替换目标步骤快照，先逻辑删除旧步骤，再插入本次模型提交的新步骤。
     */
    @Override
    public void replaceSteps(Long goalId, List<ChatGoalStep> steps, LocalDateTime now) {
        chatGoalStepMapper.update(null, new LambdaUpdateWrapper<ChatGoalStepDO>()
            .eq(ChatGoalStepDO::getGoalId, goalId)
            .eq(ChatGoalStepDO::getDeleted, 0)
            .set(ChatGoalStepDO::getDeleted, 1)
            .set(ChatGoalStepDO::getUpdatedAt, now));
        for (ChatGoalStep step : steps) {
            chatGoalStepMapper.insert(toDataObject(step));
        }
    }

    /**
     * 追加目标事件流水，事件不更新不删除，作为目标工具操作审计。
     */
    @Override
    public void appendEvent(ChatGoalEventRecord eventRecord) {
        ChatGoalEventDO dataObject = new ChatGoalEventDO();
        dataObject.setId(eventRecord.id());
        dataObject.setGoalId(eventRecord.goalId());
        dataObject.setConversationId(eventRecord.conversationId());
        dataObject.setRunId(eventRecord.runId());
        dataObject.setEventType(eventRecord.eventType());
        dataObject.setPayloadJson(eventRecord.payloadJson());
        dataObject.setCreatedAt(eventRecord.createdAt());
        chatGoalEventMapper.insert(dataObject);
    }

    /**
     * 查询当前会话目标事件流水，事件表无逻辑删除字段，按写入顺序展示原始审计记录。
     */
    @Override
    public List<ChatGoalEventRecord> findEventsByConversationId(Long conversationId) {
        return chatGoalEventMapper.selectList(new LambdaQueryWrapper<ChatGoalEventDO>()
                .eq(ChatGoalEventDO::getConversationId, conversationId)
                .orderByAsc(ChatGoalEventDO::getCreatedAt)
                .orderByAsc(ChatGoalEventDO::getId))
            .stream()
            .map(this::toEventRecord)
            .toList();
    }

    /**
     * 将目标领域对象转换为数据库对象。
     */
    private ChatGoalDO toDataObject(ChatGoal goal) {
        ChatGoalDO dataObject = new ChatGoalDO();
        dataObject.setId(goal.getId());
        dataObject.setConversationId(goal.getConversationId());
        dataObject.setUserId(goal.getUserId());
        dataObject.setGoalKey(goal.getGoalKey());
        dataObject.setTitle(goal.getTitle());
        dataObject.setDescription(goal.getDescription());
        dataObject.setStatus(goal.getStatus() == null ? ChatGoalStatus.ACTIVE.name() : goal.getStatus().name());
        dataObject.setProgressSummary(goal.getProgressSummary());
        dataObject.setCreatedRunId(goal.getCreatedRunId());
        dataObject.setUpdatedRunId(goal.getUpdatedRunId());
        dataObject.setCreatedAt(goal.getCreatedAt());
        dataObject.setUpdatedAt(goal.getUpdatedAt());
        dataObject.setCompletedAt(goal.getCompletedAt());
        dataObject.setDeleted(goal.getDeleted() == null ? 0 : goal.getDeleted());
        return dataObject;
    }

    /**
     * 将目标数据库对象转换为领域对象。
     */
    private ChatGoal toDomain(ChatGoalDO dataObject) {
        return ChatGoal.builder()
            .id(dataObject.getId())
            .conversationId(dataObject.getConversationId())
            .userId(dataObject.getUserId())
            .goalKey(dataObject.getGoalKey())
            .title(dataObject.getTitle())
            .description(dataObject.getDescription())
            .status(ChatGoalStatus.normalize(dataObject.getStatus(), ChatGoalStatus.ACTIVE))
            .progressSummary(dataObject.getProgressSummary())
            .createdRunId(dataObject.getCreatedRunId())
            .updatedRunId(dataObject.getUpdatedRunId())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .completedAt(dataObject.getCompletedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }

    /**
     * 将步骤领域对象转换为数据库对象。
     */
    private ChatGoalStepDO toDataObject(ChatGoalStep step) {
        ChatGoalStepDO dataObject = new ChatGoalStepDO();
        dataObject.setId(step.getId());
        dataObject.setGoalId(step.getGoalId());
        dataObject.setStepKey(step.getStepKey());
        dataObject.setTitle(step.getTitle());
        dataObject.setStatus(step.getStatus() == null ? ChatGoalStepStatus.PENDING.name() : step.getStatus().name());
        dataObject.setSortNo(step.getSortNo());
        dataObject.setDetail(step.getDetail());
        dataObject.setStartedAt(step.getStartedAt());
        dataObject.setCompletedAt(step.getCompletedAt());
        dataObject.setUpdatedAt(step.getUpdatedAt());
        dataObject.setDeleted(step.getDeleted() == null ? 0 : step.getDeleted());
        return dataObject;
    }

    /**
     * 将步骤数据库对象转换为领域对象。
     */
    private ChatGoalStep toDomain(ChatGoalStepDO dataObject) {
        return ChatGoalStep.builder()
            .id(dataObject.getId())
            .goalId(dataObject.getGoalId())
            .stepKey(dataObject.getStepKey())
            .title(dataObject.getTitle())
            .status(ChatGoalStepStatus.normalize(dataObject.getStatus(), ChatGoalStepStatus.PENDING))
            .sortNo(dataObject.getSortNo())
            .detail(dataObject.getDetail())
            .startedAt(dataObject.getStartedAt())
            .completedAt(dataObject.getCompletedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }

    /**
     * 将目标事件数据库对象转换为仓储事件记录，保留 payloadJson 原文供管理端展示。
     */
    private ChatGoalEventRecord toEventRecord(ChatGoalEventDO dataObject) {
        return new ChatGoalEventRecord(
            dataObject.getId(),
            dataObject.getGoalId(),
            dataObject.getConversationId(),
            dataObject.getRunId(),
            dataObject.getEventType(),
            dataObject.getPayloadJson(),
            dataObject.getCreatedAt()
        );
    }
}
