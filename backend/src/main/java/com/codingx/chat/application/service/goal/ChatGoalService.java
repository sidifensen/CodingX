package com.codingx.chat.application.service.goal;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.domain.model.ChatGoal;
import com.codingx.chat.domain.model.ChatGoalStatus;
import com.codingx.chat.domain.model.ChatGoalStep;
import com.codingx.chat.domain.model.ChatGoalStepStatus;
import com.codingx.chat.domain.port.ChatStreamPublisher;
import com.codingx.chat.domain.repository.ChatGoalRepository;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 聊天目标应用服务，集中处理目标工具和页面查询所需的会话级目标编排。
 */
@Service
public class ChatGoalService {

    /** 目标仓储，负责目标、步骤和事件三张表的持久化。 */
    private final ChatGoalRepository chatGoalRepository;
    /** 聊天流发布端口，用于创建或更新目标后推送 goal SSE 事件；单元测试可为空。 */
    private final ChatStreamPublisher chatStreamPublisher;

    /**
     * 生产构造器，注入仓储和流式发布端口。
     *
     * @param chatGoalRepository 目标仓储。
     * @param chatStreamPublisher 聊天流事件发布端口。
     */
    @Autowired
    public ChatGoalService(ChatGoalRepository chatGoalRepository, ChatStreamPublisher chatStreamPublisher) {
        this.chatGoalRepository = chatGoalRepository;
        this.chatStreamPublisher = chatStreamPublisher;
    }

    /**
     * 测试构造器，仅注入仓储时不会发布 SSE，便于服务层单测聚焦持久化契约。
     *
     * @param chatGoalRepository 目标仓储。
     */
    public ChatGoalService(ChatGoalRepository chatGoalRepository) {
        this(chatGoalRepository, null);
    }

    /**
     * 查询当前会话的 active goal。
     *
     * @param conversationId 会话 ID。
     * @param userId 当前用户 ID。
     * @return active goal 视图，不存在时为空。
     */
    public Optional<ChatGoalView> getActiveGoal(Long conversationId, Long userId) {
        validateContext(conversationId, userId);
        return chatGoalRepository.findActiveByConversationIdAndUserId(conversationId, userId)
            .map(goal -> toView(goal, chatGoalRepository.findStepsByGoalId(goal.getId()), null));
    }

    /**
     * 查询当前会话目标，优先按 goalId，其次按 goalKey，未提供时读取 active goal。
     *
     * @param conversationId 会话 ID。
     * @param userId 当前用户 ID。
     * @param goalId 目标 ID 字符串，可为空。
     * @param goalKey 目标稳定键，可为空。
     * @return 目标视图，不存在时为空。
     */
    public Optional<ChatGoalView> getGoal(Long conversationId, Long userId, String goalId, String goalKey) {
        validateContext(conversationId, userId);
        Optional<ChatGoal> goalOptional;
        if (StrUtil.isNotBlank(goalId)) {
            goalOptional = parseLong(goalId)
                .flatMap(parsedGoalId -> chatGoalRepository.findByIdAndConversationIdAndUserId(parsedGoalId, conversationId, userId));
        } else if (StrUtil.isNotBlank(goalKey)) {
            goalOptional = chatGoalRepository.findByGoalKeyAndConversationIdAndUserId(goalKey, conversationId, userId);
        } else {
            goalOptional = chatGoalRepository.findActiveByConversationIdAndUserId(conversationId, userId);
        }
        return goalOptional.map(goal -> toView(goal, chatGoalRepository.findStepsByGoalId(goal.getId()), null));
    }

    /**
     * 创建当前会话目标；若会话已有 ACTIVE 目标，则直接返回既有目标以保证 active 唯一。
     *
     * @param conversationId 会话 ID。
     * @param userId 当前用户 ID。
     * @param runId 当前聊天运行 ID，可为空。
     * @param command 创建目标命令。
     * @return 创建或复用后的目标视图。
     */
    public ChatGoalView createGoal(Long conversationId, Long userId, Long runId, CreateGoalCommand command) {
        validateContext(conversationId, userId);
        CreateGoalCommand safeCommand = command == null
            ? new CreateGoalCommand(null, null, null, null, List.of())
            : command;
        Optional<ChatGoal> activeGoal = chatGoalRepository.findActiveByConversationIdAndUserId(conversationId, userId);
        if (activeGoal.isPresent()) {
            ChatGoal existing = activeGoal.orElseThrow();
            return toView(existing, chatGoalRepository.findStepsByGoalId(existing.getId()), "GOAL_EXISTS");
        }

        LocalDateTime now = LocalDateTime.now();
        Long goalId = parseLong(safeCommand.goalId()).orElseGet(IdUtil::getSnowflakeNextId);
        ChatGoal goal = ChatGoal.builder()
            .id(goalId)
            .conversationId(conversationId)
            .userId(userId)
            .goalKey(StrUtil.blankToDefault(safeCommand.goalKey(), "default"))
            .title(StrUtil.blankToDefault(safeCommand.title(), "默认目标"))
            .description(safeCommand.description())
            .status(ChatGoalStatus.ACTIVE)
            .progressSummary(null)
            .createdRunId(runId)
            .updatedRunId(runId)
            .createdAt(now)
            .updatedAt(now)
            .completedAt(null)
            .deleted(0)
            .build();
        List<ChatGoalStep> steps = buildSteps(goalId, safeCommand.steps(), now);

        chatGoalRepository.saveGoal(goal);
        chatGoalRepository.replaceSteps(goalId, steps, now);
        ChatGoalView view = toView(goal, steps, "GOAL_CREATED");
        appendEvent(goal, runId, "GOAL_CREATED", safeCommand, view, now);
        publishGoal(conversationId, view);
        return view;
    }

    /**
     * 更新当前会话目标，按 goalId/goalKey/active goal 定位后更新主状态和步骤快照。
     *
     * @param conversationId 会话 ID。
     * @param userId 当前用户 ID。
     * @param runId 当前聊天运行 ID，可为空。
     * @param command 更新目标命令。
     * @return 更新后的目标视图。
     */
    public ChatGoalView updateGoal(Long conversationId, Long userId, Long runId, UpdateGoalCommand command) {
        validateContext(conversationId, userId);
        UpdateGoalCommand safeCommand = command == null
            ? new UpdateGoalCommand(null, null, null, null, null, null, List.of())
            : command;
        ChatGoal existing = resolveGoal(conversationId, userId, safeCommand.goalId(), safeCommand.goalKey())
            .orElseThrow(() -> new BusinessException("CHAT_TOOL_GOAL_NOT_FOUND", ErrorMessageCatalog.CHAT_TOOL_GOAL_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        ChatGoalStatus status = ChatGoalStatus.normalize(safeCommand.status(), existing.getStatus());
        LocalDateTime completedAt = status == ChatGoalStatus.ACTIVE ? null : (existing.getCompletedAt() == null ? now : existing.getCompletedAt());
        ChatGoal updated = existing.toBuilder()
            .title(StrUtil.blankToDefault(safeCommand.title(), existing.getTitle()))
            .description(safeCommand.description() == null ? existing.getDescription() : safeCommand.description())
            .status(status)
            .progressSummary(safeCommand.progressSummary() == null ? existing.getProgressSummary() : safeCommand.progressSummary())
            .updatedRunId(runId)
            .updatedAt(now)
            .completedAt(completedAt)
            .build();
        List<ChatGoalStep> steps = safeCommand.steps() == null || safeCommand.steps().isEmpty()
            ? chatGoalRepository.findStepsByGoalId(existing.getId())
            : buildSteps(existing.getId(), safeCommand.steps(), now);

        chatGoalRepository.saveGoal(updated);
        if (safeCommand.steps() != null && !safeCommand.steps().isEmpty()) {
            chatGoalRepository.replaceSteps(existing.getId(), steps, now);
        }
        String eventType = eventTypeForStatus(status);
        ChatGoalView view = toView(updated, steps, eventType);
        appendEvent(updated, runId, eventType, safeCommand, view, now);
        publishGoal(conversationId, view);
        return view;
    }

    /**
     * 按工具传入的目标标识解析目标，未提供时回退当前 active goal。
     */
    private Optional<ChatGoal> resolveGoal(Long conversationId, Long userId, String goalId, String goalKey) {
        if (StrUtil.isNotBlank(goalId)) {
            return parseLong(goalId)
                .flatMap(parsedGoalId -> chatGoalRepository.findByIdAndConversationIdAndUserId(parsedGoalId, conversationId, userId));
        }
        if (StrUtil.isNotBlank(goalKey)) {
            return chatGoalRepository.findByGoalKeyAndConversationIdAndUserId(goalKey, conversationId, userId);
        }
        return chatGoalRepository.findActiveByConversationIdAndUserId(conversationId, userId);
    }

    /**
     * 构造步骤快照，保留模型顺序并为缺失 stepKey 的步骤生成稳定键。
     */
    private List<ChatGoalStep> buildSteps(Long goalId, List<StepCommand> commands, LocalDateTime now) {
        List<StepCommand> safeCommands = commands == null ? List.of() : commands;
        List<ChatGoalStep> steps = new ArrayList<>();
        for (int index = 0; index < safeCommands.size(); index++) {
            StepCommand stepCommand = safeCommands.get(index);
            ChatGoalStepStatus status = ChatGoalStepStatus.normalize(stepCommand.status(), ChatGoalStepStatus.PENDING);
            steps.add(ChatGoalStep.builder()
                .id(IdUtil.getSnowflakeNextId())
                .goalId(goalId)
                .stepKey(StrUtil.blankToDefault(stepCommand.stepKey(), "step-" + (index + 1)))
                .title(StrUtil.blankToDefault(stepCommand.title(), "步骤 " + (index + 1)))
                .status(status)
                .sortNo(index)
                .detail(stepCommand.detail())
                .startedAt(status == ChatGoalStepStatus.IN_PROGRESS || status == ChatGoalStepStatus.COMPLETED ? now : null)
                .completedAt(status == ChatGoalStepStatus.COMPLETED ? now : null)
                .updatedAt(now)
                .deleted(0)
                .build());
        }
        return steps;
    }

    /**
     * 追加事件流水，payload 同时保存命令和目标快照，便于后续排查模型输入与状态结果。
     */
    private void appendEvent(ChatGoal goal, Long runId, String eventType, Object command, ChatGoalView view, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("command", command);
        payload.put("goal", view);
        chatGoalRepository.appendEvent(new ChatGoalRepository.ChatGoalEventRecord(
            IdUtil.getSnowflakeNextId(),
            goal.getId(),
            goal.getConversationId(),
            runId,
            eventType,
            JSONUtil.toJsonStr(payload),
            now
        ));
    }

    /**
     * 发布目标 SSE 事件；测试构造器未注入发布器时跳过。
     */
    private void publishGoal(Long conversationId, ChatGoalView view) {
        if (chatStreamPublisher != null) {
            chatStreamPublisher.publishGoal(conversationId, view);
        }
    }

    /**
     * 将目标和步骤转换为统一视图，所有 Long ID 在这里字符串化。
     */
    private ChatGoalView toView(ChatGoal goal, List<ChatGoalStep> steps, String eventType) {
        return new ChatGoalView(
            String.valueOf(goal.getId()),
            String.valueOf(goal.getConversationId()),
            goal.getGoalKey(),
            goal.getTitle(),
            goal.getDescription(),
            goal.getStatus().name(),
            goal.getProgressSummary(),
            eventType,
            goal.getCreatedAt(),
            goal.getUpdatedAt(),
            goal.getCompletedAt(),
            steps.stream()
                .map(step -> new ChatGoalView.StepView(
                    String.valueOf(step.getId()),
                    step.getStepKey(),
                    step.getTitle(),
                    step.getStatus().name(),
                    step.getDetail(),
                    step.getSortNo()
                ))
                .toList()
        );
    }

    /**
     * 目标进入终态时使用更具体事件名，否则按普通更新记录。
     */
    private String eventTypeForStatus(ChatGoalStatus status) {
        return switch (status) {
            case COMPLETED -> "GOAL_COMPLETED";
            case BLOCKED -> "GOAL_BLOCKED";
            case CANCELLED -> "GOAL_CANCELLED";
            case ACTIVE -> "GOAL_UPDATED";
        };
    }

    /**
     * 校验目标调用必须绑定会话和用户上下文。
     */
    private void validateContext(Long conversationId, Long userId) {
        if (conversationId == null || userId == null) {
            throw new BusinessException("CHAT_TOOL_GOAL_CONTEXT_REQUIRED", "目标工具必须在聊天会话中执行");
        }
    }

    /**
     * 容忍模型传入字符串 ID，非法字符串按未提供处理。
     */
    private Optional<Long> parseLong(String value) {
        if (StrUtil.isBlank(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    /**
     * 创建目标命令，承载 create_goal 工具入参。
     *
     * @param goalId 可选目标 ID。
     * @param goalKey 可选目标稳定键。
     * @param title 目标标题。
     * @param description 目标说明。
     * @param steps 初始步骤列表。
     */
    public record CreateGoalCommand(
        String goalId,
        String goalKey,
        String title,
        String description,
        List<StepCommand> steps
    ) {
    }

    /**
     * 更新目标命令，承载 update_goal 工具入参。
     *
     * @param goalId 可选目标 ID。
     * @param goalKey 可选目标稳定键。
     * @param title 新标题，可为空。
     * @param description 新说明，可为空。
     * @param status 新状态，可为空。
     * @param progressSummary 新进度摘要，可为空。
     * @param steps 最新步骤快照，可为空或空列表。
     */
    public record UpdateGoalCommand(
        String goalId,
        String goalKey,
        String title,
        String description,
        String status,
        String progressSummary,
        List<StepCommand> steps
    ) {
    }

    /**
     * 目标步骤命令，承载 create_goal/update_goal 的 steps 数组元素。
     *
     * @param stepKey 步骤稳定键。
     * @param title 步骤标题。
     * @param status 步骤状态。
     * @param detail 步骤详情或阻塞原因。
     */
    public record StepCommand(String stepKey, String title, String status, String detail) {
    }
}
