package com.codingx.chat.application.service;

import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.domain.model.ChatExecutionRun;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.chat.domain.repository.ChatSkillRepository;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 负责将聊天消息处理异步派发到后台线程，避免 SSE 入口阻塞整个 HTTP 请求。
 */
@Service
public class ChatStreamExecutionService {

    private final ChatApplicationService chatApplicationService;
    private final ChatRuntimeGuardService chatRuntimeGuardService;
    private final ConversationTraceRecordService conversationTraceRecordService;
    private final ChatExecutionRunRepository chatExecutionRunRepository;
    private final ChatSkillRepository chatSkillRepository;
    private final ExecutorService executor;

    /**
     * 注入可替换执行器，便于测试与后续线程池治理。
     * @param chatApplicationService 聊天应用服务。
     * @param chatRuntimeGuardService 运行保护服务。
     * @param executor 后台执行器。
     */
    @Autowired
    public ChatStreamExecutionService(
        ChatApplicationService chatApplicationService,
        ChatRuntimeGuardService chatRuntimeGuardService,
        ConversationTraceRecordService conversationTraceRecordService,
        ChatExecutionRunRepository chatExecutionRunRepository,
        ChatSkillRepository chatSkillRepository,
        @Qualifier("chatStreamExecutor")
        ExecutorService executor
    ) {
        this.chatApplicationService = chatApplicationService;
        this.chatRuntimeGuardService = chatRuntimeGuardService;
        this.conversationTraceRecordService = conversationTraceRecordService;
        this.chatExecutionRunRepository = chatExecutionRunRepository;
        this.chatSkillRepository = chatSkillRepository;
        this.executor = executor;
    }

    /**
     * 异步派发聊天消息处理，同时在入口线程完成门控和取消注册。
     * @param command 聊天消息命令。
     * @param userId 当前用户标识。
     */
    public void dispatch(SendChatMessageCommand command, Long userId) {
        Long runId = cn.hutool.core.util.IdUtil.getSnowflakeNextId();
        LocalDateTime now = LocalDateTime.now();
        chatExecutionRunRepository.save(ChatExecutionRun.builder()
            .id(runId)
            .conversationId(command.conversationId())
            .taskId(runId)
            .status("RUNNING")
            .startedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build());
        // 步骤：派发入口固定先写入本次技能绑定，保证工作区“当前技能”可在回放接口中稳定读取。
        chatSkillRepository.bindTaskSkills(runId, command.mcpCodes());
        com.codingx.chat.domain.model.ChatTraceRun traceRun = conversationTraceRecordService.startTrace("chat-entry", command.conversationId(), userId);
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        chatRuntimeGuardService.registerCancellation(command.conversationId(), () -> {
            Future<?> future = futureRef.get();
            if (future != null) {
                future.cancel(true);
            }
        });
        Future<?> future = executor.submit(() -> {
            try {
                ChatExecutionContext.start(runId);
                ConversationTraceContext.bind(traceRun);
                chatApplicationService.sendMessage(command, userId);
            } catch (Throwable throwable) {
                markRunFailed(runId, command.conversationId(), throwable);
                throw throwable;
            } finally {
                chatRuntimeGuardService.completeConversation(command.conversationId());
                ChatExecutionContext.clear();
                ConversationTraceContext.clear();
            }
        });
        futureRef.set(future);
    }

    /**
     * 当后台主链路在应用服务外层直接失败时，补充执行记录与 Trace 的错误收口。
     * @param runId 运行标识。
     * @param conversationId 会话标识。
     * @param throwable 原始异常。
     */
    private void markRunFailed(Long runId, Long conversationId, Throwable throwable) {
        LocalDateTime now = LocalDateTime.now();
        chatExecutionRunRepository.save(ChatExecutionRun.builder()
            .id(runId)
            .conversationId(conversationId)
            .taskId(runId)
            .status("ERROR")
            .errorMessage(throwable.getMessage())
            .finishedAt(now)
            .updatedAt(now)
            .build());
        ChatTraceRun traceRun = ConversationTraceContext.current();
        if (traceRun != null) {
            conversationTraceRecordService.finishTrace(traceRun.getTraceId(), runId, "ERROR", throwable.getMessage());
        }
    }
}
