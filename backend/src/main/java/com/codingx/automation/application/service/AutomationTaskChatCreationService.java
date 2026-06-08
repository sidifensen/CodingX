package com.codingx.automation.application.service;

import com.codingx.automation.domain.model.AutomationTask;
import com.codingx.chat.domain.model.ChatConversation;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 聊天会话内自动化创建编排服务，负责解析用户消息并直接创建定时任务。
 */
@Service
@RequiredArgsConstructor
public class AutomationTaskChatCreationService {

    /** 自动化意图解析器，用保守规则识别明确的定时任务创建请求。 */
    private final AutomationTaskIntentParser automationTaskIntentParser;
    /** 自动化任务服务，负责真实落库和下一次执行时间计算。 */
    private final AutomationTaskService automationTaskService;

    /**
     * 尝试从当前聊天消息创建自动化任务；未命中时返回空并让聊天走普通链路。
     * @param conversation 当前会话，已由聊天主链路完成归属校验。
     * @param plainQuestion 用户原始问题正文。
     * @param userId 当前用户标识。
     * @param runId 当前聊天运行标识。
     * @return 创建结果，未命中时为空。
     */
    public Optional<AutomationTaskChatCreationResult> tryCreateFromChatMessage(
        ChatConversation conversation,
        String plainQuestion,
        Long userId,
        Long runId
    ) {
        LocalDateTime now = LocalDateTime.now();
        return automationTaskIntentParser.parse(plainQuestion, now)
            .map(intent -> {
                AutomationTask task = automationTaskService.createChatTask(
                    userId,
                    conversation.getWorkspaceId(),
                    conversation.getId(),
                    intent.name(),
                    intent.prompt(),
                    intent.scheduleType(),
                    intent.scheduleTime(),
                    intent.scheduleDayOfWeek(),
                    intent.onceExecuteAt(),
                    now
                );
                return new AutomationTaskChatCreationResult(task, buildAssistantContent(task));
            });
    }

    /**
     * 构造写回聊天会话的创建成功摘要，前端按普通 assistant 消息展示。
     */
    private String buildAssistantContent(AutomationTask task) {
        return "已创建自动化任务：" + task.getName()
            + "\n执行计划：" + scheduleLabel(task)
            + "\n需求：" + task.getPrompt();
    }

    /**
     * 生成人可读计划文案。
     */
    private String scheduleLabel(AutomationTask task) {
        return switch (task.getScheduleType()) {
            case DAILY -> "每天 " + task.getScheduleTime();
            case WEEKLY -> "每周" + weekDayLabel(task.getScheduleDayOfWeek()) + " " + task.getScheduleTime();
            case ONCE -> "一次性 " + task.getOnceExecuteAt();
        };
    }

    /**
     * 将星期数字转换为中文短标签。
     */
    private String weekDayLabel(Integer dayOfWeek) {
        return switch (dayOfWeek == null ? 1 : dayOfWeek) {
            case 1 -> "一";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            case 5 -> "五";
            case 6 -> "六";
            default -> "日";
        };
    }
}
