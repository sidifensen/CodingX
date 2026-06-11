package com.codingx.chat.domain.repository;

import com.codingx.chat.domain.model.ChatGoal;
import com.codingx.chat.domain.model.ChatGoalStep;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 定义聊天目标的持久化端口，应用服务通过该接口读写目标、步骤和事件。
 */
public interface ChatGoalRepository {

    /**
     * 查询当前会话当前用户的 ACTIVE 目标。
     *
     * @param conversationId 会话 ID。
     * @param userId 当前用户 ID。
     * @return 当前 active goal，不存在时为空。
     */
    Optional<ChatGoal> findActiveByConversationIdAndUserId(Long conversationId, Long userId);

    /**
     * 查询当前会话当前用户的最新目标，包含 COMPLETED/BLOCKED/CANCELLED 终态。
     *
     * @param conversationId 会话 ID。
     * @param userId 当前用户 ID。
     * @return 最新目标，不存在时为空。
     */
    Optional<ChatGoal> findLatestByConversationIdAndUserId(Long conversationId, Long userId);

    /**
     * 按目标 ID、会话和用户查询目标，避免跨会话或跨用户读取。
     *
     * @param goalId 目标 ID。
     * @param conversationId 会话 ID。
     * @param userId 当前用户 ID。
     * @return 匹配目标，不存在时为空。
     */
    Optional<ChatGoal> findByIdAndConversationIdAndUserId(Long goalId, Long conversationId, Long userId);

    /**
     * 按目标稳定键、会话和用户查询目标。
     *
     * @param goalKey 目标稳定键。
     * @param conversationId 会话 ID。
     * @param userId 当前用户 ID。
     * @return 匹配目标，不存在时为空。
     */
    Optional<ChatGoal> findByGoalKeyAndConversationIdAndUserId(String goalKey, Long conversationId, Long userId);

    /**
     * 管理端按会话查询全部未删除目标，用于会话详情只读排障。
     *
     * @param conversationId 会话 ID。
     * @return 当前会话下全部未删除目标，通常按更新时间倒序排列。
     */
    List<ChatGoal> findAllByConversationId(Long conversationId);

    /**
     * 保存目标主表，存在则更新，不存在则插入。
     *
     * @param goal 目标实体。
     */
    void saveGoal(ChatGoal goal);

    /**
     * 查询目标的未删除步骤快照。
     *
     * @param goalId 目标 ID。
     * @return 按排序号升序排列的步骤列表。
     */
    List<ChatGoalStep> findStepsByGoalId(Long goalId);

    /**
     * 管理端批量查询多个目标的未删除步骤快照，避免会话详情逐目标查询。
     *
     * @param goalIds 目标 ID 列表。
     * @return 目标步骤列表，按目标 ID、排序号和步骤 ID 稳定排列。
     */
    List<ChatGoalStep> findStepsByGoalIds(List<Long> goalIds);

    /**
     * 替换目标步骤快照，旧步骤逻辑删除，新步骤重新写入。
     *
     * @param goalId 目标 ID。
     * @param steps 最新步骤快照。
     * @param now 本次替换时间。
     */
    void replaceSteps(Long goalId, List<ChatGoalStep> steps, LocalDateTime now);

    /**
     * 追加目标事件流水。
     *
     * @param eventRecord 事件记录。
     */
    void appendEvent(ChatGoalEventRecord eventRecord);

    /**
     * 管理端按会话查询目标事件流水，用于展示 chat_goal_event 原始审计记录。
     *
     * @param conversationId 会话 ID。
     * @return 当前会话下全部目标事件，按创建时间和事件 ID 稳定排列。
     */
    List<ChatGoalEventRecord> findEventsByConversationId(Long conversationId);

    /**
     * 目标事件记录，保存工具输入和更新结果快照，便于审计和恢复。
     *
     * @param id 事件主键。
     * @param goalId 目标 ID。
     * @param conversationId 会话 ID。
     * @param runId 触发运行 ID，可为空。
     * @param eventType 事件类型。
     * @param payloadJson 事件载荷 JSON，可为空。
     * @param createdAt 创建时间。
     */
    record ChatGoalEventRecord(
        Long id,
        Long goalId,
        Long conversationId,
        Long runId,
        String eventType,
        String payloadJson,
        LocalDateTime createdAt
    ) {
    }
}
