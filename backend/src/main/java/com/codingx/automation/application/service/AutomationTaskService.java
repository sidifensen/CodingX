package com.codingx.automation.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.automation.domain.model.AutomationScheduleType;
import com.codingx.automation.domain.model.AutomationTask;
import com.codingx.automation.domain.model.AutomationTaskSourceType;
import com.codingx.automation.domain.repository.AutomationTaskRepository;
import com.codingx.common.exception.BusinessException;
import com.codingx.workspace.domain.repository.WorkspaceRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 自动化任务应用服务，负责创建、查询、归属校验和基础调度状态推进。
 */
@Service
@RequiredArgsConstructor
public class AutomationTaskService {

    /** 执行时间格式，页面和聊天解析统一使用 HH:mm。 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    /** 自动化任务仓储，承接任务持久化与到期查询。 */
    private final AutomationTaskRepository automationTaskRepository;
    /** 工作空间仓储，负责校验显式 workspaceId 是否属于当前用户。 */
    private final WorkspaceRepository workspaceRepository;

    /**
     * 创建页面手动配置的自动化任务。
     * @param userId 当前用户标识。
     * @param workspaceId 工作空间标识，可为空。
     * @param name 任务名称。
     * @param prompt 任务需求说明。
     * @param scheduleType 计划类型。
     * @param scheduleTime 固定执行时间。
     * @param scheduleDayOfWeek 每周执行星期。
     * @param onceExecuteAt 一次性执行时间。
     * @param now 当前时间，测试可传入固定值。
     * @return 创建后的任务。
     */
    public AutomationTask createManualTask(
        Long userId,
        Long workspaceId,
        String name,
        String prompt,
        AutomationScheduleType scheduleType,
        String scheduleTime,
        Integer scheduleDayOfWeek,
        LocalDateTime onceExecuteAt,
        LocalDateTime now
    ) {
        return createTask(
            userId,
            workspaceId,
            AutomationTaskSourceType.MANUAL,
            null,
            name,
            prompt,
            scheduleType,
            scheduleTime,
            scheduleDayOfWeek,
            onceExecuteAt,
            now
        );
    }

    /**
     * 创建聊天会话内自动识别出的自动化任务。
     * @param userId 当前用户标识。
     * @param workspaceId 来源会话工作空间，可为空。
     * @param sourceConversationId 来源会话标识。
     * @param name 任务名称。
     * @param prompt 任务需求说明。
     * @param scheduleType 计划类型。
     * @param scheduleTime 固定执行时间。
     * @param scheduleDayOfWeek 每周执行星期。
     * @param onceExecuteAt 一次性执行时间。
     * @param now 当前时间。
     * @return 创建后的任务。
     */
    public AutomationTask createChatTask(
        Long userId,
        Long workspaceId,
        Long sourceConversationId,
        String name,
        String prompt,
        AutomationScheduleType scheduleType,
        String scheduleTime,
        Integer scheduleDayOfWeek,
        LocalDateTime onceExecuteAt,
        LocalDateTime now
    ) {
        if (sourceConversationId == null) {
            throw new BusinessException("AUTOMATION_SOURCE_CONVERSATION_REQUIRED", "来源会话不能为空");
        }
        return createTask(
            userId,
            workspaceId,
            AutomationTaskSourceType.CHAT,
            sourceConversationId,
            name,
            prompt,
            scheduleType,
            scheduleTime,
            scheduleDayOfWeek,
            onceExecuteAt,
            now
        );
    }

    /**
     * 查询当前用户可见的自动化任务。
     * @param userId 当前用户标识。
     * @return 当前用户未删除任务。
     */
    public List<AutomationTask> listTasks(Long userId) {
        requireUserId(userId);
        return automationTaskRepository.findByUserId(userId);
    }

    /**
     * 触发已经到期的启用任务，并推进下一次执行时间。
     * @param now 当前调度时间。
     * @return 本次触发的任务快照。
     */
    public List<AutomationTask> triggerDueTasks(LocalDateTime now) {
        LocalDateTime effectiveNow = now == null ? LocalDateTime.now() : now;
        return automationTaskRepository.findDueTasks(effectiveNow, 100)
            .stream()
            .map(task -> triggerTask(task, effectiveNow))
            .flatMap(Optional::stream)
            .toList();
    }

    /**
     * 统一创建任务，集中校验输入并计算下一次执行时间。
     */
    private AutomationTask createTask(
        Long userId,
        Long workspaceId,
        AutomationTaskSourceType sourceType,
        Long sourceConversationId,
        String name,
        String prompt,
        AutomationScheduleType scheduleType,
        String scheduleTime,
        Integer scheduleDayOfWeek,
        LocalDateTime onceExecuteAt,
        LocalDateTime now
    ) {
        requireUserId(userId);
        validateWorkspaceOwnership(workspaceId, userId);
        String normalizedName = requireText(name, "AUTOMATION_NAME_REQUIRED", "任务名称不能为空");
        String normalizedPrompt = requireText(prompt, "AUTOMATION_PROMPT_REQUIRED", "需求说明不能为空");
        AutomationScheduleType effectiveScheduleType = scheduleType == null ? AutomationScheduleType.DAILY : scheduleType;
        LocalDateTime effectiveNow = now == null ? LocalDateTime.now() : now;
        LocalTime parsedTime = parseScheduleTime(effectiveScheduleType, scheduleTime);
        Integer normalizedDayOfWeek = normalizeDayOfWeek(effectiveScheduleType, scheduleDayOfWeek, effectiveNow);
        LocalDateTime normalizedOnceExecuteAt = normalizeOnceExecuteAt(effectiveScheduleType, onceExecuteAt, parsedTime, effectiveNow);
        LocalDateTime nextRunAt = calculateNextRunAt(
            effectiveScheduleType,
            parsedTime,
            normalizedDayOfWeek,
            normalizedOnceExecuteAt,
            effectiveNow
        );
        AutomationTask task = AutomationTask.builder()
            .id(IdUtil.getSnowflakeNextId())
            .userId(userId)
            .workspaceId(workspaceId)
            .sourceType(sourceType)
            .sourceConversationId(sourceConversationId)
            .name(normalizedName)
            .prompt(normalizedPrompt)
            .scheduleType(effectiveScheduleType)
            .scheduleTime(parsedTime == null ? null : parsedTime.format(TIME_FORMATTER))
            .scheduleDayOfWeek(normalizedDayOfWeek)
            .onceExecuteAt(normalizedOnceExecuteAt)
            .nextRunAt(nextRunAt)
            .lastRunStatus("PENDING")
            .enabled(true)
            .deleted(false)
            .createdAt(effectiveNow)
            .updatedAt(effectiveNow)
            .build();
        automationTaskRepository.save(task);
        return task;
    }

    /**
     * 将到期任务推进为已触发，并为周期任务计算下一次执行时间。
     */
    private Optional<AutomationTask> triggerTask(AutomationTask task, LocalDateTime now) {
        LocalTime parsedTime = parseScheduleTime(task.getScheduleType(), task.getScheduleTime());
        LocalDateTime nextRunAt = task.getScheduleType() == AutomationScheduleType.ONCE
            ? null
            : calculateNextRunAt(
                task.getScheduleType(),
                parsedTime,
                task.getScheduleDayOfWeek(),
                task.getOnceExecuteAt(),
                now.plusSeconds(1)
            );
        AutomationTask updatedTask = task.toBuilder()
            .lastRunAt(now)
            .lastRunStatus("TRIGGERED")
            .nextRunAt(nextRunAt)
            .enabled(task.getScheduleType() == AutomationScheduleType.ONCE ? false : task.isEnabled())
            .updatedAt(now)
            .build();
        boolean claimed = automationTaskRepository.markTriggeredIfDue(updatedTask, task.getNextRunAt());
        return claimed ? Optional.of(updatedTask) : Optional.empty();
    }

    /**
     * 校验并解析 HH:mm 时间。
     */
    private LocalTime parseScheduleTime(AutomationScheduleType scheduleType, String scheduleTime) {
        if (scheduleType == AutomationScheduleType.ONCE && StrUtil.isBlank(scheduleTime)) {
            return null;
        }
        if (StrUtil.isBlank(scheduleTime)) {
            throw new BusinessException("AUTOMATION_SCHEDULE_TIME_REQUIRED", "执行时间不能为空");
        }
        try {
            return LocalTime.parse(StrUtil.trim(scheduleTime), TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new BusinessException("AUTOMATION_SCHEDULE_TIME_INVALID", "执行时间格式不正确");
        }
    }

    /**
     * 规范化每周执行星期，缺省时使用当前日期的星期。
     */
    private Integer normalizeDayOfWeek(
        AutomationScheduleType scheduleType,
        Integer scheduleDayOfWeek,
        LocalDateTime now
    ) {
        if (scheduleType != AutomationScheduleType.WEEKLY) {
            return null;
        }
        int effectiveDayOfWeek = scheduleDayOfWeek == null
            ? now.getDayOfWeek().getValue()
            : scheduleDayOfWeek;
        if (effectiveDayOfWeek < 1 || effectiveDayOfWeek > 7) {
            throw new BusinessException("AUTOMATION_WEEKDAY_INVALID", "执行星期不正确");
        }
        return effectiveDayOfWeek;
    }

    /**
     * 一次性任务可用显式日期时间，也可由当天时间自动折算为未来最近一次。
     */
    private LocalDateTime normalizeOnceExecuteAt(
        AutomationScheduleType scheduleType,
        LocalDateTime onceExecuteAt,
        LocalTime parsedTime,
        LocalDateTime now
    ) {
        if (scheduleType != AutomationScheduleType.ONCE) {
            return null;
        }
        if (onceExecuteAt != null) {
            if (!onceExecuteAt.isAfter(now)) {
                throw new BusinessException("AUTOMATION_ONCE_TIME_EXPIRED", "一次性执行时间必须晚于当前时间");
            }
            return onceExecuteAt;
        }
        if (parsedTime == null) {
            throw new BusinessException("AUTOMATION_ONCE_TIME_REQUIRED", "一次性执行时间不能为空");
        }
        LocalDateTime candidate = LocalDateTime.of(now.toLocalDate(), parsedTime);
        return candidate.isAfter(now) ? candidate : candidate.plusDays(1);
    }

    /**
     * 根据计划类型计算下一次执行时间。
     */
    private LocalDateTime calculateNextRunAt(
        AutomationScheduleType scheduleType,
        LocalTime scheduleTime,
        Integer scheduleDayOfWeek,
        LocalDateTime onceExecuteAt,
        LocalDateTime now
    ) {
        return switch (scheduleType) {
            case DAILY -> nextDailyRunAt(scheduleTime, now);
            case WEEKLY -> nextWeeklyRunAt(scheduleTime, scheduleDayOfWeek, now);
            case ONCE -> onceExecuteAt;
        };
    }

    /**
     * 计算每日任务下一次执行时间，若今天已过则顺延到明天。
     */
    private LocalDateTime nextDailyRunAt(LocalTime scheduleTime, LocalDateTime now) {
        LocalDateTime candidate = LocalDateTime.of(now.toLocalDate(), scheduleTime);
        return candidate.isAfter(now) ? candidate : candidate.plusDays(1);
    }

    /**
     * 计算每周任务下一次执行时间，若本周目标时间已过则顺延一周。
     */
    private LocalDateTime nextWeeklyRunAt(LocalTime scheduleTime, Integer scheduleDayOfWeek, LocalDateTime now) {
        int targetDayOfWeek = scheduleDayOfWeek == null ? now.getDayOfWeek().getValue() : scheduleDayOfWeek;
        LocalDate targetDate = now.toLocalDate();
        int delta = targetDayOfWeek - now.getDayOfWeek().getValue();
        if (delta < 0) {
            delta += 7;
        }
        targetDate = targetDate.plusDays(delta);
        LocalDateTime candidate = LocalDateTime.of(targetDate, scheduleTime);
        return candidate.isAfter(now) ? candidate : candidate.plusWeeks(1);
    }

    /**
     * 校验用户身份必须存在。
     */
    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException("AUTOMATION_USER_REQUIRED", "用户身份不能为空");
        }
    }

    /**
     * 校验必填文本并统一裁剪空白。
     */
    private String requireText(String value, String code, String message) {
        String normalizedValue = StrUtil.trimToEmpty(value);
        if (StrUtil.isBlank(normalizedValue)) {
            throw new BusinessException(code, message);
        }
        return normalizedValue;
    }

    /**
     * 校验显式工作空间归属；空工作空间表示任务不绑定项目上下文，直接放行。
     */
    private void validateWorkspaceOwnership(Long workspaceId, Long userId) {
        if (workspaceId == null) {
            return;
        }
        workspaceRepository.ensureOwnedByUser(workspaceId, userId);
    }

    /**
     * 将计划类型字符串解析为枚举，供接口层复用。
     */
    public AutomationScheduleType parseScheduleType(String value) {
        if (StrUtil.isBlank(value)) {
            return AutomationScheduleType.DAILY;
        }
        try {
            return AutomationScheduleType.valueOf(StrUtil.trim(value).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("AUTOMATION_SCHEDULE_TYPE_INVALID", "计划类型不正确");
        }
    }
}
