package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.domain.model.ChatExecutionRun;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.expert.domain.repository.ChatExpertRepository;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.tool.application.service.ChatToolExecutionContext;
import java.time.LocalDateTime;
import java.nio.file.Path;
import java.util.Optional;
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
    private final ChatMcpRepository chatMcpRepository;
    private final ChatSkillRepository chatSkillRepository;
    private final ChatExpertRepository chatExpertRepository;
    private final ChatConversationRepository chatConversationRepository;
    private final ChatWorkspaceBindingService chatWorkspaceBindingService;
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
        ChatMcpRepository chatMcpRepository,
        ChatSkillRepository chatSkillRepository,
        ChatExpertRepository chatExpertRepository,
        ChatConversationRepository chatConversationRepository,
        ChatWorkspaceBindingService chatWorkspaceBindingService,
        @Qualifier("chatStreamExecutor")
        ExecutorService executor
    ) {
        this.chatApplicationService = chatApplicationService;
        this.chatRuntimeGuardService = chatRuntimeGuardService;
        this.conversationTraceRecordService = conversationTraceRecordService;
        this.chatExecutionRunRepository = chatExecutionRunRepository;
        this.chatMcpRepository = chatMcpRepository;
        this.chatSkillRepository = chatSkillRepository;
        this.chatExpertRepository = chatExpertRepository;
        this.chatConversationRepository = chatConversationRepository;
        this.chatWorkspaceBindingService = chatWorkspaceBindingService;
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
            .queueStatus("WAITING")
            .startedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build());
        // 步骤：派发入口固定先写入本次 MCP 与技能绑定，保证工作区“当前能力上下文”可在回放接口中稳定读取。
        chatMcpRepository.bindTaskMcps(runId, command.mcpCodes());
        chatSkillRepository.bindTaskSkills(runId, command.skillCodes());
        chatExpertRepository.bindTaskExpert(runId, command.expertCode());
        com.codingx.chat.domain.model.ChatTraceRun traceRun = conversationTraceRecordService.startTrace("chat-entry", command.conversationId(), userId);
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        chatRuntimeGuardService.registerCancellation(command.conversationId(), runId, () -> {
            Future<?> future = futureRef.get();
            if (future != null) {
                future.cancel(true);
            }
        });
        Future<?> future = executor.submit(() -> {
            try {
                ChatExecutionContext.start(runId);
                ConversationTraceContext.bind(traceRun);
                bindToolWorkingDirectory(command, userId);
                chatApplicationService.sendMessage(command, userId);
            } catch (IllegalStateException exception) {
                if (exception.getMessage() != null && exception.getMessage().startsWith("Conversation rejected:")) {
                    markRunRejected(runId, command.conversationId(), exception.getMessage());
                } else {
                    markRunFailed(runId, command.conversationId(), exception);
                }
                throw exception;
            } catch (Throwable throwable) {
                markRunFailed(runId, command.conversationId(), throwable);
                throw throwable;
            } finally {
                chatRuntimeGuardService.completeConversation(command.conversationId(), runId);
                ChatExecutionContext.clear();
                ConversationTraceContext.clear();
                ChatToolExecutionContext.clear();
            }
        });
        futureRef.set(future);
    }

    /**
     * 将会话绑定仓库目录注入工具执行线程上下文，优先使用消息显式传参。
     * @param command 聊天命令。
     * @param userId 当前用户标识。
     */
    private void bindToolWorkingDirectory(SendChatMessageCommand command, Long userId) {
        if (StrUtil.isNotBlank(command.repositoryPath())) {
            try {
                ChatToolExecutionContext.bindToolWorkingDirectory(Path.of(command.repositoryPath()));
                return;
            } catch (Exception ignored) {
                // repositoryPath 非法时回退用户默认绑定，避免单次异常参数阻断对话链路。
            }
        }
        Optional<com.codingx.chat.domain.model.ChatConversation> conversationOptional =
            java.util.Optional.ofNullable(command.conversationId())
                .flatMap(conversationId -> {
                    try {
                        return java.util.Optional.of(chatConversationRepository.requireById(conversationId));
                    } catch (Exception ignored) {
                        return java.util.Optional.empty();
                    }
                });
        if (conversationOptional.isPresent() && conversationOptional.get().getWorkspaceId() != null) {
            Optional<Path> workspacePath = chatWorkspaceBindingService.findRepositoryPathByWorkspaceId(
                conversationOptional.get().getWorkspaceId()
            );
            if (workspacePath.isPresent()) {
                ChatToolExecutionContext.bindToolWorkingDirectory(workspacePath.get());
                return;
            }
        }
        Optional<Path> boundRepositoryPath = chatWorkspaceBindingService.findRepositoryPathByUserId(userId);
        boundRepositoryPath.ifPresent(ChatToolExecutionContext::bindToolWorkingDirectory);
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
            .queueStatus("FAILED")
            .errorMessage(throwable.getMessage())
            .finishedAt(now)
            .updatedAt(now)
            .build());
        ChatTraceRun traceRun = ConversationTraceContext.current();
        if (traceRun != null) {
            conversationTraceRecordService.finishTrace(traceRun.getTraceId(), runId, "ERROR", throwable.getMessage());
        }
    }

    /**
     * 当门控拒绝会话时，以 REJECTED 状态收口运行记录，避免误记为系统异常。
     * @param runId 运行标识。
     * @param conversationId 会话标识。
     * @param message 拒绝原因。
     */
    private void markRunRejected(Long runId, Long conversationId, String message) {
        LocalDateTime now = LocalDateTime.now();
        chatExecutionRunRepository.save(ChatExecutionRun.builder()
            .id(runId)
            .conversationId(conversationId)
            .taskId(runId)
            .status("REJECTED")
            .queueStatus("REJECTED")
            .errorMessage(message)
            .finishedAt(now)
            .updatedAt(now)
            .build());
        ChatTraceRun traceRun = ConversationTraceContext.current();
        if (traceRun != null) {
            conversationTraceRecordService.finishTrace(traceRun.getTraceId(), runId, "REJECTED", message);
        }
    }
}
