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
import com.codingx.governance.application.service.HookRuleService;
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

    /** 旧 run 自动收口时写入前端和管理端可读的统一中文原因。 */
    private static final String INTERRUPTED_RUN_MESSAGE = "上一次执行已中断，已自动收口";

    /** 聊天主应用服务，承接后台线程中真正的消息处理链路。 */
    private final ChatApplicationService chatApplicationService;
    /** 运行态守卫服务，用于提交前检查会话是否已有执行中的 run。 */
    private final ChatRuntimeGuardService chatRuntimeGuardService;
    /** Trace 记录服务，用于后台执行失败或本地执行时收口链路状态。 */
    private final ConversationTraceRecordService conversationTraceRecordService;
    /** 执行运行仓储，用于恢复重新生成时的原始 run 上下文。 */
    private final ChatExecutionRunRepository chatExecutionRunRepository;
    /** 会话仓储，用于校验会话归属并读取运行目标和工作空间绑定。 */
    private final ChatConversationRepository chatConversationRepository;
    /** 工作空间绑定服务，用于把本地运行和会话映射到真实仓库目录。 */
    private final ChatWorkspaceBindingService chatWorkspaceBindingService;
    /** 流事件发布器，用于向前端推送异步执行开始、失败和完成事件。 */
    private final ChatStreamPublisher chatStreamPublisher;
    /** 聊天后台执行器，隔离 HTTP/SSE 入口线程与实际模型执行线程。 */
    private final ExecutorService executor;
    /** 技能本地缓存服务，用于云端运行启动前同步可用技能包。 */
    private final com.codingx.skill.application.service.SkillLocalCacheService skillLocalCacheService;
    /** Hook 规则服务，用于发布任务级生命周期事件，供桌面通知或宠物联动消费。 */
    private final HookRuleService hookRuleService;

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
        ChatConversationRepository chatConversationRepository,
        ChatWorkspaceBindingService chatWorkspaceBindingService,
        ChatStreamPublisher chatStreamPublisher,
        com.codingx.skill.application.service.SkillLocalCacheService skillLocalCacheService,
        HookRuleService hookRuleService,
        @Qualifier("chatStreamExecutor")
        ExecutorService executor
    ) {
        this.chatApplicationService = chatApplicationService;
        this.chatRuntimeGuardService = chatRuntimeGuardService;
        this.conversationTraceRecordService = conversationTraceRecordService;
        this.chatExecutionRunRepository = chatExecutionRunRepository;
        this.chatConversationRepository = chatConversationRepository;
        this.chatWorkspaceBindingService = chatWorkspaceBindingService;
        this.chatStreamPublisher = chatStreamPublisher;
        this.skillLocalCacheService = skillLocalCacheService;
        this.hookRuleService = hookRuleService;
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
        ChatConversationRepository chatConversationRepository,
        ChatWorkspaceBindingService chatWorkspaceBindingService,
        ExecutorService executor
    ) {
        this(
            chatApplicationService,
            chatRuntimeGuardService,
            conversationTraceRecordService,
            chatExecutionRunRepository,
            chatConversationRepository,
            chatWorkspaceBindingService,
            new NoopChatStreamPublisher(),
            null,
            null,
            executor
        );
    }

    /**
     * 兼容既有单测构造签名，允许测试显式注入流发布器和技能缓存但不接入 Hook 服务。
     */
    public ChatStreamExecutionService(
        ChatApplicationService chatApplicationService,
        ChatRuntimeGuardService chatRuntimeGuardService,
        ConversationTraceRecordService conversationTraceRecordService,
        ChatExecutionRunRepository chatExecutionRunRepository,
        ChatConversationRepository chatConversationRepository,
        ChatWorkspaceBindingService chatWorkspaceBindingService,
        ChatStreamPublisher chatStreamPublisher,
        com.codingx.skill.application.service.SkillLocalCacheService skillLocalCacheService,
        ExecutorService executor
    ) {
        this(
            chatApplicationService,
            chatRuntimeGuardService,
            conversationTraceRecordService,
            chatExecutionRunRepository,
            chatConversationRepository,
            chatWorkspaceBindingService,
            chatStreamPublisher,
            skillLocalCacheService,
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
     * 按指定运行标识派发聊天处理；taskId 字段仅作为兼容列继续写入同一个 runId。
     * @param runId 后台运行标识。
     * @param command 聊天消息命令。
     * @param userId 当前用户标识。
     */
    public void dispatch(Long runId, SendChatMessageCommand command, Long userId) {
        // 步骤 1：本地临时运行不创建持久化 run 和 Trace，直接派发内存级执行链路。
        boolean effectivePlanMode = isEffectivePlanMode(command);
        if (command.localOnly()) {
            log.info(
                "聊天派发: runId={}, 会话={}, 模式=本地, 深度思考={}, 目标模式={}, 有效目标模式={}, MCP数={}, 技能数={}, 专家={}",
                runId,
                command.conversationId(),
                command.deepThinking(),
                command.planMode(),
                effectivePlanMode,
                sizeOf(command.mcpCodes()),
                sizeOf(command.skillCodes()),
                command.expertCode()
            );
            dispatchLocalOnly(runId, command, userId);
            return;
        }
        // 步骤 2：云端运行只落库执行 run，保证前端 meta、后台线程和执行时间线使用同一个主键。
        LocalDateTime now = LocalDateTime.now();
        log.info(
            "聊天派发: runId={}, 会话={}, 模式=云端, 深度思考={}, 目标模式={}, 有效目标模式={}, MCP数={}, 技能数={}, 专家={}",
            runId,
            command.conversationId(),
            command.deepThinking(),
            command.planMode(),
            effectivePlanMode,
            sizeOf(command.mcpCodes()),
            sizeOf(command.skillCodes()),
            command.expertCode()
        );
        markInterruptedConversationRuns(command.conversationId(), runId, now);
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
        // 步骤 3：MCP、技能与专家选择由应用服务写入 chat_execution_step，派发层不再维护旧绑定表。
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
                ChatToolExecutionContext.bindGovernanceContext(userId, command.conversationId(), runId);
                bindToolWorkingDirectory(command, userId);
                tempSkillRoot = bindSkillDirectories(command);
                triggerGovernanceHook("BEFORE_TASK_START", command.conversationId(), runId, null, "任务开始执行");
                chatApplicationService.sendMessage(command, userId);
                markConversationRunFinished(command.conversationId());
            } catch (ConflictException exception) {
                // 步骤 5：队列或运行门控拒绝时记录 REJECTED，前端按业务冲突展示而不是系统错误。
                markRunRejected(runId, command.conversationId(), exception.getMessage());
                triggerGovernanceHook("TASK_FAILED", command.conversationId(), runId, null, "任务被拒绝：" + exception.getMessage());
                markConversationRunFinished(command.conversationId());
                throw exception;
            } catch (IllegalStateException exception) {
                // 步骤 6：已知运行态异常写入失败状态并通过 SSE 告知前端，保留原异常继续向线程池传播。
                markRunFailed(runId, command.conversationId(), exception);
                triggerGovernanceHook("TASK_FAILED", command.conversationId(), runId, null, "任务失败：" + exception.getMessage());
                markConversationRunFinished(command.conversationId());
                chatStreamPublisher.publishError(command.conversationId(), exception.getMessage());
                throw exception;
            } catch (Throwable throwable) {
                // 步骤 7：未知异常统一写入失败终态，避免 run 长时间停留在 RUNNING。
                markRunFailed(runId, command.conversationId(), throwable);
                triggerGovernanceHook("TASK_FAILED", command.conversationId(), runId, null, "任务失败：" + throwable.getMessage());
                markConversationRunFinished(command.conversationId());
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
     * 本地运行态只保留内存级执行生命周期，不写 run、trace 或能力绑定表。
     * 业务意图：本地历史由客户端快照负责，后端只承担本次临时推理和工具执行。
     * @param runId 临时运行标识。
     * @param command 本地运行命令。
     * @param userId 当前用户标识。
     */
    private void dispatchLocalOnly(Long runId, SendChatMessageCommand command, Long userId) {
        // 步骤 1：本地运行只注册取消句柄，不写入 run 或 Trace 表。
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
                ChatToolExecutionContext.bindGovernanceContext(userId, command.conversationId(), runId);
                bindToolWorkingDirectory(command, userId);
                tempSkillRoot = bindSkillDirectories(command);
                triggerGovernanceHook("BEFORE_TASK_START", command.conversationId(), runId, null, "本地任务开始执行");
                chatApplicationService.sendMessage(command, userId);
            } catch (Throwable throwable) {
                triggerGovernanceHook("TASK_FAILED", command.conversationId(), runId, null, "本地任务失败：" + throwable.getMessage());
                throw throwable;
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
     * 后台 run 收口后将会话提醒状态置为未读，供前端历史列表刷新后提示用户查看结果。
     * @param conversationId 会话标识。
     */
    private void markConversationRunFinished(Long conversationId) {
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
            // 提醒状态只影响侧栏提示，不能反向污染已经完成的后台 run 终态。
            log.warn("标记会话运行完成提醒未读失败，conversationId={}", conversationId, exception);
        }
    }

    /**
     * 新 run 派发前收口同会话旧运行记录，避免服务重启、模型长连接中断或旧线程丢失后前端一直显示运行中。
     * @param conversationId 会话标识。
     * @param currentRunId 本次即将创建的 run 标识。
     * @param interruptedAt 收口时间。
     */
    private void markInterruptedConversationRuns(Long conversationId, Long currentRunId, LocalDateTime interruptedAt) {
        if (conversationId == null || currentRunId == null) {
            return;
        }
        java.util.List<ChatExecutionRun> previousRuns;
        try {
            // 步骤 1：只读取同会话 run，避免跨会话任务被误收口。
            previousRuns = chatExecutionRunRepository.findByConversationId(conversationId);
        } catch (RuntimeException exception) {
            log.warn("读取同会话旧运行记录失败，跳过自动收口: conversationId={}", conversationId, exception);
            return;
        }
        for (ChatExecutionRun previousRun : previousRuns) {
            if (!shouldMarkInterrupted(previousRun, currentRunId)) {
                continue;
            }
            // 步骤 2：仅把旧运行态写为明确失败终态，不修改消息、步骤和目标事件历史。
            chatExecutionRunRepository.save(previousRun.toBuilder()
                .status("ERROR")
                .queueStatus("FAILED")
                .errorMessage(INTERRUPTED_RUN_MESSAGE)
                .finishedAt(interruptedAt)
                .updatedAt(interruptedAt)
                .build());
            log.warn(
                "旧聊天 run 已自动收口: conversationId={}, oldRunId={}, newRunId={}, oldStatus={}, oldQueueStatus={}",
                conversationId,
                previousRun.getId(),
                currentRunId,
                previousRun.getStatus(),
                previousRun.getQueueStatus()
            );
        }
    }

    /**
     * 判断旧 run 是否属于需要自动中断收口的运行态。
     * @param previousRun 候选旧 run。
     * @param currentRunId 当前 run 标识。
     * @return 是否需要收口。
     */
    private boolean shouldMarkInterrupted(ChatExecutionRun previousRun, Long currentRunId) {
        if (previousRun == null || previousRun.getId() == null || previousRun.getId().equals(currentRunId)) {
            return false;
        }
        if (previousRun.getFinishedAt() != null) {
            return false;
        }
        return StrUtil.equalsAnyIgnoreCase(previousRun.getStatus(), "RUNNING", "WAITING", "ACQUIRED")
            || StrUtil.equalsAnyIgnoreCase(previousRun.getQueueStatus(), "WAITING", "ACQUIRED");
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
     * 派发层只做轻量目标模式识别，方便日志判断桌面端是否传入 planMode 或由用户原文触发兜底。
     * 真实目标工具编排仍由 ChatApplicationService 负责，避免派发层承载业务状态流转。
     * @param command 聊天消息命令。
     * @return 本轮是否应被后端按目标/规划模式处理。
     */
    private boolean isEffectivePlanMode(SendChatMessageCommand command) {
        if (command == null) {
            return false;
        }
        if (command.planMode()) {
            return true;
        }
        String normalizedContent = StrUtil.trimToEmpty(command.content()).replaceAll("\\s+", "");
        if (StrUtil.isBlank(normalizedContent)) {
            return false;
        }
        boolean goalModeSignal = normalizedContent.contains("开启目标模式")
            || normalizedContent.contains("目标模式")
            || normalizedContent.contains("创建一个目标")
            || normalizedContent.contains("创建目标")
            || normalizedContent.contains("检查当前线程是否已有目标");
        boolean executionOrProgressSignal = normalizedContent.contains("更新目标进度")
            || normalizedContent.contains("跟进进度")
            || normalizedContent.contains("每完成一个关键步骤")
            || normalizedContent.contains("执行过程中")
            || normalizedContent.contains("不要只给方案")
            || normalizedContent.contains("直接执行")
            || normalizedContent.contains("验证、提交")
            || normalizedContent.contains("完成提交");
        return goalModeSignal && executionOrProgressSignal;
    }

    /**
     * 触发任务级自动化 Hook；Hook 规则匹配失败只写日志，不能影响后台任务状态收口。
     * @param triggerPoint 任务生命周期触发点。
     * @param conversationId 会话标识。
     * @param runId 运行标识。
     * @param toolCode 关联工具编码，可为空。
     * @param contextText 事件上下文摘要。
     */
    private void triggerGovernanceHook(String triggerPoint, Long conversationId, Long runId, String toolCode, String contextText) {
        if (hookRuleService == null) {
            return;
        }
        try {
            hookRuleService.trigger(triggerPoint, conversationId, runId, toolCode, contextText);
        } catch (RuntimeException exception) {
            log.warn(
                "自动化 Hook 触发失败: triggerPoint={}, conversationId={}, runId={}",
                triggerPoint,
                conversationId,
                runId,
                exception
            );
        }
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
