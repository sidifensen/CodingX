package com.codingx.automation.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.automation.domain.model.AutomationTask;
import com.codingx.automation.domain.repository.AutomationTaskRepository;
import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.application.service.chat.ChatStreamExecutionService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 自动化任务执行服务，把调度器已认领的任务转换为后台聊天运行并交付到会话。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationTaskExecutionService {

    /** 自动化任务仓储，用于回写手动任务交付会话和不可交付失败状态。 */
    private final AutomationTaskRepository automationTaskRepository;

    /** 聊天会话仓储，用于校验会话归属或创建手动任务的结果交付会话。 */
    private final ChatConversationRepository chatConversationRepository;

    /** 聊天后台执行服务，复用现有搜索、模型、SSE 和未读提醒链路。 */
    private final ChatStreamExecutionService chatStreamExecutionService;

    /**
     * 批量执行本轮已认领任务；单个任务失败不能阻断同批其他任务。
     * @param tasks 本轮调度认领成功的任务快照。
     */
    public void executeTriggeredTasks(List<AutomationTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        for (AutomationTask task : tasks) {
            executeTriggeredTask(task);
        }
    }

    /**
     * 执行单个已认领任务，先解析交付会话，再异步派发聊天运行。
     * @param task 已认领的任务快照。
     */
    private void executeTriggeredTask(AutomationTask task) {
        if (task == null || task.getId() == null) {
            return;
        }
        AutomationTask dispatchSnapshot = task;
        try {
            DeliveryContext deliveryContext = resolveDeliveryContext(task)
                .orElseThrow(() -> new IllegalStateException("自动化任务来源会话不存在或无权访问"));
            dispatchSnapshot = deliveryContext.taskSnapshot();
            SendChatMessageCommand command = new SendChatMessageCommand(
                deliveryContext.conversation().getId(),
                buildExecutionPrompt(dispatchSnapshot),
                false,
                List.of(),
                List.of(),
                null,
                null,
                List.of()
            );
            // 步骤 1：使用独立 runId 承接本次自动化执行，真实搜索和模型调用由聊天后台链路完成。
            chatStreamExecutionService.dispatch(IdUtil.getSnowflakeNextId(), command, deliveryContext.taskSnapshot().getUserId());
        } catch (Exception exception) {
            // 步骤 2：交付会话解析失败属于自动化执行失败，必须记录状态但不能影响下一轮调度扫描。
            log.warn(
                "自动化任务执行派发失败: 任务ID={}, 用户ID={}, 来源会话ID={}, 异常信息={}",
                dispatchSnapshot.getId(),
                dispatchSnapshot.getUserId(),
                dispatchSnapshot.getSourceConversationId(),
                exception.getMessage()
            );
            markTaskFailed(dispatchSnapshot);
        }
    }

    /**
     * 解析任务结果交付会话；聊天任务复用来源会话，手动任务首次执行时自动创建会话。
     * @param task 已认领任务。
     * @return 可交付会话。
     */
    private Optional<DeliveryContext> resolveDeliveryContext(AutomationTask task) {
        if (task.getSourceConversationId() != null) {
            return findOwnedConversation(task.getSourceConversationId(), task.getUserId())
                .map(conversation -> new DeliveryContext(task, conversation));
        }
        ChatConversation conversation = ChatConversation.create(
            IdUtil.getSnowflakeNextId(),
            "自动化任务：" + StrUtil.blankToDefault(task.getName(), "未命名任务"),
            task.getUserId(),
            task.getWorkspaceId(),
            ChatConversationStatus.ACTIVE
        );
        // 步骤 1：新建交付会话默认已读；后台聊天执行完成后会由 ChatStreamExecutionService 标记未读。
        chatConversationRepository.save(conversation);
        // 步骤 2：把交付会话写回任务，周期任务后续继续复用同一个结果会话。
        AutomationTask taskSnapshot = task.toBuilder()
            .sourceConversationId(conversation.getId())
            .updatedAt(LocalDateTime.now())
            .build();
        automationTaskRepository.save(taskSnapshot);
        return Optional.of(new DeliveryContext(taskSnapshot, conversation));
    }

    /**
     * 按 ID 查询会话并校验归属，避免自动化任务把结果写入他人会话。
     * @param conversationId 会话标识。
     * @param userId 任务归属用户。
     * @return 当前用户拥有的会话。
     */
    private Optional<ChatConversation> findOwnedConversation(Long conversationId, Long userId) {
        Optional<ChatConversation> conversationOptional = chatConversationRepository.findById(conversationId);
        if (conversationOptional == null || conversationOptional.isEmpty()) {
            return Optional.empty();
        }
        ChatConversation conversation = conversationOptional.get();
        if (!conversation.getCreatedBy().equals(userId)) {
            return Optional.empty();
        }
        return Optional.of(conversation);
    }

    /**
     * 组装自动化执行提示词，让聊天链路知道这是到期任务执行而不是创建新任务。
     * @param task 已认领任务。
     * @return 发送给聊天服务的用户消息。
     */
    private String buildExecutionPrompt(AutomationTask task) {
        return "自动化任务「" + StrUtil.blankToDefault(task.getName(), "未命名任务") + "」已到执行时间，请直接完成以下需求并给出结果：\n"
            + StrUtil.blankToDefault(task.getPrompt(), "");
    }

    /**
     * 记录自动化任务执行派发失败状态。
     * @param task 原任务快照。
     */
    private void markTaskFailed(AutomationTask task) {
        automationTaskRepository.save(task.toBuilder()
            .lastRunStatus("FAILED")
            .updatedAt(LocalDateTime.now())
            .build());
    }

    /**
     * 自动化任务交付上下文，绑定任务最新快照和结果会话，避免失败回写丢失新建会话 ID。
     * @param taskSnapshot 已补齐交付会话的任务快照。
     * @param conversation 本次执行结果写入的聊天会话。
     */
    private record DeliveryContext(AutomationTask taskSnapshot, ChatConversation conversation) {
    }
}
