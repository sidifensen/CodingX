package com.codingx.chat.application.service.chat;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.application.service.ChatApplicationService;
import com.codingx.chat.application.service.ChatExecutionContext;
import com.codingx.chat.application.service.ChatRuntimeGuardService;
import com.codingx.chat.application.service.ChatWorkspaceBindingService;
import com.codingx.chat.application.service.ConversationTraceRecordService;
import com.codingx.chat.application.service.ConversationTraceContext;
import com.codingx.chat.domain.model.ChatExecutionRun;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.chat.domain.port.ChatStreamPublisher;
import com.codingx.common.exception.ConflictException;
import com.codingx.expert.domain.repository.ChatExpertRepository;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.task.domain.model.RuntimeType;
import com.codingx.task.domain.model.Task;
import com.codingx.task.domain.repository.TaskRepository;
import com.codingx.tool.application.service.ChatToolExecutionContext;
import java.time.LocalDateTime;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.codingx.chat.infrastructure.stream.NoopChatStreamPublisher;

/**
 * 负责将聊天消息处理异步派发到后台线程，避免 SSE 入口阻塞整个 HTTP 请求。
 */
@Service
@Slf4j
public class ChatStreamExecutionService {

    /** 聊天主应用服务，承接异步任务中真正的消息处理链路。 */
    private final ChatApplicationService chatApplicationService;
    /** 运行态守卫服务，用于提交前检查会话是否已有执行中任务。 */
    private final ChatRuntimeGuardService chatRuntimeGuardService;
    /** Trace 记录服务，用于异步任务失败或本地执行时收口链路状态。 */
    private final ConversationTraceRecordService conversationTraceRecordService;
    /** 执行运行仓储，用于恢复重新生成时的原始 run 上下文。 */
    private final ChatExecutionRunRepository chatExecutionRunRepository;
    /** MCP 配置仓储，用于根据历史 run 恢复已选 MCP 工具。 */
    private final ChatMcpRepository chatMcpRepository;
    /** 技能仓储，用于根据历史 run 恢复已选技能编码。 */
    private final ChatSkillRepository chatSkillRepository;
    /** 专家仓储，用于根据历史 run 恢复已选专家编码。 */
    private final ChatExpertRepository chatExpertRepository;
    /** 会话仓储，用于校验会话归属并读取运行目标和工作空间绑定。 */
    private final ChatConversationRepository chatConversationRepository;
    /** 工作空间绑定服务，用于把本地任务和会话映射到真实仓库目录。 */
    private final ChatWorkspaceBindingService chatWorkspaceBindingService;
    /** 任务仓储，用于本地任务场景绑定和更新任务完成状态。 */
    private final TaskRepository taskRepository;
    /** 流事件发布器，用于向前端推送异步执行开始、失败和完成事件。 */
    private final ChatStreamPublisher chatStreamPublisher;
    /** 聊天后台执行器，隔离 HTTP/SSE 入口线程与实际模型执行线程。 */
    private final ExecutorService executor;
    /** 技能本地缓存服务，用于本地任务启动前同步可用技能包。 */
    private final com.codingx.skill.application.service.SkillLocalCacheService skillLocalCacheService;

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
        TaskRepository taskRepository,
        ChatStreamPublisher chatStreamPublisher,
        com.codingx.skill.application.service.SkillLocalCacheService skillLocalCacheService,
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
        this.taskRepository = taskRepository;
        this.chatStreamPublisher = chatStreamPublisher;
        this.skillLocalCacheService = skillLocalCacheService;
        this.executor = executor;
    }

    /**
     * 兼容既有单测构造签名，默认注入空实现流发布器。
     */
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
        TaskRepository taskRepository,
        ExecutorService executor
    ) {
        this(
            chatApplicationService,
            chatRuntimeGuardService,
            conversationTraceRecordService,
            chatExecutionRunRepository,
            chatMcpRepository,
            chatSkillRepository,
            chatExpertRepository,
            chatConversationRepository,
            chatWorkspaceBindingService,
            taskRepository,
            new NoopChatStreamPublisher(),
            null,
            executor
        );
    }

    /**
     * 异步派发聊天消息处理，同时在入口线程完成门控和取消注册。
     * @param command 聊天消息命令。
     * @param userId 当前用户标识。
     */
    public void dispatch(SendChatMessageCommand command, Long userId) {
        dispatch(cn.hutool.core.util.IdUtil.getSnowflakeNextId(), command, userId);
    }

    /**
     * 按指定任务标识派发聊天处理，保证前端 meta、任务表与执行 run 使用同一个主键。
     * @param taskId 后台任务标识。
     * @param command 聊天消息命令。
     * @param userId 当前用户标识。
     */
    public void dispatch(Long taskId, SendChatMessageCommand command, Long userId) {
        // 步骤 1：本地临时运行不创建任务、run 和 Trace，直接派发内存级执行链路。
        if (command.localOnly()) {
            log.info(
                "聊天派发: runId={}, 会话={}, 模式=本地, 深度思考={}, MCP数={}, 技能数={}, 专家={}",
                taskId,
                command.conversationId(),
                command.deepThinking(),
                sizeOf(command.mcpCodes()),
                sizeOf(command.skillCodes()),
                command.expertCode()
            );
            dispatchLocalOnly(taskId, command, userId);
            return;
        }
        // 步骤 2：云端运行先落库任务和执行 run，保证前端 meta、后台任务和执行时间线使用同一个主键。
        Long runId = taskId;
        LocalDateTime now = LocalDateTime.now();
        log.info(
            "聊天派发: runId={}, 会话={}, 模式=云端, 深度思考={}, MCP数={}, 技能数={}, 专家={}",
            runId,
            command.conversationId(),
            command.deepThinking(),
            sizeOf(command.mcpCodes()),
            sizeOf(command.skillCodes()),
            command.expertCode()
        );
        Task task = createRunningTask(taskId, command, userId);
        // Task 是可变领域对象，保存时使用快照，避免后续终态变更污染已持久化的运行态语义。
        taskRepository.save(task.toBuilder().build());
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
        // 步骤 3：MCP 与技能选择由应用服务写入 chat_execution_step，派发层只保留专家旧表兼容。
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
            Path tempSkillRoot = null;
            try {
                // 步骤 4：工作线程绑定 run、Trace、工具目录和 skill 目录后，进入聊天应用服务主链路。
                ChatExecutionContext.start(runId);
                ConversationTraceContext.bind(traceRun);
                bindToolWorkingDirectory(command, userId);
                tempSkillRoot = bindSkillDirectories(command);
                log.info("聊天执行开始");
                chatApplicationService.sendMessage(command, userId);
                log.info("聊天执行结束: runId={}, 会话={}, 状态=SUCCESS", runId, command.conversationId());
                markTaskFinished(task, command.conversationId(), null);
            } catch (ConflictException exception) {
                // 步骤 5：队列或运行门控拒绝时记录 REJECTED，前端按业务冲突展示而不是系统错误。
                markRunRejected(runId, command.conversationId(), exception.getMessage());
                markTaskFinished(task, command.conversationId(), exception);
                throw exception;
            } catch (IllegalStateException exception) {
                // 步骤 6：已知运行态异常写入失败状态并通过 SSE 告知前端，保留原异常继续向线程池传播。
                markRunFailed(runId, command.conversationId(), exception);
                markTaskFinished(task, command.conversationId(), exception);
                chatStreamPublisher.publishError(command.conversationId(), exception.getMessage());
                throw exception;
            } catch (Throwable throwable) {
                // 步骤 7：未知异常统一写入失败终态，避免任务和 run 长时间停留在 RUNNING。
                markRunFailed(runId, command.conversationId(), throwable);
                markTaskFinished(task, command.conversationId(), throwable);
                chatStreamPublisher.publishError(command.conversationId(), throwable.getMessage());
                throw throwable;
            } finally {
                // 步骤 8：释放云端临时 skill 目录、会话运行锁和线程上下文，防止影响后续请求。
                if (tempSkillRoot != null) {
                    try {
                        cn.hutool.core.io.FileUtil.del(tempSkillRoot.toFile());
                    } catch (Exception exception) {
                        log.warn("临时 skill 目录清理失败: path={}", tempSkillRoot, exception);
                    }
                }
                chatRuntimeGuardService.completeConversation(command.conversationId(), runId);
                ChatExecutionContext.clear();
                ConversationTraceContext.clear();
                ChatToolExecutionContext.clear();
            }
        });
        futureRef.set(future);
    }

    /**
     * 本地运行态只保留内存级执行生命周期，不写任务、run、trace 或能力绑定表。
     * 业务意图：本地历史由客户端快照负责，后端只承担本次临时推理和工具执行。
     * @param taskId 临时运行标识。
     * @param command 本地运行命令。
     * @param userId 当前用户标识。
     */
    private void dispatchLocalOnly(Long taskId, SendChatMessageCommand command, Long userId) {
        // 步骤 1：本地运行以 taskId 作为临时 runId，只注册取消句柄，不写入任务、run 或 Trace 表。
        Long runId = taskId;
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        chatRuntimeGuardService.registerCancellation(command.conversationId(), runId, () -> {
            Future<?> future = futureRef.get();
            if (future != null) {
                future.cancel(true);
            }
        });
        Future<?> future = executor.submit(() -> {
            Path tempSkillRoot = null;
            try {
                // 步骤 2：进入后台线程后绑定运行上下文、工具工作目录和临时技能目录，再复用主聊天链路执行。
                ChatExecutionContext.start(runId);
                bindToolWorkingDirectory(command, userId);
                tempSkillRoot = bindSkillDirectories(command);
                log.info("聊天执行开始");
                chatApplicationService.sendMessage(command, userId);
                log.info("聊天执行结束: runId={}, 会话={}, 状态=SUCCESS", runId, command.conversationId());
            } finally {
                // 步骤 3：无论执行成功、失败或取消，都清理临时目录、运行锁和线程上下文，避免污染下一次本地运行。
                if (tempSkillRoot != null) {
                    try {
                        cn.hutool.core.io.FileUtil.del(tempSkillRoot.toFile());
                    } catch (Exception exception) {
                        log.warn("临时 skill 目录清理失败: path={}", tempSkillRoot, exception);
                    }
                }
                chatRuntimeGuardService.completeConversation(command.conversationId(), runId);
                ChatExecutionContext.clear();
                ChatToolExecutionContext.clear();
            }
        });
        futureRef.set(future);
    }

    /**
     * 创建并启动聊天后台任务；任务运行时类型按本地仓库参数做最小区分。
     * @param taskId 任务主键。
     * @param command 聊天命令。
     * @param userId 创建人。
     * @return 已进入 RUNNING 的任务。
     */
    private Task createRunningTask(Long taskId, SendChatMessageCommand command, Long userId) {
        Task task = Task.create(
            taskId,
            resolveTaskTitle(command),
            "聊天会话后台执行任务",
            StrUtil.isNotBlank(command.repositoryPath()) ? RuntimeType.LOCAL : RuntimeType.CLOUD,
            resolveWorkspaceId(command.conversationId()),
            userId,
            command.skillCodes()
        );
        task.start();
        return task;
    }

    /**
     * 任务标题保留用户问题前缀，便于后续任务页定位来源会话。
     * @param command 聊天命令。
     * @return 任务标题。
     */
    private String resolveTaskTitle(SendChatMessageCommand command) {
        String content = StrUtil.blankToDefault(command.content(), "聊天会话");
        return content.length() > 40 ? content.substring(0, 40) : content;
    }

    /**
     * 尝试读取会话工作空间，用于任务与工作空间建立弱绑定；读取失败不阻断执行。
     * @param conversationId 会话标识。
     * @return 工作空间标识。
     */
    private Long resolveWorkspaceId(Long conversationId) {
        try {
            com.codingx.chat.domain.model.ChatConversation conversation =
                chatConversationRepository.requireById(conversationId);
            return conversation == null ? null : conversation.getWorkspaceId();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 根据当前 run 的最终状态收口任务表，确保客户端断开后仍可从任务状态恢复列表展示。
     * @param task 后台任务。
     * @param conversationId 会话标识。
     * @param throwable 外层异常，可为空。
     */
    private void markTaskFinished(Task task, Long conversationId, Throwable throwable) {
        ChatExecutionRun latestRun = chatExecutionRunRepository.findByConversationId(conversationId).stream()
            .filter(run -> task.getId().equals(run.getTaskId()) || task.getId().equals(run.getId()))
            .findFirst()
            .orElse(null);
        String status = latestRun == null ? null : latestRun.getStatus();
        if (isRunSuccessful(status) && throwable == null) {
            task.complete(latestRun.getStatus());
        } else {
            String message = throwable == null
                ? latestRun == null ? null : latestRun.getErrorMessage()
                : throwable.getMessage();
            task.fail(message);
        }
        taskRepository.save(task.toBuilder().build());
        markConversationTaskCompletionUnread(conversationId);
    }

    /**
     * 后台任务收口后将会话提醒状态置为未读，供前端历史列表刷新后提示用户查看结果。
     * @param conversationId 会话标识。
     */
    private void markConversationTaskCompletionUnread(Long conversationId) {
        Optional<com.codingx.chat.domain.model.ChatConversation> conversationOptional =
            chatConversationRepository.findById(conversationId);
        if (conversationOptional == null || conversationOptional.isEmpty()) {
            return;
        }
        try {
            com.codingx.chat.domain.model.ChatConversation conversation = conversationOptional.get();
            conversation.markTaskCompletionUnread();
            chatConversationRepository.save(conversation);
        } catch (Exception exception) {
            // 提醒状态只影响侧栏提示，不能反向污染已经完成的后台任务终态。
            log.warn("标记会话任务完成提醒未读失败，conversationId={}", conversationId, exception);
        }
    }

    /**
     * 判断执行 run 是否为成功终态；未知或取消都按失败任务收口，避免列表继续显示运行中。
     * @param status run 状态。
     * @return 是否成功。
     */
    private boolean isRunSuccessful(String status) {
        return StrUtil.equalsAnyIgnoreCase(status, "COMPLETED", "SUCCESS");
    }

    /**
     * 将会话绑定仓库目录注入工具执行线程上下文，优先使用消息显式传参。
     * @param command 聊天命令。
     * @param userId 当前用户标识。
     */
    private void bindToolWorkingDirectory(SendChatMessageCommand command, Long userId) {
        // 步骤 1：优先使用本次消息显式携带的仓库目录，显式参数非法时再回退历史绑定。
        if (StrUtil.isNotBlank(command.repositoryPath())) {
            try {
                ChatToolExecutionContext.bindToolWorkingDirectory(Path.of(command.repositoryPath()));
                return;
            } catch (Exception ignored) {
                // repositoryPath 非法时回退用户默认绑定，避免单次异常参数阻断对话链路。
            }
        }
        // 步骤 2：显式目录不可用时按会话 workspaceId 查询持久化工作空间目录。
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
        // 步骤 3：会话没有绑定 workspace 时退回用户级目录绑定；仍无目录则让工具链路自行按默认工作区处理。
        Optional<Path> boundRepositoryPath = chatWorkspaceBindingService.findRepositoryPathByUserId(userId);
        boundRepositoryPath.ifPresent(ChatToolExecutionContext::bindToolWorkingDirectory);
    }

    /**
     * 绑定 skill 目录到工具执行上下文。
     * @param command 聊天命令。
     * @return 云端运行时创建的临时目录根路径，本地运行时返回 null。
     */
    private Path bindSkillDirectories(SendChatMessageCommand command) {
        // 步骤 1：没有选择技能时不绑定目录，后续工具执行只使用默认工作目录。
        if (command.skillCodes() == null || command.skillCodes().isEmpty()) {
            return null;
        }

        java.util.Map<String, Path> skillDirs = new java.util.HashMap<>();
        Path tempSkillRoot = null;

        // 步骤 2：本地运行读取前端传入路径，云端运行从对象存储下载到临时目录。
        for (String skillCode : command.skillCodes()) {
            if (command.localRuntime() && command.skillPaths() != null) {
                // 本地运行时：使用前端传来的路径
                String localPath = command.skillPaths().get(skillCode);
                if (StrUtil.isNotBlank(localPath)) {
                    try {
                        Path skillPath = Path.of(localPath);
                        if (java.nio.file.Files.exists(skillPath)) {
                            skillDirs.put(skillCode, skillPath);
                        } else {
                            log.warn("本地 skill 路径不存在，跳过: skillCode={}, path={}", skillCode, localPath);
                        }
                    } catch (Exception exception) {
                        log.warn("本地 skill 路径解析失败，跳过: skillCode={}, path={}", skillCode, localPath, exception);
                    }
                }
            } else {
                // 云端运行时：从 RustFS 下载到临时目录
                try {
                    Path skillPath = skillLocalCacheService.downloadSkillToTemp(skillCode);
                    skillDirs.put(skillCode, skillPath);
                    if (tempSkillRoot == null) {
                        tempSkillRoot = resolveCloudSkillTempRoot(skillPath);
                    }
                } catch (Exception exception) {
                    log.warn("云端 skill 下载失败，跳过: skillCode={}", skillCode, exception);
                }
            }
        }

        // 步骤 3：仅在至少一个 skill 目录可用时绑定上下文，否则保留日志方便排查选择与路径不一致问题。
        if (!skillDirs.isEmpty()) {
            ChatToolExecutionContext.bindSkillDirectories(skillDirs);
            log.info(
                "技能运行目录已生效: 选择数={}, 生效数={}, 生效技能={}, 临时根={}",
                command.skillCodes().size(),
                skillDirs.size(),
                skillDirs,
                tempSkillRoot
            );
        } else {
            log.warn(
                "技能运行目录未生效: 选择技能={}, 本地运行={}, skillPaths={}",
                command.skillCodes(),
                command.localRuntime(),
                command.skillPaths()
            );
        }

        // 步骤 4：返回云端临时根目录，外层 finally 负责清理；本地目录由用户机器管理，不能删除。
        return tempSkillRoot;
    }

    /**
     * 从 skill 目录反推本次下载包根目录，清理边界必须停在 codingx-skills-*，避免误删系统 Temp 根目录。
     * @param skillPath 形如 <tmp>/codingx-skills-uuid/<skillCode> 的 skill 目录。
     * @return 可安全清理的下载包根目录；路径异常时返回 null。
     */
    private Path resolveCloudSkillTempRoot(Path skillPath) {
        if (skillPath == null || skillPath.getParent() == null || skillPath.getParent().getFileName() == null) {
            return null;
        }
        Path packageRoot = skillPath.getParent();
        if (!packageRoot.getFileName().toString().startsWith("codingx-skills-")) {
            log.warn("云端 skill 临时目录根路径异常，跳过自动清理: skillPath={}", skillPath);
            return null;
        }
        return packageRoot;
    }

    /**
     * 统一处理可空集合长度，避免日志调用点重复空判断。
     */
    private int sizeOf(java.util.Collection<?> values) {
        return values == null ? 0 : values.size();
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
