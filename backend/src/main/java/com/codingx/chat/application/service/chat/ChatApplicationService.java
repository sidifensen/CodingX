package com.codingx.chat.application.service;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.automation.application.service.AutomationTaskChatCreationResult;
import com.codingx.automation.application.service.AutomationTaskChatCreationService;
import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatExecutionStep;
import com.codingx.chat.domain.model.ChatExecutionRun;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatIntentNode;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.chat.domain.repository.ChatExecutionStepRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.domain.port.AiChatClient;
import com.codingx.chat.domain.port.ChatStreamPublisher;
import com.codingx.chat.application.service.agent.AgentLoopCompletionReason;
import com.codingx.chat.application.service.agent.AgentLoopCoordinator;
import com.codingx.chat.application.service.agent.AgentLoopResult;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.common.support.ai.AiToolCall;
import com.codingx.common.support.ai.AiToolCallDelta;
import com.codingx.expert.application.service.ChatExpertContextService;
import com.codingx.governance.application.service.GovernanceAgentContextService;
import com.codingx.governance.application.service.HookRuleService;
import com.codingx.mcp.application.service.ChatMcpExecutionService;
import com.codingx.mcp.application.service.ChatMcpQueryService;
import com.codingx.mcp.application.executor.ChatMcpProgressListener;
import com.codingx.mcp.application.executor.ChatMcpToolResult;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.skill.application.service.ChatSkillContextService;
import com.codingx.tool.application.service.ChatToolExecutionContext;
import com.codingx.tool.application.service.ChatToolExecutionResult;
import com.codingx.tool.application.service.ChatToolExecutionService;
import com.codingx.tool.application.service.ChatToolSpec;
import com.codingx.tool.application.service.ChatToolSpecService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 聊天主流程应用服务。
 * 职责边界：
 * 1. 编排一次用户消息从鉴权、意图路由、工具执行到模型输出的完整链路
 * 2. 仅负责流程协调，不承载仓储实现细节与模型底层协议
 * 3. 确保所有出口（成功、失败、取消）都完成执行结果落库与 Trace 收口
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatApplicationService {

    private static final int PROMPT_CONTEXT_LOG_PREVIEW_LENGTH = 1_800;
    private static final String QUICK_GREETING_REPLY = "你好，我在。你可以直接说要查资料、改代码、看项目，或让我帮你梳理问题。";
    private static final Set<String> QUICK_GREETING_TEXTS = Set.of(
        "你好",
        "您好",
        "你好呀",
        "你好啊",
        "嗨",
        "哈喽",
        "hello",
        "hi",
        "在吗",
        "早上好",
        "上午好",
        "中午好",
        "下午好",
        "晚上好"
    );
    /** 会话聚合仓储，负责读取与更新会话主状态（归属、标题、最后活跃时间等） */
    private final ChatConversationRepository chatConversationRepository;
    /** 消息仓储，负责会话消息历史读写与按会话回放 */
    private final ChatMessageRepository chatMessageRepository;
    /** 执行步骤仓储，记录搜索/MCP 等中间步骤供前端步骤面板展示 */
    private final ChatExecutionStepRepository chatExecutionStepRepository;
    /** 执行运行仓储，落库每次请求的最终状态、意图与错误信息 */
    private final ChatExecutionRunRepository chatExecutionRunRepository;

    /** 模型客户端，负责发起 LLM 流式对话并回调增量输出 */
    private final AiChatClient aiChatClient;
    /** 流事件发布器，向前端推送用户消息、步骤事件与模型增量内容 */
    private final ChatStreamPublisher chatStreamPublisher;
    /** 运行态守卫，控制会话是否可执行并处理取消态判断 */
    private final ChatRuntimeGuardService chatRuntimeGuardService;

    /** 会话标题服务，基于上下文生成或刷新会话标题 */
    private final ConversationTitleService conversationTitleService;
    /** 会话摘要服务，构建模型输入历史并按需刷新会话摘要 */
    private final ConversationSummaryService conversationSummaryService;
    /** 问题改写服务，对用户问题做重写与拆分以提升检索/意图命中率 */
    private final ConversationRewriteService conversationRewriteService;
    /** 意图路由服务，决定澄清、直答、搜索、MCP 等执行分支 */
    private final ConversationIntentService conversationIntentService;
    /** 提示词模板加载器，读取系统级基础 Prompt 资产 */
    private final PromptTemplateLoader promptTemplateLoader;

    /** MCP 执行服务，调用已配置工具并返回结构化工具结果 */
    private final ChatMcpExecutionService chatMcpExecutionService;
    /** MCP 查询服务，负责按当前执行器注册状态收敛用户侧可用 MCP 列表 */
    private final ChatMcpQueryService chatMcpQueryService;
    /** MCP 配置仓储，读取工具启用状态与展示信息 */
    private final ChatMcpRepository chatMcpRepository;
    /** 技能绑定仓储，供重新生成时复用原始 run 的技能选择 */
    private final ChatSkillRepository chatSkillRepository;
    /** 附件服务，校验附件归属并绑定到当前消息 */
    private final ChatAttachmentService chatAttachmentService;
    /** 意图节点仓储，读取意图节点配置（类型、Prompt、MCP 工具映射） */
    private final com.codingx.chat.domain.repository.ChatIntentNodeRepository chatIntentNodeRepository;
    /** Web 搜索执行服务，执行外部检索并返回候选来源 */
    private final WebSearchExecutionService webSearchExecutionService;
    /** 搜索引用收集器，汇总去重来源并写入运行上下文 */
    private final SearchReferenceCollector searchReferenceCollector;
    /** 文档产物服务，基于搜索结果生成可下载文档产物 */
    private final DocumentArtifactService documentArtifactService;
    /** Trace 收口服务，确保运行链路最终状态被正确收敛 */
    private final ConversationTraceRecordService conversationTraceRecordService;
    /** Token 估算服务，用于统计模型输入规模并支撑运行观测 */
    private final com.codingx.common.support.ai.TokenCounterService tokenCounterService;
    /** LLM 输出清洗器，规整模型文本以避免脏数据落库与回显 */
    private final com.codingx.common.support.ai.LlmResponseCleaner llmResponseCleaner;
    /** 搜索并发执行器，控制多子问题检索的线程池策略 */
    @Qualifier("searchExecutor")
    private final ExecutorService searchExecutor;
    /** 运行时配置服务，提供并发上限等动态参数 */
    private final RuntimeSettingService runtimeSettingService;
    /** 技能上下文服务，将选中技能拼装为模型可消费上下文 */
    private final ChatSkillContextService chatSkillContextService;
    /** 专家上下文服务，将选中专家设定拼装为模型可消费上下文 */
    private final ChatExpertContextService chatExpertContextService;
    /** 工具 schema 服务，提供模型可见的真实本地工具集合 */
    private final ChatToolSpecService chatToolSpecService;
    /** 工具执行服务，承接模型发起的本地工具调用 */
    private final ChatToolExecutionService chatToolExecutionService;
    /** Hook 规则服务，用于匹配任务生命周期自动化动作，供桌面通知或宠物联动消费。 */
    private final HookRuleService hookRuleService;
    /** 治理上下文服务，负责把仓库规范文件和已生效长期记忆注入模型，并在完成后提取新的长期记忆 */
    private final GovernanceAgentContextService governanceAgentContextService;
    /** 会话 workspace 绑定服务，负责把本地空间映射为真实仓库目录 */
    private final ChatWorkspaceBindingService chatWorkspaceBindingService;
    /** Agent Loop 确定性规则协调器，负责轮次、完成原因和重复工具调用判断 */
    private final AgentLoopCoordinator agentLoopCoordinator;
    /** 自动化聊天创建服务，命中定时任务请求时直接落库并生成助手摘要；旧测试未注入时允许为空。 */
    private final AutomationTaskChatCreationService automationTaskChatCreationService;

    /**
     * 处理 HTTP 同步入口发送消息的协议适配逻辑。
     * @param conversationId 会话标识。
     * @param content 用户输入内容。
     * @param skillCodes 前端显式选择的技能编码。
     * @param attachmentIds 前端上传并绑定的附件主键。
     * @param userId 当前登录用户标识。
     */
    public void sendSynchronousMessage(
        Long conversationId,
        String content,
        List<String> skillCodes,
        List<Long> attachmentIds,
        Long userId
    ) {
        // 步骤 1：同步入口只接收前端显式选择的技能，统一去空、去重后进入运行上下文。
        List<String> selectedSkillCodes = skillCodes == null ? List.of() : skillCodes.stream()
            .map(StrUtil::trimToEmpty)
            .filter(StrUtil::isNotBlank)
            .distinct()
            .toList();

        // 步骤 2：按当前可执行 MCP 注册表收敛模型可见 MCP，避免把不可执行配置暴露给一次消息。
        List<String> selectedMcpCodes = chatMcpQueryService.listEnabledMcps().stream()
            .filter(mcp -> mcp.getAvailable() == null || Boolean.TRUE.equals(mcp.getAvailable()))
            .map(ChatMcp::getMcpCode)
            .filter(StrUtil::isNotBlank)
            .toList();

        // 步骤 3：复用聊天主流程发送命令，并确保成功或异常路径都释放同步入口的会话门控。
        try {
            sendMessage(
                new SendChatMessageCommand(
                    conversationId,
                    content,
                    false,
                    selectedMcpCodes,
                    selectedSkillCodes,
                    null,
                    null,
                    attachmentIds == null ? List.of() : attachmentIds
                ),
                userId
            );
        } finally {
            chatRuntimeGuardService.completeConversation(conversationId);
        }
    }

    /**
     * 重新生成最后一条助手回复，复用原始会话上下文但不重复插入用户消息。
     * 关键约束：
     * 1. 重新生成必须开启独立 run，避免覆盖上一次执行记录
     * 2. 重新生成必须复用最近一次已完成 run 的技能、MCP 与专家绑定
     * 3. 重新生成只重跑最后一条用户提问，不向消息历史重复写入用户消息
     *
     * @param conversationId 会话标识。
     * @param userId 当前用户标识。
     */
    public void regenerateLastAssistantMessage(Long conversationId, Long userId) {
        // 步骤 1：校验会话归属和运行门控，避免越权用户或并发请求触发重新生成。
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        chatRuntimeGuardService.ensureAccepted(conversationId);
        // 步骤 2：创建新的 run 与 Trace，重新生成必须独立记录执行链路，不能覆盖原 run。
        Long runId = IdUtil.getSnowflakeNextId();
        ChatExecutionContext.start(runId);
        ChatTraceRun traceRun = conversationTraceRecordService.startTrace("chat-regenerate", conversationId, userId);
        LocalDateTime now = LocalDateTime.now();
        chatExecutionRunRepository.save(ChatExecutionRun.builder()
            .id(runId)
            .conversationId(conversationId)
            .taskId(runId)
            .status("RUNNING")
            .queueStatus("ACQUIRED")
            .startedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build());
        // 步骤 3：读取原会话历史，定位最近一条用户消息和助手消息作为重新生成的输入来源。
        List<ChatMessage> history = new ArrayList<>(chatMessageRepository.findByConversationId(conversationId));
        ChatMessage lastUserMessage = findLastMessageByRole(history, ChatMessageRole.USER)
            .orElseThrow(() -> new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN));
        ChatMessage lastAssistantMessage = findLastMessageByRole(history, ChatMessageRole.ASSISTANT)
            .orElseThrow(() -> new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN));
        // 步骤 4：恢复原 run 选择的技能、MCP 与专家上下文；老数据缺失时回退解析用户消息里的技能 mention。
        Long sourceRunId = lastAssistantMessage.getRunId() != null ? lastAssistantMessage.getRunId() : resolveRegenerateSourceRunId(conversation);
        List<String> selectedSkillCodes = loadSelectedSkillCodes(sourceRunId);
        if (selectedSkillCodes.isEmpty()) {
            selectedSkillCodes = ChatCapabilityMentionSupport.parseSkillCodes(lastUserMessage.getContent());
        }
        List<String> selectedMcpCodes = loadSelectedMcpCodes(sourceRunId);
        String selectedExpertCode = loadSelectedExpertCode(sourceRunId);
        SendChatMessageCommand command = new SendChatMessageCommand(
            conversationId,
            ChatCapabilityMentionSupport.stripSelectedSkillMentions(lastUserMessage.getContent(), selectedSkillCodes),
            false,
            selectedMcpCodes,
            selectedSkillCodes,
            selectedExpertCode,
            null,
            List.of()
        );
        bindSelectedContextToRun(runId, selectedMcpCodes, selectedSkillCodes, selectedExpertCode);
        try {
            // 步骤 5：复用正常聊天执行链路生成新助手消息，失败时统一写入 run 与 Trace 终态。
            processRegeneratedMessage(command, conversation, history, lastUserMessage, runId);
        } catch (RuntimeException exception) {
            LocalDateTime failedAt = LocalDateTime.now();
            chatExecutionRunRepository.save(ChatExecutionRun.builder()
                .id(runId)
                .conversationId(conversationId)
                .taskId(runId)
                .status("ERROR")
                .queueStatus("FAILED")
                .errorMessage(exception.getMessage())
                .finishedAt(failedAt)
                .updatedAt(failedAt)
                .build());
            conversationTraceRecordService.finishTrace(traceRun.getTraceId(), runId, "ERROR", exception.getMessage());
            throw exception;
        } finally {
            // 步骤 6：释放会话运行锁和线程上下文，防止后续聊天请求继承本次 runId。
            chatRuntimeGuardService.completeConversation(conversationId, runId);
            ChatExecutionContext.clear();
        }
    }

    /**
     * 执行重新生成的消息流，复用与正常发送一致的意图分流、模型流式、落库和 Trace 收口逻辑。
     * 关键约束：
     * 1. 历史列表中不得重复写入同一条用户消息
     * 2. 重新生成只负责生成新的助手消息，不修改原始用户消息内容
     * 3. 重新生成的绑定上下文必须由原 run 的技能、MCP、专家配置恢复
     *
     * @param command 重新生成所需的命令。
     * @param conversation 当前会话。
     * @param history 会话历史。
     * @param requestMessage 触发重新生成的原始用户消息。
     * @param runId 新的运行标识。
     */
    private void processRegeneratedMessage(
        SendChatMessageCommand command,
        ChatConversation conversation,
        List<ChatMessage> history,
        ChatMessage requestMessage,
        Long runId
    ) {
        if (history.stream().noneMatch(message -> message.getId().equals(requestMessage.getId()))) {
            history.add(requestMessage);
        }
        ConversationRewriteResult rewriteResult = conversationRewriteService.rewriteResult(
            plainUserContents(history),
            command.content()
        );
        List<SearchReferenceCandidate> searchReferences = List.of();
        String rewrittenQuestion = rewriteResult.rewrite();
        boolean mcpEnabled = command.mcpCodes() != null && !command.mcpCodes().isEmpty();
        List<SubQuestionIntentDecision> subQuestionDecisions = normalizeSelectedSkillShortQuestionDecisions(
            suppressAutomaticSearchDecisions(
                suppressCodeArtifactFollowUpSearchDecisions(
                    resolveSubQuestionDecisions(rewriteResult, mcpEnabled),
                    command.content(),
                    history
                )
            ),
            command.skillCodes()
        );
        ConversationIntentDecision intentDecision = primaryIntentDecision(subQuestionDecisions);
        logChatDecision("继续生成", command, runId, intentDecision, rewriteResult);
        Optional<SubQuestionIntentDecision> clarifyDecision = firstDecisionWithAction(
            subQuestionDecisions,
            ConversationIntentAction.CLARIFY
        );
        if (clarifyDecision.isPresent() && CollUtil.isEmpty(command.skillCodes())) {
            intentDecision = clarifyDecision.get().intentDecision();
            ChatMessage assistantMessage = ChatMessage.assistantMessage(
                command.conversationId(),
                intentDecision.reply(),
                ChatMessageStatus.COMPLETED,
                null,
                null,
                null
            ).attachRun(runId);
            chatMessageRepository.save(assistantMessage);
            history.add(assistantMessage);
            conversation.rename(conversationTitleService.generateTitle(conversation, plainAiMessages(history)));
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, requestMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), false, false, ChatMessageStatus.COMPLETED, null);
            finishTrace(runId, "SUCCESS", null);
            chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getId(), assistantMessage.getContent(), conversation.getTitle());
            return;
        }
        if (isSingleDirectReply(subQuestionDecisions) && CollUtil.isEmpty(command.skillCodes())) {
            ChatMessage assistantMessage = ChatMessage.assistantMessage(
                command.conversationId(),
                intentDecision.reply(),
                ChatMessageStatus.COMPLETED,
                null,
                null,
                null
            ).attachRun(runId);
            chatMessageRepository.save(assistantMessage);
            history.add(assistantMessage);
            conversation.rename(conversationTitleService.generateTitle(conversation, plainAiMessages(history)));
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, requestMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), false, false, ChatMessageStatus.COMPLETED, null);
            finishTrace(runId, "SUCCESS", null);
            chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getId(), assistantMessage.getContent(), conversation.getTitle());
            return;
        }
        Optional<SubQuestionIntentDecision> mcpDisabledDecision = firstDecisionWithAction(
            subQuestionDecisions,
            ConversationIntentAction.MCP_DISABLED
        );
        if (mcpDisabledDecision.isPresent()) {
            intentDecision = mcpDisabledDecision.get().intentDecision();
            ChatMessage assistantMessage = ChatMessage.assistantMessage(
                command.conversationId(),
                "你当前未连接 MCP。请在输入框上方开启“连接 MCP”并至少选择一个 MCP 后重试",
                ChatMessageStatus.COMPLETED,
                null,
                null,
                null
            ).attachRun(runId);
            chatMessageRepository.save(assistantMessage);
            history.add(assistantMessage);
            conversation.rename(conversationTitleService.generateTitle(conversation, plainAiMessages(history)));
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, requestMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), false, false, ChatMessageStatus.COMPLETED, null);
            finishTrace(runId, "SUCCESS", null);
            chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getId(), assistantMessage.getContent(), conversation.getTitle());
            return;
        }
        Optional<SubQuestionIntentDecision> unavailableMcpDecision = firstUnavailableMcpDecision(
            subQuestionDecisions,
            command.mcpCodes()
        );
        if (unavailableMcpDecision.isPresent()) {
            intentDecision = unavailableMcpDecision.get().intentDecision();
            ChatMessage assistantMessage = ChatMessage.assistantMessage(
                command.conversationId(),
                "当前会话未连接该 MCP，请在输入框上方先启用对应 MCP 后重试",
                ChatMessageStatus.COMPLETED,
                null,
                null,
                null
            ).attachRun(runId);
            chatMessageRepository.save(assistantMessage);
            history.add(assistantMessage);
            conversation.rename(conversationTitleService.generateTitle(conversation, plainAiMessages(history)));
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, requestMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), false, false, ChatMessageStatus.COMPLETED, null);
            finishTrace(runId, "SUCCESS", null);
            chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getId(), assistantMessage.getContent(), conversation.getTitle());
            return;
        }
        long nextSequenceNo = executeMcpDecisions(subQuestionDecisions, command, runId, history, 1L);
        List<String> searchQuestions = searchQuestions(subQuestionDecisions);
        if (CollUtil.isNotEmpty(searchQuestions)) {
            searchReferences = executeSearchQuestions(
                searchQuestions,
                runId,
                command.conversationId(),
                nextSequenceNo
            );
            searchReferenceCollector.collect(runId, requestMessage.getId(), command.conversationId(), searchReferences);
            documentArtifactService.createDocxArtifact(runId, requestMessage.getId(), command.conversationId(), "搜索结果整理中");
        }
        StringBuilder builder = new StringBuilder();
        StringBuilder thinkingBuilder = new StringBuilder();
        AtomicReference<LocalDateTime> thinkingStartedAt = new AtomicReference<>();
        final Throwable[] streamError = new Throwable[1];
        final String[] selectedProvider = new String[1];
        final String[] selectedModel = new String[1];
        List<ChatMessage> aiHistory = buildAiHistory(
            conversationSummaryService.buildModelHistory(command.conversationId(), modelVisibleAiMessages(history)),
            intentDecision,
            command.conversationId(),
            command.skillCodes(),
            command.expertCode(),
            searchReferences,
            buildGovernanceAgentContext(conversation, rewrittenQuestion),
            command.deepThinking(),
            command.planMode()
        );
        log.info(
            "模型调用: 深度思考={}, 技能数={}, 专家={}, 历史条数={}, 搜索引用数={}",
            command.deepThinking(),
            sizeOf(command.skillCodes()),
            command.expertCode(),
            aiHistory.size(),
            searchReferences.size()
        );
        logPromptContext(aiHistory);
        tokenCounterService.estimateConversationTokens(aiHistory);
        final Long activeRunId = runId;
        try {
            runAiToolAwareLoop(
                command,
                runId,
                aiHistory,
                builder,
                thinkingBuilder,
                thinkingStartedAt,
                streamError,
                selectedProvider,
                selectedModel,
                activeRunId,
                List.of(),
                shouldExposeModelTools(searchReferences, command.skillCodes(), rewrittenQuestion)
            );
        } catch (RuntimeException exception) {
            if (chatRuntimeGuardService.isCancelled(command.conversationId(), activeRunId)) {
                ChatMessage cancelledMessage = ChatMessage.assistantMessage(
                    command.conversationId(),
                    StrUtil.blankToDefault(builder.toString(), "已取消"),
                    ChatMessageStatus.CANCELLED,
                    selectedProvider[0],
                    selectedModel[0],
                    null
                ).attachRun(runId);
                applyThinkingRuntimeState(cancelledMessage, thinkingStartedAt.get(), thinkingBuilder.toString());
                chatMessageRepository.save(cancelledMessage);
                conversation.touch();
                conversation.recordLastRunId(runId);
                chatConversationRepository.save(conversation);
                recordExecutionOutcome(conversation, requestMessage.getId(), cancelledMessage.getId(), intentDecision.intentCode(), intentDecision.action() == ConversationIntentAction.SEARCH, true, ChatMessageStatus.CANCELLED, null);
                finishTrace(runId, "CANCELLED", null);
                return;
            }
            throw exception;
        }
        if (chatRuntimeGuardService.isCancelled(command.conversationId(), activeRunId)) {
            ChatMessage cancelledMessage = ChatMessage.assistantMessage(
                command.conversationId(),
                StrUtil.blankToDefault(builder.toString(), "已取消"),
                ChatMessageStatus.CANCELLED,
                selectedProvider[0],
                selectedModel[0],
                null
            ).attachRun(runId);
            applyThinkingRuntimeState(cancelledMessage, thinkingStartedAt.get(), thinkingBuilder.toString());
            chatMessageRepository.save(cancelledMessage);
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, requestMessage.getId(), cancelledMessage.getId(), intentDecision.intentCode(), intentDecision.action() == ConversationIntentAction.SEARCH, true, ChatMessageStatus.CANCELLED, null);
            finishTrace(runId, "CANCELLED", null);
            return;
        }
        if (streamError[0] != null) {

            ChatMessage failedMessage = ChatMessage.assistantMessage(
                command.conversationId(),
                StrUtil.blankToDefault(llmResponseCleaner.clean(builder.toString()), ErrorMessageCatalog.CHAT_AI_RESPONSE_FAILED),
                ChatMessageStatus.FAILED,
                selectedProvider[0],
                selectedModel[0],
                streamError[0].getMessage()

            ).attachRun(runId);
            applyThinkingRuntimeState(failedMessage, thinkingStartedAt.get(), thinkingBuilder.toString());
            chatMessageRepository.save(failedMessage);
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            chatStreamPublisher.publishError(command.conversationId(), streamError[0].getMessage());
            recordExecutionOutcome(conversation, requestMessage.getId(), failedMessage.getId(), intentDecision.intentCode(), intentDecision.action() == ConversationIntentAction.SEARCH, false, ChatMessageStatus.FAILED, streamError[0].getMessage());
            finishTrace(runId, "ERROR", streamError[0].getMessage());
            return;
        }
        ChatMessage assistantMessage = ChatMessage.assistantMessage(
            command.conversationId(),
            StrUtil.blankToDefault(llmResponseCleaner.clean(builder.toString()), ""),
            ChatMessageStatus.COMPLETED,
            selectedProvider[0],
            selectedModel[0],
            null

        ).attachRun(runId);
        applyThinkingRuntimeState(assistantMessage, thinkingStartedAt.get(), thinkingBuilder.toString());
        chatMessageRepository.save(assistantMessage);
        history.add(assistantMessage);
        conversation.rename(conversationTitleService.generateTitle(conversation, plainAiMessages(history)));
        conversationSummaryService.refreshSummaryIfNeeded(conversation, plainAiMessages(history));
        conversation.touch();
        conversation.recordLastRunId(runId);
        chatConversationRepository.save(conversation);
        recordExecutionOutcome(conversation, requestMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), intentDecision.action() == ConversationIntentAction.SEARCH, true, ChatMessageStatus.COMPLETED, null);
        finishTrace(runId, "SUCCESS", null);
        chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getId(), assistantMessage.getContent(), conversation.getTitle());
    }

    /**
     * 在消息列表中从后往前查找最后一条指定角色的消息。
     * @param messages 消息历史。
     * @param role 目标角色。
     * @return 最后命中的消息。
     */
    private Optional<ChatMessage> findLastMessageByRole(List<ChatMessage> messages, ChatMessageRole role) {
        if (messages == null || messages.isEmpty() || role == null) {
            return Optional.empty();
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            ChatMessage message = messages.get(index);
            if (message.getRole() == role) {
                return Optional.of(message);
            }
        }
        return Optional.empty();
    }

    /**
     * 解析重新生成所需的上一轮 run 标识，优先使用会话上次成功收口的 run。
     * @param conversation 当前会话。
     * @return 可复用的 run 标识，缺失时返回 null。
     */
    private Long resolveRegenerateSourceRunId(ChatConversation conversation) {
        if (conversation.getLastRunId() != null) {
            return conversation.getLastRunId();
        }
        return chatExecutionRunRepository.findByConversationId(conversation.getId()).stream()
            .filter(this::isReusableRun)
            .max(Comparator.comparing(ChatExecutionRun::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChatExecutionRun::getId, Comparator.nullsLast(Comparator.naturalOrder())))
            // runtime_context 步骤以 chat_execution_run.id 为外键，不能再回退到兼容 taskId。
            .map(ChatExecutionRun::getId)
            .orElse(null);
    }

    /**
     * 重新生成时读取上一轮 run 绑定的技能编码。
     * @param sourceRunId 上一轮 run 标识。
     * @return 技能编码列表。
     */
    private List<String> loadSelectedSkillCodes(Long sourceRunId) {
        if (sourceRunId == null) {
            return List.of();
        }
        return ChatRunContextStepSupport.parseContext(chatExecutionStepRepository.findByRunId(sourceRunId)).skillCodes();
    }

    /**
     * 重新生成时读取上一轮 run 绑定的 MCP 编码。
     * @param sourceRunId 上一轮 run 标识。
     * @return MCP 编码列表。
     */
    private List<String> loadSelectedMcpCodes(Long sourceRunId) {
        if (sourceRunId == null) {
            return List.of();
        }
        return ChatRunContextStepSupport.parseContext(chatExecutionStepRepository.findByRunId(sourceRunId)).mcpCodes();
    }

    /**
     * 重新生成时读取上一轮 run 绑定的专家编码。
     * @param sourceRunId 上一轮 run 标识。
     * @return 专家编码，缺失时返回 null。
     */
    private String loadSelectedExpertCode(Long sourceRunId) {
        if (sourceRunId == null) {
            return null;
        }
        // 专家选择随 run 写入隐藏 runtime_context 步骤；旧 task_expert 表已删除，缺失时不再回退。
        return ChatRunContextStepSupport.parseContext(chatExecutionStepRepository.findByRunId(sourceRunId)).expertCode();
    }

    /**
     * 将重新生成使用的上下文显式绑定到新 run，确保右侧回放能读取到同一批能力配置。
     * @param runId 新的执行标识。
     * @param selectedMcpCodes 绑定的 MCP 编码。
     * @param selectedSkillCodes 绑定的技能编码。
     * @param selectedExpertCode 绑定的专家编码。
     */
    private void bindSelectedContextToRun(Long runId, List<String> selectedMcpCodes, List<String> selectedSkillCodes, String selectedExpertCode) {
        saveRunCapabilityContext(runId, selectedMcpCodes, selectedSkillCodes, selectedExpertCode);
    }

    /**
     * 将本轮能力选择写入 chat_execution_step 隐藏上下文步骤，替代旧 task_skill/task_mcp 表。
     * @param runId 运行标识。
     * @param selectedMcpCodes MCP 编码。
     * @param selectedSkillCodes 技能编码。
     * @param selectedExpertCode 专家编码。
     */
    private void saveRunCapabilityContext(
        Long runId,
        List<String> selectedMcpCodes,
        List<String> selectedSkillCodes,
        String selectedExpertCode
    ) {
        if (runId == null) {
            return;
        }
        List<ChatExecutionStep> existingSteps = chatExecutionStepRepository.findByRunId(runId);
        ChatExecutionStep existingContextStep = ChatRunContextStepSupport.findContextStep(existingSteps).orElse(null);
        chatExecutionStepRepository.save(ChatRunContextStepSupport.buildStep(
            runId,
            selectedMcpCodes,
            selectedSkillCodes,
            selectedExpertCode,
            existingContextStep
        ));
    }

    /**
     * 只有非运行中的历史 run 才适合作为重新生成的上下文来源。
     * @param run 执行记录。
     * @return 是否可复用。
     */
    private boolean isReusableRun(ChatExecutionRun run) {
        if (run == null || StrUtil.isBlank(run.getStatus())) {
            return false;
        }
        return !StrUtil.equalsAnyIgnoreCase(run.getStatus(), "RUNNING", "WAITING", "ACQUIRED");
    }

    /**
     * 处理用户发送消息主流程。
     * 关键约束：
     * 1. 必须先校验会话归属，再执行后续流程，避免越权访问
     * 2. 所有分支都要绑定 runId，并写入执行结果与 Trace 终态，避免运行态悬挂
     * 3. 命中澄清、直答、MCP 限制等短路分支时立即返回，不进入模型流式调用
     *
     * @param command 发送请求，包含消息内容、附件、MCP 选择及技能上下文
     * @param userId 当前用户标识，用于会话权限校验
     */
    public void sendMessage(SendChatMessageCommand command, Long userId) {
        // 步骤 1：先处理输入校验和本地临时模式分流，本地模式不进入云端落库链路。
        if (StrUtil.isBlank(command.content())) {
            throw new IllegalArgumentException(ErrorMessageCatalog.CHAT_MESSAGE_CONTENT_REQUIRED);
        }
        if (command.localOnly()) {
            sendLocalOnlyMessage(command, userId);
            return;
        }
        // 步骤 2：校验会话归属和运行门控，并把本轮能力选择写入 run 上下文。
        Long runId = currentRunId(command.conversationId());
        ChatConversation conversation = chatConversationRepository.requireById(command.conversationId());
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        chatRuntimeGuardService.ensureAccepted(command.conversationId());
        List<String> selectedSkillCodes = ChatCapabilityMentionSupport.mergeSkillCodes(command.skillCodes(), command.content());
        String plainQuestion = ChatCapabilityMentionSupport.stripSelectedSkillMentions(command.content(), selectedSkillCodes);
        // 步骤 3：纯问候不需要历史、附件、改写、意图识别和模型工具循环，先落库用户输入后直接确定性收口。
        if (shouldReplyWithQuickGreeting(command, plainQuestion, selectedSkillCodes)) {
            ChatMessage userMessage = ChatMessage.userMessage(command.conversationId(), plainQuestion).attachRun(runId);
            chatMessageRepository.save(userMessage);
            chatStreamPublisher.publishUserMessage(command.conversationId(), plainQuestion);
            completeQuickGreetingReply(conversation, userMessage, command, runId);
            return;
        }
        // 步骤 4：非问候请求需要保存能力上下文，并读取历史与附件后进入完整编排链路。
        saveRunCapabilityContext(runId, command.mcpCodes(), selectedSkillCodes, command.expertCode());
        List<ChatMessage> history = new ArrayList<>(chatMessageRepository.findByConversationId(command.conversationId()));
        List<ChatAttachment> validatedAttachments = chatAttachmentService.requireOwnedAttachments(
            command.attachmentIds(),
            command.conversationId(),
            userId
        );
        // 步骤 5：写入用户消息和附件绑定，再通过 SSE 立即回传用户输入，保证前端历史先落地。
        ChatMessage userMessage = ChatMessage.userMessage(
            command.conversationId(),
            ChatCapabilityMentionSupport.formatContentWithSkillMentions(selectedSkillCodes, plainQuestion)
        ).attachRun(runId);
        chatMessageRepository.save(userMessage);
        for (ChatAttachment attachment : validatedAttachments) {
            chatAttachmentService.bindToMessage(attachment, command.conversationId(), userMessage.getId(), runId);
        }
        chatStreamPublisher.publishUserMessage(command.conversationId(), plainQuestion);
        history.add(userMessage);
        // 步骤 6：明确的定时任务创建请求在会话内直接落库并回复摘要，不跳转到自动化页面确认。
        Optional<AutomationTaskChatCreationResult> automationCreationResult = tryCreateAutomationTaskFromChat(
            conversation,
            plainQuestion,
            userId,
            runId
        );
        if (automationCreationResult.isPresent()) {
            ChatMessage assistantMessage = ChatMessage.assistantMessage(
                command.conversationId(),
                automationCreationResult.get().assistantContent(),
                ChatMessageStatus.COMPLETED,
                null,
                null,
                null
            ).attachRun(runId);
            chatMessageRepository.save(assistantMessage);
            history.add(assistantMessage);
            conversation.rename(conversationTitleService.generateTitle(conversation, plainAiMessages(history)));
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, userMessage.getId(), assistantMessage.getId(), "automation.create", false, false, ChatMessageStatus.COMPLETED, null);
            finishTrace(runId, "SUCCESS", null);
            chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getId(), assistantMessage.getContent(), conversation.getTitle());
            return;
        }
        // 步骤 7：改写问题并执行意图分流，多子问题会在后续分别触发搜索或 MCP。
        ConversationRewriteResult rewriteResult = conversationRewriteService.rewriteResult(
            plainUserContents(history),
            plainQuestion
        );
        List<SearchReferenceCandidate> searchReferences = List.of();
        String rewrittenQuestion = rewriteResult.rewrite();
        boolean mcpEnabled = command.mcpCodes() != null && !command.mcpCodes().isEmpty();
        List<SubQuestionIntentDecision> subQuestionDecisions = normalizeSelectedSkillShortQuestionDecisions(
            suppressAutomaticSearchDecisions(
                suppressCodeArtifactFollowUpSearchDecisions(
                    resolveSubQuestionDecisions(rewriteResult, mcpEnabled),
                    plainQuestion,
                    history
                )
            ),
            selectedSkillCodes
        );
        ConversationIntentDecision intentDecision = primaryIntentDecision(subQuestionDecisions);
        logChatDecision("发送消息", command, runId, intentDecision, rewriteResult);
        // 步骤 8：优先处理无需进入模型的短路分支，包括澄清、直答和 MCP 未启用提示。
        // 已选技能短指代应继续进入模型，由技能上下文解释当前引用的技能或追问执行目标。
        Optional<SubQuestionIntentDecision> clarifyDecision = firstDecisionWithAction(
            subQuestionDecisions,
            ConversationIntentAction.CLARIFY
        );
        if (clarifyDecision.isPresent() && selectedSkillCodes.isEmpty()) {
            intentDecision = clarifyDecision.get().intentDecision();
            ChatMessage assistantMessage = ChatMessage.assistantMessage(
                command.conversationId(),
                intentDecision.reply(),
                ChatMessageStatus.COMPLETED,
                null,
                null,
                null
            ).attachRun(runId);
            chatMessageRepository.save(assistantMessage);
            history.add(assistantMessage);
            conversation.rename(conversationTitleService.generateTitle(conversation, plainAiMessages(history)));
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, userMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), false, false, ChatMessageStatus.COMPLETED, null);
            finishTrace(runId, "SUCCESS", null);
            chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getId(), assistantMessage.getContent(), conversation.getTitle());
            return;
        }
        if (isSingleDirectReply(subQuestionDecisions) && selectedSkillCodes.isEmpty()) {
            ChatMessage assistantMessage = ChatMessage.assistantMessage(
                command.conversationId(),
                intentDecision.reply(),
                ChatMessageStatus.COMPLETED,
                null,
                null,
                null
            ).attachRun(runId);
            chatMessageRepository.save(assistantMessage);
            history.add(assistantMessage);
            conversation.rename(conversationTitleService.generateTitle(conversation, plainAiMessages(history)));
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, userMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), false, false, ChatMessageStatus.COMPLETED, null);
            finishTrace(runId, "SUCCESS", null);
            chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getId(), assistantMessage.getContent(), conversation.getTitle());
            return;
        }
        Optional<SubQuestionIntentDecision> mcpDisabledDecision = firstDecisionWithAction(
            subQuestionDecisions,
            ConversationIntentAction.MCP_DISABLED
        );
        if (mcpDisabledDecision.isPresent()) {
            intentDecision = mcpDisabledDecision.get().intentDecision();
            ChatMessage assistantMessage = ChatMessage.assistantMessage(
                command.conversationId(),
                "你当前未连接 MCP。请在输入框上方开启“连接 MCP”并至少选择一个 MCP 后重试",
                ChatMessageStatus.COMPLETED,
                null,
                null,
                null
            ).attachRun(runId);
            chatMessageRepository.save(assistantMessage);
            history.add(assistantMessage);
            conversation.rename(conversationTitleService.generateTitle(conversation, plainAiMessages(history)));
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, userMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), false, false, ChatMessageStatus.COMPLETED, null);
            finishTrace(runId, "SUCCESS", null);
            chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getId(), assistantMessage.getContent(), conversation.getTitle());
            return;
        }
        Optional<SubQuestionIntentDecision> unavailableMcpDecision = firstUnavailableMcpDecision(
            subQuestionDecisions,
            command.mcpCodes()
        );
        if (unavailableMcpDecision.isPresent()) {
            intentDecision = unavailableMcpDecision.get().intentDecision();
            ChatMessage assistantMessage = ChatMessage.assistantMessage(
                command.conversationId(),
                "当前会话未连接该 MCP，请在输入框上方先启用对应 MCP 后重试",
                ChatMessageStatus.COMPLETED,
                null,
                null,
                null
            ).attachRun(runId);
            chatMessageRepository.save(assistantMessage);
            history.add(assistantMessage);
            conversation.rename(conversationTitleService.generateTitle(conversation, plainAiMessages(history)));
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, userMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), false, false, ChatMessageStatus.COMPLETED, null);
            finishTrace(runId, "SUCCESS", null);
            chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getId(), assistantMessage.getContent(), conversation.getTitle());
            return;
        }
        // 步骤 9：执行可用的 MCP 和搜索分支，搜索引用会同时写入引用表并生成文档产物占位。
        long nextSequenceNo = executeMcpDecisions(subQuestionDecisions, command, runId, history, 1L);
        List<String> searchQuestions = searchQuestions(subQuestionDecisions);
        if (CollUtil.isNotEmpty(searchQuestions)) {
            searchReferences = executeSearchQuestions(
                searchQuestions,
                runId,
                command.conversationId(),
                nextSequenceNo
            );
            searchReferenceCollector.collect(runId, userMessage.getId(), command.conversationId(), searchReferences);
            documentArtifactService.createDocxArtifact(runId, userMessage.getId(), command.conversationId(), "搜索结果整理中");
        }
        // 步骤 10：组装模型上下文，包含摘要裁剪后的历史、能力上下文、专家提示和搜索引用。
        StringBuilder builder = new StringBuilder();
        StringBuilder thinkingBuilder = new StringBuilder();
        AtomicReference<LocalDateTime> thinkingStartedAt = new AtomicReference<>();
        final Throwable[] streamError = new Throwable[1];
        final String[] selectedProvider = new String[1];
        final String[] selectedModel = new String[1];
        List<ChatMessage> aiHistory = buildAiHistory(
            conversationSummaryService.buildModelHistory(command.conversationId(), modelVisibleAiMessages(history)),
            intentDecision,
            command.conversationId(),
            selectedSkillCodes,
            command.expertCode(),
            searchReferences,
            buildGovernanceAgentContext(conversation, rewrittenQuestion),
            command.deepThinking(),
            command.planMode()
        );
        log.info(
            "模型调用: 深度思考={}, 技能数={}, 专家={}, 历史条数={}, 搜索引用数={}",
            command.deepThinking(),
            sizeOf(selectedSkillCodes),
            command.expertCode(),
            aiHistory.size(),
            searchReferences.size()
        );
        logPromptContext(aiHistory);
        tokenCounterService.estimateConversationTokens(aiHistory);
        final Long activeRunId = runId;
        try {
            // 步骤 11：进入支持工具调用的模型循环，流式内容、thinking 与工具事件都会写入缓冲区或 SSE。
            runAiToolAwareLoop(
                command,
                runId,
                aiHistory,
                builder,
                thinkingBuilder,
                thinkingStartedAt,
                streamError,
                selectedProvider,
                selectedModel,
                activeRunId,
                validatedAttachments,
                shouldExposeModelTools(searchReferences, selectedSkillCodes, rewrittenQuestion)
            );
        } catch (RuntimeException exception) {
            // 步骤 12：模型循环中被用户取消时记录取消态，否则继续抛出交由外层异常处理。
            if (chatRuntimeGuardService.isCancelled(command.conversationId(), activeRunId)) {
                ChatMessage cancelledMessage = ChatMessage.assistantMessage(
                    command.conversationId(),
                    StrUtil.blankToDefault(builder.toString(), "已取消"),
                    ChatMessageStatus.CANCELLED,
                    selectedProvider[0],
                    selectedModel[0],
                    null
                ).attachRun(runId);
                applyThinkingRuntimeState(cancelledMessage, thinkingStartedAt.get(), thinkingBuilder.toString());
                chatMessageRepository.save(cancelledMessage);
                conversation.touch();
                conversation.recordLastRunId(runId);
                chatConversationRepository.save(conversation);
                recordExecutionOutcome(conversation, userMessage.getId(), cancelledMessage.getId(), intentDecision.intentCode(), intentDecision.action() == ConversationIntentAction.SEARCH, true, ChatMessageStatus.CANCELLED, null);
                finishTrace(runId, "CANCELLED", null);
                return;
            }
            throw exception;
        }
        // 步骤 13：模型循环结束后再次检查取消状态，覆盖流结束与取消请求竞态。
        if (chatRuntimeGuardService.isCancelled(command.conversationId(), activeRunId)) {
            ChatMessage cancelledMessage = ChatMessage.assistantMessage(
                command.conversationId(),
                StrUtil.blankToDefault(builder.toString(), "已取消"),
                ChatMessageStatus.CANCELLED,
                selectedProvider[0],
                selectedModel[0],
                null
            ).attachRun(runId);
            applyThinkingRuntimeState(cancelledMessage, thinkingStartedAt.get(), thinkingBuilder.toString());
            chatMessageRepository.save(cancelledMessage);
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, userMessage.getId(), cancelledMessage.getId(), intentDecision.intentCode(), intentDecision.action() == ConversationIntentAction.SEARCH, true, ChatMessageStatus.CANCELLED, null);
            finishTrace(runId, "CANCELLED", null);
            return;
        }
        // 步骤 14：流式异常时保留已经生成的部分回答，并把错误状态写入消息、run 和 Trace。
        if (streamError[0] != null) {

            ChatMessage failedMessage = ChatMessage.assistantMessage(
                command.conversationId(),
                StrUtil.blankToDefault(llmResponseCleaner.clean(builder.toString()), ErrorMessageCatalog.CHAT_AI_RESPONSE_FAILED),
                ChatMessageStatus.FAILED,
                selectedProvider[0],
                selectedModel[0],
                streamError[0].getMessage()

            ).attachRun(runId);
            applyThinkingRuntimeState(failedMessage, thinkingStartedAt.get(), thinkingBuilder.toString());
            chatMessageRepository.save(failedMessage);
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            chatStreamPublisher.publishError(command.conversationId(), streamError[0].getMessage());
            recordExecutionOutcome(conversation, userMessage.getId(), failedMessage.getId(), intentDecision.intentCode(), intentDecision.action() == ConversationIntentAction.SEARCH, false, ChatMessageStatus.FAILED, streamError[0].getMessage());
            finishTrace(runId, "ERROR", streamError[0].getMessage());
            return;
        }
        // 步骤 15：正常完成时保存助手消息、刷新标题和摘要，最后发布完成事件给前端。
        ChatMessage assistantMessage = ChatMessage.assistantMessage(
            command.conversationId(),
            StrUtil.blankToDefault(llmResponseCleaner.clean(builder.toString()), ""),
            ChatMessageStatus.COMPLETED,
            selectedProvider[0],
            selectedModel[0],
            null

        ).attachRun(runId);
        applyThinkingRuntimeState(assistantMessage, thinkingStartedAt.get(), thinkingBuilder.toString());
        chatMessageRepository.save(assistantMessage);
        history.add(assistantMessage);
        conversation.rename(conversationTitleService.generateTitle(conversation, plainAiMessages(history)));
        conversationSummaryService.refreshSummaryIfNeeded(conversation, plainAiMessages(history));
        conversation.touch();
        conversation.recordLastRunId(runId);
        chatConversationRepository.save(conversation);
        recordExecutionOutcome(conversation, userMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), intentDecision.action() == ConversationIntentAction.SEARCH, true, ChatMessageStatus.COMPLETED, null);
        finishTrace(runId, "SUCCESS", null);
        extractGovernanceMemoryCandidates(conversation, userMessage, assistantMessage);
        chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getId(), assistantMessage.getContent(), conversation.getTitle());
    }

    /**
     * 尝试在聊天会话内直接创建自动化任务；未启用服务或未命中意图时返回空。
     * 兼容约束：部分旧单测未显式注入该新增依赖，因此空依赖必须跳过而不能影响普通聊天。
     * @param conversation 当前会话，调用前已完成用户归属校验。
     * @param plainQuestion 用户消息正文。
     * @param userId 当前用户标识。
     * @param runId 当前运行标识。
     * @return 自动化任务创建结果。
     */
    private Optional<AutomationTaskChatCreationResult> tryCreateAutomationTaskFromChat(
        ChatConversation conversation,
        String plainQuestion,
        Long userId,
        Long runId
    ) {
        if (automationTaskChatCreationService == null) {
            return Optional.empty();
        }
        return automationTaskChatCreationService.tryCreateFromChatMessage(conversation, plainQuestion, userId, runId);
    }

    /**
     * 判断当前消息是否可走确定性问候回复。
     * 业务约束：只有纯文本问候且没有附件、技能、专家、深度思考、目标模式或仓库路径时才短路，
     * 避免把“你好，帮我看这个文件”这类真实任务误判为简单问候。MCP 选择属于输入框粘性配置，
     * 纯问候没有工具执行意图，因此不应因为已选 MCP 被拖入改写、意图识别和模型工具链路。
     * @param command 当前发送命令。
     * @param plainQuestion 已去掉技能 mention 的用户问题。
     * @param selectedSkillCodes 当前消息显式选择或 mention 的技能。
     * @return true 表示可直接返回内置问候。
     */
    private boolean shouldReplyWithQuickGreeting(
        SendChatMessageCommand command,
        String plainQuestion,
        List<String> selectedSkillCodes
    ) {
        return !command.deepThinking()
            && !command.planMode()
            && CollUtil.isEmpty(selectedSkillCodes)
            && CollUtil.isEmpty(command.attachmentIds())
            && CollUtil.isEmpty(command.skillPaths())
            && StrUtil.isBlank(command.expertCode())
            && StrUtil.isBlank(command.repositoryPath())
            && QUICK_GREETING_TEXTS.contains(normalizeQuickGreetingText(plainQuestion));
    }

    /**
     * 将问候文本归一为稳定匹配键，兼容中英文大小写、空白和常见标点。
     * @param question 用户输入。
     * @return 用于集合匹配的问候键。
     */
    private String normalizeQuickGreetingText(String question) {
        return StrUtil.blankToDefault(question, "")
            .replaceAll("[\\p{Punct}\\s，。？！、：；“”‘’（）【】《》]+", "")
            .toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 完成云端会话的确定性问候回复，并按普通成功链路写入消息、run、trace 与 SSE 完成事件。
     * @param conversation 当前会话。
     * @param userMessage 已保存的用户消息。
     * @param command 当前发送命令。
     * @param runId 当前运行标识。
     */
    private void completeQuickGreetingReply(
        ChatConversation conversation,
        ChatMessage userMessage,
        SendChatMessageCommand command,
        Long runId
    ) {
        ChatMessage assistantMessage = ChatMessage.assistantMessage(
            command.conversationId(),
            QUICK_GREETING_REPLY,
            ChatMessageStatus.COMPLETED,
            null,
            null,
            null
        ).attachRun(runId);
        chatMessageRepository.save(assistantMessage);
        conversation.touch();
        conversation.recordLastRunId(runId);
        chatConversationRepository.save(conversation);
        recordExecutionOutcome(conversation, userMessage.getId(), assistantMessage.getId(), "sys-welcome", false, false, ChatMessageStatus.COMPLETED, null);
        finishTrace(runId, "SUCCESS", null);
        chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getId(), assistantMessage.getContent(), conversation.getTitle());
    }

    /**
     * 执行本地临时聊天链路，只向 SSE 推送运行结果，不读写云端会话、消息、run 或任务相关表。
     * 关键约束：本地历史的 source of truth 是客户端本地快照，后端仅用临时 conversationId 做流式路由。
     *
     * @param command 本地运行命令。
     * @param userId 当前用户标识，预留给后续本地权限约束，当前不落库。
     */
    private void sendLocalOnlyMessage(SendChatMessageCommand command, Long userId) {
        // 步骤 1：初始化本地临时 run 和内存历史，仅用于 SSE 路由和本轮模型输入。
        Long runId = currentRunId(command.conversationId());
        chatRuntimeGuardService.ensureAccepted(command.conversationId());
        List<String> selectedSkillCodes = ChatCapabilityMentionSupport.mergeSkillCodes(command.skillCodes(), command.content());
        String plainQuestion = ChatCapabilityMentionSupport.stripSelectedSkillMentions(command.content(), selectedSkillCodes);
        List<ChatMessage> history = new ArrayList<>();
        ChatMessage userMessage = ChatMessage.userMessage(command.conversationId(), plainQuestion).attachRun(runId);
        history.add(userMessage);
        chatStreamPublisher.publishUserMessage(command.conversationId(), userMessage.getContent());
        // 步骤 2：本地模式允许澄清、直答和 MCP 未启用提示短路，但技能说明不得静态伪造成执行结果。
        if (shouldReplyWithQuickGreeting(command, plainQuestion, selectedSkillCodes)) {
            chatStreamPublisher.publishAssistantCompleted(
                command.conversationId(),
                QUICK_GREETING_REPLY,
                resolveLocalConversationTitle(command)
            );
            return;
        }
        // 步骤 3：改写并路由本地问题，但不写入云端消息、run、Trace 或任务表。
        ConversationRewriteResult rewriteResult = conversationRewriteService.rewriteResult(
            List.of(plainQuestion),
            plainQuestion
        );
        String rewrittenQuestion = rewriteResult.rewrite();
        boolean mcpEnabled = command.mcpCodes() != null && !command.mcpCodes().isEmpty();
        ConversationIntentDecision intentDecision = normalizeSelectedSkillShortQuestionDecision(
            rewrittenQuestion,
            conversationIntentService.route(rewrittenQuestion, mcpEnabled),
            selectedSkillCodes
        );
        logChatDecision("本地消息", command, runId, intentDecision, rewriteResult);
        if (intentDecision.action() == ConversationIntentAction.CLARIFY && selectedSkillCodes.isEmpty()) {
            chatStreamPublisher.publishAssistantCompleted(
                command.conversationId(),
                intentDecision.reply(),
                resolveLocalConversationTitle(command)
            );
            return;
        }
        if (intentDecision.action() == ConversationIntentAction.DIRECT && StrUtil.isNotBlank(intentDecision.reply()) && selectedSkillCodes.isEmpty()) {
            chatStreamPublisher.publishAssistantCompleted(
                command.conversationId(),
                intentDecision.reply(),
                resolveLocalConversationTitle(command)
            );
            return;
        }
        if (intentDecision.action() == ConversationIntentAction.MCP_DISABLED) {
            chatStreamPublisher.publishAssistantCompleted(
                command.conversationId(),
                "你当前未连接 MCP。请在输入框上方开启“连接 MCP”并至少选择一个 MCP 后重试",
                resolveLocalConversationTitle(command)
            );
            return;
        }
        // 步骤 4：组装本地模型上下文并进入工具感知模型循环，结果只通过 SSE 返回。
        StringBuilder builder = new StringBuilder();
        StringBuilder thinkingBuilder = new StringBuilder();
        AtomicReference<LocalDateTime> thinkingStartedAt = new AtomicReference<>();
        final Throwable[] streamError = new Throwable[1];
        final String[] selectedProvider = new String[1];
        final String[] selectedModel = new String[1];
        List<ChatMessage> aiHistory = buildAiHistory(
            history,
            intentDecision,
            command.conversationId(),
            selectedSkillCodes,
            command.expertCode(),
            List.of(),
            "",
            command.deepThinking(),
            command.planMode()
        );
        log.info(
            "模型调用: 深度思考={}, 技能数={}, 专家={}, 历史条数={}, 搜索引用数={}",
            command.deepThinking(),
            sizeOf(selectedSkillCodes),
            command.expertCode(),
            aiHistory.size(),
            0
        );
        tokenCounterService.estimateConversationTokens(aiHistory);
        try {
            runAiToolAwareLoop(
                command,
                runId,
                aiHistory,
                builder,
                thinkingBuilder,
                thinkingStartedAt,
                streamError,
                selectedProvider,
                selectedModel,
                runId,
                List.of(),
                shouldExposeModelTools(List.of(), selectedSkillCodes, rewrittenQuestion)
            );
        } catch (RuntimeException exception) {
            // 步骤 5：取消时直接结束本地临时链路，非取消异常继续抛给外层统一处理。
            if (chatRuntimeGuardService.isCancelled(command.conversationId(), runId)) {
                return;
            }
            throw exception;
        }
        // 步骤 6：根据取消、流错误或正常完成三种终态向前端发布最终事件。
        if (chatRuntimeGuardService.isCancelled(command.conversationId(), runId)) {
            return;
        }
        if (streamError[0] != null) {
            chatStreamPublisher.publishError(command.conversationId(), streamError[0].getMessage());
            return;
        }
        String assistantContent = StrUtil.blankToDefault(llmResponseCleaner.clean(builder.toString()), "");
        chatStreamPublisher.publishAssistantCompleted(
            command.conversationId(),
            assistantContent,
            resolveLocalConversationTitle(command)
        );
    }

    /**
     * 生成本地临时会话标题；不依赖云端会话实体，避免为了标题创建数据库记录。
     * @param command 本地运行命令。
     * @return 本地显示标题。
     */
    private String resolveLocalConversationTitle(SendChatMessageCommand command) {
        String title = StrUtil.blankToDefault(command.content(), "本地对话").trim();
        return title.length() > 40 ? title.substring(0, 40) : title;
    }

    private Long currentRunId(Long conversationId) {
        return ChatExecutionContext.currentRunId().orElse(conversationId);
    }

    /**
     * 打印聊天主流程分支决策，只输出短字段，方便定位本轮进入哪个能力链路。
     */
    private void logChatDecision(
        String stage,
        SendChatMessageCommand command,
        Long runId,
        ConversationIntentDecision intentDecision,
        ConversationRewriteResult rewriteResult
    ) {
        log.info(
            "聊天决策: 阶段={}, 动作={}, 意图={}, 改写后问题={}, 拆分={}",
            stage,
            intentDecision.action(),
            intentDecision.intentCode(),
            logPreview(rewriteResult.rewrite()),
            rewriteResult.shouldSplit()
        );
    }

    /**
     * 统一处理可空集合长度，避免日志调用点重复空判断。
     */
    private int sizeOf(java.util.Collection<?> values) {
        return values == null ? 0 : values.size();
    }

    /**
     * 构建治理上下文片段，供持久化会话在模型输入前获取仓库规范文件和已生效长期记忆。
     * @param conversation 当前会话。
     * @param rewrittenQuestion 本轮改写后的用户问题。
     * @return 可注入 system prompt 的治理上下文，缺失服务或上下文为空时返回空字符串。
     */
    private String buildGovernanceAgentContext(ChatConversation conversation, String rewrittenQuestion) {
        if (governanceAgentContextService == null || conversation == null) {
            return "";
        }
        // 步骤 1：治理上下文只是模型输入增强，失败时不能阻断主聊天链路。
        try {
            return governanceAgentContextService.buildAgentContext(
                conversation.getCreatedBy(),
                conversation.getWorkspaceId(),
                rewrittenQuestion
            );
        } catch (RuntimeException exception) {
            log.warn("治理上下文构建失败: conversationId={}, message={}", conversation.getId(), exception.getMessage());
            return "";
        }
    }

    /**
     * 助手成功完成后提取长期记忆；提取结果默认 ACTIVE，下一轮相关问题即可参与模型上下文回注。
     * @param conversation 当前会话。
     * @param userMessage 本轮用户消息。
     * @param assistantMessage 本轮助手完成消息。
     */
    private void extractGovernanceMemoryCandidates(
        ChatConversation conversation,
        ChatMessage userMessage,
        ChatMessage assistantMessage
    ) {
        if (governanceAgentContextService == null || conversation == null || userMessage == null || assistantMessage == null) {
            return;
        }
        // 步骤 1：记忆提取属于成功回复后的附加动作，异常只记录日志，不回滚已完成回答。
        try {
            governanceAgentContextService.extractMemoryCandidates(conversation, userMessage, assistantMessage);
        } catch (RuntimeException exception) {
            log.warn(
                "长期记忆提取失败: conversationId={}, userMessageId={}, assistantMessageId={}, message={}",
                conversation.getId(),
                userMessage.getId(),
                assistantMessage.getId(),
                exception.getMessage()
            );
        }
    }

    /**
     * 保存确定性助手回复并完成 run/trace 收口，用于后端已经能确定不该调用模型或工具的分支。
     */
    private void completeDeterministicAssistantReply(
        ChatConversation conversation,
        List<ChatMessage> history,
        Long requestMessageId,
        String reply,
        String intentCode
    ) {
        Long runId = currentRunId(conversation.getId());
        ChatMessage assistantMessage = ChatMessage.assistantMessage(
            conversation.getId(),
            reply,
            ChatMessageStatus.COMPLETED,
            null,
            null,
            null
        ).attachRun(runId);
        chatMessageRepository.save(assistantMessage);
        history.add(assistantMessage);
        conversation.rename(conversationTitleService.generateTitle(conversation, plainAiMessages(history)));
        conversation.touch();
        conversation.recordLastRunId(runId);
        chatConversationRepository.save(conversation);
        recordExecutionOutcome(conversation, requestMessageId, assistantMessage.getId(), intentCode, false, false, ChatMessageStatus.COMPLETED, null);
        finishTrace(runId, "SUCCESS", null);
        chatStreamPublisher.publishAssistantCompleted(conversation.getId(), assistantMessage.getId(), assistantMessage.getContent(), conversation.getTitle());
    }

    /**
     * 日志文本只保留短预览，避免用户长输入或模型参数撑大单行日志。
     */
    private String logPreview(String text) {
        return StrUtil.maxLength(text, 120);
    }

    /**
     * 提示词日志保留系统规则与技能 front matter 简介，避免完整技能执行文档撑爆日志。
     */
    private String promptContextLogPreview(String systemPrompt) {
        // 步骤 1：没有技能块时直接截断完整提示词，减少日志中的长文本。
        String normalizedPrompt = StrUtil.trimToEmpty(systemPrompt);
        if (StrUtil.isBlank(normalizedPrompt) || !normalizedPrompt.contains("## /")) {
            return StrUtil.maxLength(normalizedPrompt, PROMPT_CONTEXT_LOG_PREVIEW_LENGTH);
        }
        // 步骤 2：逐行扫描技能块，只保留标题和 front matter 简介，避免完整技能文档进入日志。
        StringBuilder previewBuilder = new StringBuilder();
        boolean inSkillBlock = false;
        boolean skillIntroComplete = false;
        int frontMatterFenceCount = 0;
        int fallbackSkillLineCount = 0;
        for (String line : normalizedPrompt.split("\\R", -1)) {
            if (line.startsWith("## /")) {
                inSkillBlock = true;
                skillIntroComplete = false;
                frontMatterFenceCount = 0;
                fallbackSkillLineCount = 0;
                appendPromptLogLine(previewBuilder, line);
                continue;
            }
            if (!inSkillBlock) {
                appendPromptLogLine(previewBuilder, line);
                continue;
            }
            if (skillIntroComplete) {
                continue;
            }
            appendPromptLogLine(previewBuilder, line);
            String trimmedLine = line.trim();
            if ("---".equals(trimmedLine)) {
                frontMatterFenceCount++;
                skillIntroComplete = frontMatterFenceCount >= 2;
            } else if (frontMatterFenceCount == 0 && StrUtil.isNotBlank(trimmedLine)) {
                // 非标准技能文档没有 front matter 时，最多保留几行简介，避免整段说明进入 info 日志。
                fallbackSkillLineCount++;
                skillIntroComplete = fallbackSkillLineCount >= 8;
            }
        }
        // 步骤 3：最终仍按统一长度截断，防止多个技能简介叠加撑大单行日志。
        return StrUtil.maxLength(previewBuilder.toString().trim(), PROMPT_CONTEXT_LOG_PREVIEW_LENGTH);
    }

    private void appendPromptLogLine(StringBuilder builder, String line) {
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(line);
    }

    /**
     * 打印提示词上下文内容，用于调试和追踪模型输入。
     * @param aiHistory 包含系统提示词和历史消息的完整上下文。
     */
    private void logPromptContext(List<ChatMessage> aiHistory) {
        if (aiHistory == null || aiHistory.isEmpty()) {
            log.info("提示词上下文: 空");
            return;
        }
        ChatMessage systemMessage = aiHistory.stream()
            .filter(msg -> msg.getRole() == ChatMessageRole.SYSTEM)
            .findFirst()
            .orElse(null);
        if (systemMessage != null && StrUtil.isNotBlank(systemMessage.getContent())) {
            String systemPrompt = systemMessage.getContent();
            log.info("提示词上下文: 系统提示词长度={}, 预览:\n{}",
                systemPrompt.length(),
                promptContextLogPreview(systemPrompt));
        } else {
            log.info("提示词上下文: 无系统提示词");
        }
    }

    /**
     * 回填本轮 assistant 消息的思考正文与耗时。
     * 只在确实收到 thinking 增量时写入，避免普通消息或空思考内容污染消息表。
     *
     * @param message 需要回填的消息。
     * @param thinkingStartedAt 首次思考增量到达时间。
     * @param thinkingText 思考正文。
     * @return 回填后的消息对象。
     */
    private ChatMessage applyThinkingRuntimeState(ChatMessage message, LocalDateTime thinkingStartedAt, String thinkingText) {
        String cleanedThinking = llmResponseCleaner.clean(StrUtil.blankToDefault(thinkingText, ""));
        String normalizedThinking = StrUtil.isBlank(cleanedThinking) ? null : cleanedThinking;
        if (message == null || thinkingStartedAt == null || StrUtil.isBlank(normalizedThinking)) {
            return message;
        }
        message.restoreRuntimeState(
            message.getRunId(),
            normalizedThinking,
            resolveThinkingDurationSeconds(thinkingStartedAt, normalizedThinking),
            message.getCreatedAt(),
            message.getUpdatedAt()
        );
        return message;
    }

    /**
     * 计算深度思考耗时；只有收到 thinking 增量后才记录，避免无思考内容的消息出现伪耗时。
     * @param thinkingStartedAt 首个 thinking 增量到达时间。
     * @param thinkingContent 已收集的 thinking 内容。
     * @return 思考耗时秒数，缺失时返回 null。
     */
    private Integer resolveThinkingDurationSeconds(LocalDateTime thinkingStartedAt, String thinkingContent) {
        if (thinkingStartedAt == null || StrUtil.isBlank(thinkingContent)) {
            return null;
        }
        long elapsedMillis = Math.max(0L, Duration.between(thinkingStartedAt, LocalDateTime.now()).toMillis());
        long elapsedSeconds = Math.max(1L, (elapsedMillis + 999L) / 1000L);
        return Math.toIntExact(elapsedSeconds);
    }

    /**
     * 执行模型对话并处理模型自主发起的本地工具调用。
     * @param command 当前用户请求。
     * @param runId 运行标识。
     * @param initialAiHistory 初始模型历史。
     * @param builder 最终正文缓冲区。
     * @param thinkingBuilder 思考内容缓冲区。
     * @param streamError 流式异常容器。
     * @param selectedProvider 实际 provider 输出容器。
     * @param selectedModel 实际模型输出容器。
     * @param activeRunId 当前运行标识。
     * @param currentMessageAttachments 当前用户消息已校验附件，用于调整模型可见工具。
     * @param exposeModelTools 是否向模型暴露本地工具；搜索证据已存在时关闭工具，避免模型继续执行浏览器命令。
     */
    private void runAiToolAwareLoop(
        SendChatMessageCommand command,
        Long runId,
        List<ChatMessage> initialAiHistory,
        StringBuilder builder,
        StringBuilder thinkingBuilder,
        AtomicReference<LocalDateTime> thinkingStartedAt,
        Throwable[] streamError,
        String[] selectedProvider,
        String[] selectedModel,
        Long activeRunId,
        List<ChatAttachment> currentMessageAttachments,
        boolean exposeModelTools
    ) {
        List<ChatToolSpec> toolSpecs = exposeModelTools
            ? resolveModelVisibleToolSpecs(initialAiHistory, currentMessageAttachments)
            : List.of();
        List<ChatMessage> currentHistory = new ArrayList<>(initialAiHistory);
        log.info(
            "模型工具决策: 可见工具数={}, 调用模式={}, 工具暴露={}",
            toolSpecs.size(),
            toolSpecs.isEmpty() ? "普通流式" : "工具调用",
            exposeModelTools
        );
        if (toolSpecs.isEmpty()) {
            // 业务约束：没有模型可见工具时必须走普通流式契约，兼容未适配工具调用的 provider 与旧测试桩。
            aiChatClient.streamChat(currentHistory, command.deepThinking(), buildStreamHandler(
                command,
                builder,
                thinkingBuilder,
                thinkingStartedAt,
                streamError,
                selectedProvider,
                selectedModel,
                activeRunId,
                null,
                null
            ));
            return;
        }
        // 轮次上限由系统配置控制，避免模型在工具-回灌链路里无限循环。
        AgentLoopCoordinator loopCoordinator = resolveAgentLoopCoordinator();
        int maxToolRounds = loopCoordinator.normalizeMaxRounds(runtimeSettingService.chatToolMaxRounds());
        Map<String, ChatToolExecutionResult> executedToolResults = new LinkedHashMap<>();
        for (int round = 0; round < maxToolRounds; round++) {
            List<AiToolCall> toolCalls = new ArrayList<>();
            ToolRoundContentBuffer deferredContentDeltas = new ToolRoundContentBuffer(
                command.conversationId(),
                builder,
                !executedToolResults.isEmpty()
            );
            aiChatClient.streamChatWithTools(currentHistory, command.deepThinking(), toolSpecs, buildStreamHandler(
                command,
                builder,
                thinkingBuilder,
                thinkingStartedAt,
                streamError,
                selectedProvider,
                selectedModel,
                activeRunId,
                toolCalls,
                deferredContentDeltas
            ));
            log.info(
                "模型工具轮次: 轮次={}, 工具调用数={}, 流错误={}",
                round + 1,
                toolCalls.size(),
                streamError[0] == null ? null : streamError[0].getMessage()
            );
            if (streamError[0] != null) {
                return;
            }
            if (toolCalls.isEmpty() && deferredContentDeltas.isCommandPlanOnlyContent()) {
                List<AiToolCall> inferredToolCalls = inferLocalShellToolCallsFromCommandPlan(
                    deferredContentDeltas.content(),
                    toolSpecs
                );
                if (CollUtil.isNotEmpty(inferredToolCalls)) {
                    toolCalls.addAll(inferredToolCalls);
                    log.info(
                        "模型命令计划已转为本地工具调用: 轮次={}, 工具调用数={}",
                        round + 1,
                        inferredToolCalls.size()
                    );
                } else {
                    logSuppressedToolRoundContent(round + 1, deferredContentDeltas);
                    currentHistory.add(ChatMessage.create(
                        cn.hutool.core.util.IdUtil.getSnowflakeNextId(),
                        command.conversationId(),
                        ChatMessageRole.SYSTEM,
                        buildCommandPlanOnlyToolRoundGuidance(!executedToolResults.isEmpty()),
                        ChatMessageStatus.COMPLETED,
                        null,
                        null,
                        null
                    ).attachRun(runId));
                    continue;
                }
            }
            if (toolCalls.isEmpty()) {
                if (!executedToolResults.isEmpty() && deferredContentDeltas.isProgressOnlyContent()) {
                    logSuppressedToolRoundContent(round + 1, deferredContentDeltas);
                    currentHistory.add(ChatMessage.create(
                        cn.hutool.core.util.IdUtil.getSnowflakeNextId(),
                        command.conversationId(),
                        ChatMessageRole.SYSTEM,
                        buildProgressOnlyToolRoundGuidance(),
                        ChatMessageStatus.COMPLETED,
                        null,
                        null,
                        null
                    ).attachRun(runId));
                    continue;
                }
                deferredContentDeltas.flushIfNeeded();
                return;
            }
            toolCalls = filterAllowedToolCalls(toolCalls, toolSpecs, currentHistory, command.conversationId(), runId, round + 1);
            if (toolCalls.isEmpty()) {
                logSuppressedToolRoundContent(round + 1, deferredContentDeltas);
                continue;
            }
            // 工具调用轮次中的正文通常是“现在执行”“接下来调用工具”等中间过程，不能作为最终用户回答暴露。
            logSuppressedToolRoundContent(round + 1, deferredContentDeltas);
            for (AiToolCall toolCall : toolCalls) {
                ChatToolExecutionResult toolResult;
                Path deduplicateWorkingDirectory = loopCoordinator.isWriteToolCall(toolCall)
                    ? resolveToolWorkingDirectory(command)
                    : null;
                String toolCallKey = loopCoordinator.deduplicateKey(toolCall, deduplicateWorkingDirectory);
                ChatToolExecutionResult previousToolResult = executedToolResults.get(toolCallKey);
                if (previousToolResult != null) {
                    if (loopCoordinator.isWriteToolCall(toolCall)) {
                        appendAssistantDelta(command.conversationId(), builder, buildRepeatedWriteFinalAnswer(previousToolResult));
                        log.warn(
                            "重复写文件工具调用已收口: 轮次={}, 工具={}, 参数长度={}",
                            round + 1,
                            toolCall.toolCode(),
                            StrUtil.length(toolCall.arguments())
                        );
                        return;
                    }
                    currentHistory.add(ChatMessage.create(
                        cn.hutool.core.util.IdUtil.getSnowflakeNextId(),
                        command.conversationId(),
                        ChatMessageRole.SYSTEM,
                        buildRepeatedNonWriteToolFinalGuidance(toolCall),
                        ChatMessageStatus.COMPLETED,
                        null,
                        null,
                        null
                    ).attachRun(runId));
                    log.warn(
                        "重复本地工具调用已收口为普通生成: 轮次={}, 工具={}, 参数长度={}",
                        round + 1,
                        toolCall.toolCode(),
                        StrUtil.length(toolCall.arguments())
                    );
                    aiChatClient.streamChat(currentHistory, command.deepThinking(), buildStreamHandler(
                        command,
                        builder,
                        thinkingBuilder,
                        thinkingStartedAt,
                        streamError,
                        selectedProvider,
                        selectedModel,
                        activeRunId,
                        null,
                        null
                    ));
                    return;
                } else {
                    try {
                        String toolStepDisplayName = resolveLocalToolDisplayName(toolCall, toolSpecs);
                        toolResult = executeModelToolCall(command, runId, toolCall, toolStepDisplayName);
                    } catch (RuntimeException exception) {
                        streamError[0] = exception;
                        return;
                    }
                    executedToolResults.put(toolCallKey, toolResult);
                }
                String toolEvidenceContext = buildLocalToolEvidenceContext(toolResult);
                currentHistory.add(ChatMessage.create(
                    cn.hutool.core.util.IdUtil.getSnowflakeNextId(),
                    command.conversationId(),
                    ChatMessageRole.SYSTEM,
                    toolEvidenceContext,
                    ChatMessageStatus.COMPLETED,
                    null,
                    null,
                    null
                ).attachRun(runId));
                log.info(
                    "本地工具: 轮次={}, 工具={}, 输出={}, 上下文={}",
                    round + 1,
                    toolCall.toolCode(),
                    StrUtil.length(toolResult.content()),
                    StrUtil.length(toolEvidenceContext)
                );
                log.debug("提示词上下文变化内容:\n{}", toolEvidenceContext);
            }
            AgentLoopResult roundResult = loopCoordinator.resolveRoundResult(
                round,
                maxToolRounds,
                !toolCalls.isEmpty(),
                false,
                false
            );
            if (roundResult.completionReason() == AgentLoopCompletionReason.MAX_ROUNDS) {
                streamError[0] = new IllegalStateException(roundResult.message());
                return;
            }
        }
        // 连续工具调用仍未结束时，用明确异常提示用户收敛工具调用策略。
        streamError[0] = new IllegalStateException(loopCoordinator.maxRoundsMessage());
    }

    /**
     * 判断本轮是否需要向模型暴露本地工具。
     * 关键约束：
     * 1. 已经拿到系统联网检索证据时，模型只负责基于证据整理最终回答，不能再诱导 web-access 跑 Bash/CDP 循环。
     * 2. 用户选中技能后只问“这是啥/有什么用”时，语义是解释当前技能本身；走普通流式才能让前端逐 token 展示。
     * 3. 带 URL、搜索词、附件或明确执行意图的技能任务仍保留工具能力，由技能上下文约束模型是否调用工具。
     *
     * @param searchReferences 本轮系统搜索引用。
     * @param selectedSkillCodes 本轮已选技能编码。
     * @param question 本轮剥离技能标记后的自然语言问题。
     * @return 是否暴露本地工具 schema。
     */
    private boolean shouldExposeModelTools(
        List<SearchReferenceCandidate> searchReferences,
        List<String> selectedSkillCodes,
        String question
    ) {
        if (CollUtil.isNotEmpty(searchReferences)) {
            return false;
        }
        if (CollUtil.isNotEmpty(selectedSkillCodes) && isSelectedSkillShortReferenceQuestion(question)) {
            return false;
        }
        return true;
    }

    /**
     * 将确认不再发起工具调用的模型正文回放给用户，并同步追加到最终助手消息缓冲。
     *
     * @param conversationId 当前会话标识。
     * @param builder 最终助手消息正文缓冲。
     * @param deferredContentDeltas 本轮模型产生但尚未对用户可见的正文增量。
     */
    private void logSuppressedToolRoundContent(int round, ToolRoundContentBuffer deferredContentDeltas) {
        if (deferredContentDeltas == null || deferredContentDeltas.isEmpty()) {
            return;
        }
        log.info(
            "模型工具轮次正文已隐藏: 轮次={}, 增量数={}, 长度={}",
            round,
            deferredContentDeltas.size(),
            deferredContentDeltas.length()
        );
    }

    /**
     * 判断工具轮次正文是否属于内部进度说明。
     * 关键约束：工具已执行后，模型可能输出“正在执行页面信息读取...”或较长的“下一步行动/我将读取”叙述；
     * 这些内容都不是用户答案，不能发布为最终消息或落库进历史。
     *
     * @param content 本轮模型正文。
     * @return 是否只包含内部进度说明。
     */
    private boolean isProgressOnlyToolRoundContent(String content) {
        content = StrUtil.trimToEmpty(content);
        if (StrUtil.isBlank(content)) {
            return false;
        }
        if (isCommandPlanOnlyToolRoundContent(content) || isExecutionNarrationOnlyToolRoundContent(content)) {
            return true;
        }
        if (content.length() > 80) {
            return false;
        }
        String normalizedContent = content.replaceAll("\\s+", "");
        boolean hasProgressPrefix = normalizedContent.startsWith("正在执行")
            || normalizedContent.startsWith("正在读取")
            || normalizedContent.startsWith("正在获取")
            || normalizedContent.startsWith("正在检查")
            || normalizedContent.startsWith("正在打开")
            || normalizedContent.startsWith("正在访问")
            || normalizedContent.startsWith("正在处理")
            || normalizedContent.startsWith("正在加载")
            || normalizedContent.startsWith("现在执行")
            || normalizedContent.startsWith("接下来我将");
        boolean hasProgressSuffix = normalizedContent.endsWith("...")
            || normalizedContent.endsWith("…")
            || normalizedContent.endsWith("中")
            || normalizedContent.endsWith("中...");
        return hasProgressPrefix && hasProgressSuffix;
    }

    /**
     * 判断工具回灌后的正文是否只是自然语言执行叙述。
     * 业务背景：部分模型会先输出较长的“我们先确认/下一步行动/现在执行”正文，随后才发起 read/grep/write。
     * 该阶段还没有形成用户可见结论，必须继续留在后端缓冲，避免重复显示并污染下一轮历史。
     *
     * @param content 本轮模型正文。
     * @return true 表示该正文只描述待执行动作。
     */
    private boolean isExecutionNarrationOnlyToolRoundContent(String content) {
        String normalizedContent = StrUtil.trimToEmpty(content).replaceAll("\\s+", "");
        if (StrUtil.isBlank(normalizedContent)) {
            return false;
        }
        boolean hasStrongPendingMarker = normalizedContent.contains("现在执行")
            || normalizedContent.contains("准备执行")
            || normalizedContent.contains("执行命令")
            || normalizedContent.contains("下一步行动")
            || normalizedContent.contains("确认动作")
            || normalizedContent.contains("我将：")
            || normalizedContent.contains("我将:");
        if (hasStrongPendingMarker) {
            return true;
        }
        boolean hasPlanningMarker = normalizedContent.startsWith("我们先")
            || normalizedContent.startsWith("我先")
            || normalizedContent.contains("先确认")
            || normalizedContent.contains("需要先")
            || normalizedContent.contains("下一步将")
            || normalizedContent.contains("接下来我将")
            || normalizedContent.contains("再针对性");
        boolean hasCompletedResultEvidence = normalizedContent.contains("已完成")
            || normalizedContent.contains("已经完成")
            || normalizedContent.contains("修复完成")
            || normalizedContent.contains("文件已写入")
            || normalizedContent.contains("已写入")
            || normalizedContent.contains("已创建")
            || normalizedContent.contains("已读取")
            || normalizedContent.contains("结果如下")
            || normalizedContent.contains("最终结果");
        return hasPlanningMarker && !hasCompletedResultEvidence;
    }

    /**
     * 判断工具回灌后的正文是否只是“下一步要执行的命令计划”。
     * 业务约束：模型可能把 `Invoke-RestMethod` / `/info` / `/eval` 写进正文但没有发起 tool_call；
     * 这类内容对用户来说仍是未完成的内部过程，不能落库为最终回答。
     *
     * @param content 本轮模型正文。
     * @return 是否为命令计划型过程文案。
     */
    private boolean isCommandPlanOnlyToolRoundContent(String content) {
        String normalizedContent = content.replaceAll("\\s+", "");
        boolean hasPendingExecution = normalizedContent.contains("现在执行")
            || normalizedContent.contains("正在执行")
            || normalizedContent.contains("执行中")
            || normalizedContent.contains("将立即执行")
            || normalizedContent.contains("我将立即执行")
            || normalizedContent.contains("我将执行")
            || normalizedContent.contains("接下来将")
            || normalizedContent.contains("下一步将");
        boolean hasExecutionIntent = hasPendingExecution
            || normalizedContent.contains("接下来")
            || normalizedContent.contains("我将")
            || normalizedContent.contains("将执行");
        boolean hasCommandLikeText = normalizedContent.contains("Invoke-RestMethod")
            || normalizedContent.contains("Invoke-WebRequest")
            || normalizedContent.contains("http://localhost:3456/info")
            || normalizedContent.contains("http://localhost:3456/eval")
            || normalizedContent.contains("http://localhost:3456/screenshot")
            || normalizedContent.contains("/info")
            || normalizedContent.contains("/eval")
            || normalizedContent.contains("/screenshot");
        boolean hasCompletedResultEvidence = normalizedContent.contains("已执行")
            || normalizedContent.contains("工具返回")
            || normalizedContent.contains("返回：")
            || normalizedContent.contains("结果：")
            || normalizedContent.contains("返回结果")
            || normalizedContent.contains("执行结果");
        return hasExecutionIntent && hasCommandLikeText && (hasPendingExecution || !hasCompletedResultEvidence);
    }

    /**
     * 暂存工具模型轮次正文，并在工具回灌后的真实最终回答出现时切换为实时 SSE 发布。
     * 业务意图：带 tool_call 的轮次正文通常是内部执行说明，必须隐藏；无 tool_call 的最终回答则要边生成边展示。
     */
    private class ToolRoundContentBuffer {

        /** 当前会话标识，用于向对应 SSE 连接发布助手正文增量。 */
        private final Long conversationId;

        /** 最终助手消息正文缓冲，实时发布和延迟回放都必须同步写入。 */
        private final StringBuilder builder;

        /** 工具已经回灌后的轮次允许从“延迟缓冲”升级为“实时发布”。 */
        private final boolean livePublishEligible;

        /** 本轮模型正文增量，未发布时用于后续隐藏或一次性确认；已发布后用于日志与判断。 */
        private final List<String> deltas = new ArrayList<>();

        /** 一旦本轮出现 tool_call，正文必须保持隐藏，不能再升级为用户可见内容。 */
        private boolean toolCallObserved;

        /** 已确认本轮是用户可见的最终回答，后续 delta 直接走 SSE。 */
        private boolean livePublishing;

        /** 已经发布到用户侧的增量数量，用于避免轮次结束时重复回放。 */
        private int publishedDeltaCount;

        /**
         * @param conversationId 当前会话标识。
         * @param builder 最终助手消息正文缓冲。
         * @param livePublishEligible 是否允许在本轮识别为最终回答后实时发布。
         */
        private ToolRoundContentBuffer(Long conversationId, StringBuilder builder, boolean livePublishEligible) {
            this.conversationId = conversationId;
            this.builder = builder;
            this.livePublishEligible = livePublishEligible;
        }

        /**
         * 接收模型正文增量；工具回灌后的非进度正文会立即 flush 已缓冲片段并切入实时发布。
         *
         * @param delta 模型流式正文片段。
         */
        private void add(String delta) {
            deltas.add(delta);
            if (livePublishing) {
                publish(delta);
                publishedDeltaCount = deltas.size();
                return;
            }
            if (livePublishEligible && !toolCallObserved && !isPossibleProgressOnlyContent()) {
                flush();
                livePublishing = true;
            }
        }

        /**
         * 标记本轮已经出现工具调用；此后正文只作为内部执行说明保留，等待轮次结束后丢弃。
         */
        private void markToolCallObserved() {
            toolCallObserved = true;
        }

        /**
         * 将已经确认可见的正文发布给前端，并同步写入最终助手消息。
         */
        private void flush() {
            for (String delta : deltas) {
                publish(delta);
            }
            publishedDeltaCount = deltas.size();
        }

        /**
         * 轮次结束时补发尚未发布的最终回答片段；已实时发布的轮次不重复发送。
         */
        private void flushIfNeeded() {
            while (publishedDeltaCount < deltas.size()) {
                publish(deltas.get(publishedDeltaCount));
                publishedDeltaCount++;
            }
        }

        /**
         * 判断当前缓冲是否仍像“正在执行...”这类短进度句。
         *
         * @return 是否为短进度说明。
         */
        private boolean isProgressOnlyContent() {
            return isProgressOnlyToolRoundContent(String.join("", deltas));
        }

        /**
         * 判断当前缓冲是否只是待执行命令清单；命中时必须隐藏并要求模型改为真实工具调用。
         *
         * @return 是否为命令计划型过程文案。
         */
        private boolean isCommandPlanOnlyContent() {
            return isCommandPlanOnlyToolRoundContent(String.join("", deltas));
        }

        /**
         * 判断当前片段是否仍可能发展为内部进度句；命中时继续缓冲，避免把“正在执行/下一步行动”提前推给用户。
         *
         * @return 是否仍可能是内部进度说明。
         */
        private boolean isPossibleProgressOnlyContent() {
            String content = StrUtil.trimToEmpty(String.join("", deltas));
            if (StrUtil.isBlank(content)) {
                return false;
            }
            if (isCommandPlanOnlyToolRoundContent(content) || isExecutionNarrationOnlyToolRoundContent(content)) {
                return true;
            }
            if (content.length() > 80) {
                return false;
            }
            String normalizedContent = content.replaceAll("\\s+", "");
            return normalizedContent.startsWith("正在执行")
                || normalizedContent.startsWith("正在读取")
                || normalizedContent.startsWith("正在获取")
                || normalizedContent.startsWith("正在检查")
                || normalizedContent.startsWith("正在打开")
                || normalizedContent.startsWith("正在访问")
                || normalizedContent.startsWith("正在处理")
                || normalizedContent.startsWith("正在加载")
                || normalizedContent.startsWith("现在执行")
                || normalizedContent.startsWith("接下来我将");
        }

        /**
         * @return 当前轮次是否没有任何正文增量。
         */
        private boolean isEmpty() {
            return CollUtil.isEmpty(deltas);
        }

        /**
         * @return 当前轮次正文增量数量。
         */
        private int size() {
            return deltas.size();
        }

        /**
         * @return 当前轮次正文总长度。
         */
        private int length() {
            return deltas.stream().mapToInt(StrUtil::length).sum();
        }

        /**
         * @return 当前轮次完整正文，用于从模型计划中提取可兜底执行的本地命令。
         */
        private String content() {
            return String.join("", deltas);
        }

        /**
         * 发布单个正文增量；空增量不写入 builder，避免最终消息出现无意义片段。
         *
         * @param delta 待发布正文片段。
         */
        private void publish(String delta) {
            if (StrUtil.isEmpty(delta)) {
                return;
            }
            builder.append(delta);
            chatStreamPublisher.publishAssistantDelta(conversationId, delta);
        }
    }

    /**
     * 构造工具进度句被隐藏后的纠偏提示，要求模型基于已有工具证据直接给出用户可见结果。
     *
     * @return 系统提示词。
     */
    private String buildProgressOnlyToolRoundGuidance() {
        return """
            上一轮模型只输出了内部进度说明，没有给出用户可见的最终答案。
            后端已隐藏该进度说明；请基于前面已经追加的工具结果，直接输出面向用户的最终回答。
            不要再输出“正在执行”“正在读取”“请稍等”等过程文案；如果信息不足，应说明缺少哪些目标或权限。
            """;
    }

    /**
     * 构造命令计划被隐藏后的纠偏提示，避免模型把“将执行的命令”误当成已完成结果。
     *
     * @param hasToolEvidence 当前轮之前是否已经有真实工具结果。
     * @return 系统提示词。
     */
    private String buildCommandPlanOnlyToolRoundGuidance(boolean hasToolEvidence) {
        String evidenceRequirement = hasToolEvidence
            ? "如果已有工具结果已经足够回答用户，直接输出最终结果；如果还需要读取页面或执行命令，必须发起真实 tool_call。"
            : "当前还没有任何真实工具结果；如果任务需要浏览器、联网或本地命令，必须发起真实 tool_call。";
        return """
            上一轮模型只把准备执行的命令写进了正文，但没有发起真实 tool_call。
            后端已隐藏该命令计划，因为它不是用户可见的完成结果。
            %s
            不要再用正文列出 Invoke-RestMethod、Invoke-WebRequest、/info、/eval 等待执行命令；需要执行时只能通过本轮可见本地工具发起 tool_call。
            """.formatted(evidenceRequirement);
    }

    /**
     * 将模型写在正文中的 web-access CDP 命令兜底转换为真实本地工具调用。
     * 关键约束：只接受 CDP Proxy 本地端口和 check-deps 脚本，避免把普通说明文字扩大成任意命令执行。
     *
     * @param content 模型本轮正文。
     * @param toolSpecs 本轮真实暴露给模型的工具 schema。
     * @return 可直接进入后续白名单过滤和执行流程的工具调用。
     */
    private List<AiToolCall> inferLocalShellToolCallsFromCommandPlan(String content, List<ChatToolSpec> toolSpecs) {
        String shellToolName = resolveVisibleShellToolName(toolSpecs);
        if (StrUtil.isBlank(shellToolName) || StrUtil.isBlank(content)) {
            return List.of();
        }
        List<String> commands = content.lines()
            .map(this::normalizeCommandPlanLine)
            .filter(this::isAutoExecutableWebAccessCommand)
            .distinct()
            .limit(5)
            .toList();
        if (commands.isEmpty()) {
            return List.of();
        }
        List<AiToolCall> toolCalls = new ArrayList<>();
        for (String command : commands) {
            String arguments = cn.hutool.json.JSONUtil.createObj()
                .set("command", command)
                .toString();
            toolCalls.add(new AiToolCall(
                "inferred-command-plan-" + cn.hutool.core.util.IdUtil.fastSimpleUUID(),
                shellToolName,
                arguments
            ));
        }
        return toolCalls;
    }

    /**
     * 解析模型计划清单中的单行命令，去掉 Markdown 列表、引用和反引号。
     *
     * @param line 原始行文本。
     * @return 可能的 PowerShell 命令。
     */
    private String normalizeCommandPlanLine(String line) {
        String normalizedLine = StrUtil.trimToEmpty(line)
            .replace("\\\"", "\"");
        normalizedLine = normalizedLine.replaceFirst("^[>\\s]*[-*•\\d.)、\\s]+", "");
        normalizedLine = StrUtil.trimToEmpty(normalizedLine);
        while (normalizedLine.startsWith("`") && normalizedLine.endsWith("`") && normalizedLine.length() > 1) {
            normalizedLine = StrUtil.trimToEmpty(normalizedLine.substring(1, normalizedLine.length() - 1));
        }
        return normalizedLine;
    }

    /**
     * 判断命令是否属于 web-access CDP 运行时允许自动兜底执行的范围。
     *
     * @param command 规范化后的命令文本。
     * @return 是否可自动转换成工具调用。
     */
    private boolean isAutoExecutableWebAccessCommand(String command) {
        if (StrUtil.isBlank(command)) {
            return false;
        }
        String lowerCommand = command.toLowerCase(java.util.Locale.ROOT);
        boolean supportedCommand = lowerCommand.startsWith("invoke-restmethod")
            || lowerCommand.startsWith("invoke-webrequest")
            || lowerCommand.startsWith("curl ")
            || lowerCommand.startsWith("node ");
        boolean webAccessTarget = lowerCommand.contains("localhost:3456")
            || lowerCommand.contains("127.0.0.1:3456")
            || lowerCommand.contains("check-deps.mjs");
        return supportedCommand && webAccessTarget;
    }

    /**
     * 从本轮可见工具中选择可执行 PowerShell 命令的工具名。
     *
     * @param toolSpecs 本轮真实暴露给模型的工具 schema。
     * @return bash 或 shell_command；缺失时返回空字符串。
     */
    private String resolveVisibleShellToolName(List<ChatToolSpec> toolSpecs) {
        if (CollUtil.isEmpty(toolSpecs)) {
            return "";
        }
        Optional<String> bashTool = toolSpecs.stream()
            .map(ChatToolSpec::name)
            .filter(toolName -> StrUtil.equalsIgnoreCase(toolName, "bash"))
            .findFirst();
        if (bashTool.isPresent()) {
            return bashTool.get();
        }
        return toolSpecs.stream()
            .map(ChatToolSpec::name)
            .filter(toolName -> StrUtil.equalsIgnoreCase(toolName, "shell_command"))
            .findFirst()
            .orElse("");
    }

    /**
     * 只允许执行本轮 schema 明确暴露的工具，避免模型把技能编码、MCP 编码或自然语言误当成本地工具名。
     */
    private List<AiToolCall> filterAllowedToolCalls(
        List<AiToolCall> toolCalls,
        List<ChatToolSpec> visibleToolSpecs,
        List<ChatMessage> currentHistory,
        Long conversationId,
        Long runId,
        int round
    ) {
        if (CollUtil.isEmpty(toolCalls)) {
            return List.of();
        }
        Set<String> allowedToolCodes = visibleToolSpecs == null
            ? Set.of()
            : visibleToolSpecs.stream()
                .map(ChatToolSpec::name)
                .filter(StrUtil::isNotBlank)
                .map(this::normalizeToolCode)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<AiToolCall> allowedToolCalls = new ArrayList<>();
        List<String> blockedToolCodes = new ArrayList<>();
        for (AiToolCall toolCall : toolCalls) {
            String normalizedToolCode = normalizeToolCode(toolCall == null ? null : toolCall.toolCode());
            if (allowedToolCodes.contains(normalizedToolCode)) {
                allowedToolCalls.add(toolCall);
                continue;
            }
            blockedToolCodes.add(StrUtil.blankToDefault(toolCall == null ? null : toolCall.toolCode(), "unknown"));
        }
        if (blockedToolCodes.isEmpty()) {
            return allowedToolCalls;
        }
        String guidance = buildInvalidToolCallGuidance(blockedToolCodes, allowedToolCodes);
        currentHistory.add(ChatMessage.create(
            cn.hutool.core.util.IdUtil.getSnowflakeNextId(),
            conversationId,
            ChatMessageRole.SYSTEM,
            guidance,
            ChatMessageStatus.COMPLETED,
            null,
            null,
            null
        ).attachRun(runId));
        log.warn(
            "模型伪工具调用已忽略: 轮次={}, 工具={}, 可见工具={}",
            round,
            blockedToolCodes,
            allowedToolCodes
        );
        return allowedToolCalls;
    }

    /**
     * 构造伪工具调用纠偏提示，重点区分“错误 tool_call 被忽略”和“已选技能仍然有效”。
     * 模型容易把 skill code 当成本地工具名调用，这里只拦截该工具调用，不否定技能上下文本身。
     */
    private String buildInvalidToolCallGuidance(List<String> blockedToolCodes, Set<String> allowedToolCodes) {
        return """
            模型刚才把技能编码、MCP 编码或普通文本当成本地工具名发起 tool_call。后端仅忽略这个错误的 tool_call；技能选择和技能上下文仍然保留。
            被忽略的 tool_call 名称：%s
            本轮真实可调用的本地工具：%s

            继续执行要求：
            - 已选技能仍然有效，必须继续按已选技能说明理解和回答用户请求。
            - 不要告诉用户这个技能失效、未选中或被系统跳过；对用户应继续按已选技能处理请求。
            - 不要再用 skill code 发起 tool_call；只有确实需要且名称在真实可调用工具列表中时，才调用这些本地工具。
            - 如果当前运行环境确实缺少完成浏览器或联网操作的能力，只说明缺少可用执行能力，并给出可执行替代或追问缺失目标。
            """.formatted(
            String.join("、", blockedToolCodes),
            allowedToolCodes.isEmpty() ? "无" : String.join("、", allowedToolCodes)
        );
    }

    private String normalizeToolCode(String toolCode) {
        return StrUtil.trimToEmpty(toolCode).toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 根据当前模型上下文裁剪可见工具；图片附件已作为多模态内容入模时，隐藏 view_image。
     * 关键约束：上传图片不是本地路径，继续暴露 view_image 会诱导模型把附件当路径调用并返回“图片不存在”。
     *
     * @param aiHistory 本轮入模消息历史。
     * @param currentMessageAttachments 当前用户消息附件。
     * @return 本轮允许模型调用的工具列表。
     */
    private List<ChatToolSpec> resolveModelVisibleToolSpecs(
        List<ChatMessage> aiHistory,
        List<ChatAttachment> currentMessageAttachments
    ) {
        List<ChatToolSpec> toolSpecs = chatToolSpecService == null
            ? List.of()
            : Optional.ofNullable(chatToolSpecService.listModelVisibleToolSpecs()).orElse(List.of());
        if (toolSpecs.isEmpty()) {
            return toolSpecs;
        }
        boolean hasImageAttachment =
            hasImageAttachment(currentMessageAttachments) || historyHasImageAttachment(aiHistory);
        if (!hasImageAttachment) {
            return toolSpecs;
        }
        return toolSpecs.stream()
            .filter(toolSpec -> !StrUtil.equalsIgnoreCase(toolSpec.name(), "view_image"))
            .toList();
    }

    /**
     * 判断附件集合中是否包含图片。
     * @param attachments 附件集合，可为空。
     * @return 是否存在图片附件。
     */
    private boolean hasImageAttachment(List<ChatAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return false;
        }
        return attachments.stream()
            .anyMatch(attachment -> attachment != null && StrUtil.equalsIgnoreCase(attachment.getAttachmentType(), "image"));
    }

    /**
     * 从历史消息绑定附件中判断是否存在图片，覆盖重新生成和追问图片的场景。
     * @param aiHistory 本轮入模消息历史。
     * @return 是否存在历史图片附件。
     */
    private boolean historyHasImageAttachment(List<ChatMessage> aiHistory) {
        if (aiHistory == null || aiHistory.isEmpty()) {
            return false;
        }
        for (ChatMessage message : aiHistory) {
            if (message == null || message.getRole() != ChatMessageRole.USER || message.getId() == null) {
                continue;
            }
            if (hasImageAttachment(chatAttachmentService.listByMessageId(message.getId()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建统一流处理器，普通流式和工具调用流式共享增量、思考与异常处理。
     * @param command 当前用户请求。
     * @param builder 正文缓冲区。
     * @param thinkingBuilder 思考缓冲区。
     * @param streamError 流异常容器。
     * @param selectedProvider provider 输出容器。
     * @param selectedModel 模型输出容器。
     * @param activeRunId 当前运行标识。
     * @param toolCalls 工具调用收集器；为空时表示普通流式模式。
     * @param deferredContentDeltas 工具模式下的正文延迟缓冲；为空时立即发布给用户。
     * @return 可传给模型客户端的流处理器。
     */
    private AiChatClient.ToolAwareStreamHandler buildStreamHandler(
        SendChatMessageCommand command,
        StringBuilder builder,
        StringBuilder thinkingBuilder,
        AtomicReference<LocalDateTime> thinkingStartedAt,
        Throwable[] streamError,
        String[] selectedProvider,
        String[] selectedModel,
        Long activeRunId,
        List<AiToolCall> toolCalls,
        ToolRoundContentBuffer deferredContentDeltas
    ) {
        return new AiChatClient.ToolAwareStreamHandler() {
            /**
             * 已发布的工具参数长度，按 callId 记录，用于限制大段 content 分片造成的 SSE 过量刷新。
             */
            private final Map<String, Integer> publishedToolArgumentProgressLengths = new LinkedHashMap<>();

            @Override
            public void onMetadata(String provider, String model) {
                selectedProvider[0] = provider;
                selectedModel[0] = model;
            }

            @Override
            public void onDelta(String delta) {
                if (chatRuntimeGuardService.isCancelled(command.conversationId(), activeRunId)) {
                    return;
                }
                if (StrUtil.isEmpty(delta)) {
                    return;
                }
                if (deferredContentDeltas != null) {
                    deferredContentDeltas.add(delta);
                    return;
                }
                builder.append(delta);
                chatStreamPublisher.publishAssistantDelta(command.conversationId(), delta);
            }

            @Override
            public void onThinkingDelta(String delta) {
                if (chatRuntimeGuardService.isCancelled(command.conversationId(), activeRunId)) {
                    return;
                }
                thinkingStartedAt.compareAndSet(null, LocalDateTime.now());
                thinkingBuilder.append(delta);
                chatStreamPublisher.publishAssistantThinkingDelta(command.conversationId(), delta);
            }

            @Override
            public void onToolCall(AiToolCall toolCall) {
                if (toolCalls != null) {
                    toolCalls.add(toolCall);
                }
                if (deferredContentDeltas != null) {
                    deferredContentDeltas.markToolCallObserved();
                }
            }

            @Override
            public void onToolCallDelta(AiToolCallDelta toolCallDelta) {
                if (chatRuntimeGuardService.isCancelled(command.conversationId(), activeRunId)) {
                    return;
                }
                if (toolCallDelta == null || StrUtil.isBlank(toolCallDelta.toolCode())) {
                    return;
                }
                if (deferredContentDeltas != null) {
                    deferredContentDeltas.markToolCallObserved();
                }
                if (toolCalls == null || !shouldPublishToolArgumentProgress(toolCallDelta, publishedToolArgumentProgressLengths)) {
                    return;
                }
                publishLocalToolCallProgress(command.conversationId(), toolCallDelta);
            }

            @Override
            public void onComplete() {
            }

            @Override
            public void onError(Throwable throwable) {
                streamError[0] = throwable;
            }
        };
    }

    /**
     * 判断本次工具参数进度是否需要推送给前端。
     * 业务约束：write.content 可能按 token 级分片返回，首片必须立即显示，后续按长度间隔刷新即可。
     *
     * @param toolCallDelta 工具参数进度。
     * @param publishedLengths 已发布长度缓存。
     * @return 是否发布。
     */
    private boolean shouldPublishToolArgumentProgress(
        AiToolCallDelta toolCallDelta,
        Map<String, Integer> publishedLengths
    ) {
        String key = StrUtil.blankToDefault(toolCallDelta.callId(), toolCallDelta.toolCode());
        int currentLength = StrUtil.length(toolCallDelta.accumulatedArguments());
        Integer previousLength = publishedLengths.get(key);
        if (previousLength == null || currentLength < 800 || currentLength - previousLength >= 800) {
            publishedLengths.put(key, currentLength);
            return true;
        }
        return false;
    }

    /**
     * 将模型工具参数分片转换为前端已支持的 tool-call progress 事件。
     *
     * @param conversationId 会话标识。
     * @param toolCallDelta 工具参数进度。
     */
    private void publishLocalToolCallProgress(Long conversationId, AiToolCallDelta toolCallDelta) {
        AiToolCall partialToolCall = new AiToolCall(
            toolCallDelta.callId(),
            toolCallDelta.toolCode(),
            StrUtil.blankToDefault(toolCallDelta.accumulatedArguments(), "")
        );
        Map<String, Object> payload = baseLocalToolCallPayload(partialToolCall, "progress", LocalDateTime.now());
        Object progressParams = parseToolCallProgressParams(toolCallDelta);
        payload.put("params", progressParams);
        payload.put("progressStage", "arguments");
        payload.put("progressText", buildToolArgumentProgressText(toolCallDelta, progressParams));
        payload.put("progressDetail", Map.of(
            "argumentLength", StrUtil.length(toolCallDelta.accumulatedArguments()),
            "deltaLength", StrUtil.length(toolCallDelta.argumentsDelta())
        ));
        payload.put("reactAction", buildLocalToolProgressAction(toolCallDelta, progressParams));
        chatStreamPublisher.publishToolCall(conversationId, payload);
    }

    /**
     * 在当前本地 workspace 中执行模型请求的工具。
     * @param command 当前消息命令。
     * @param runId 运行标识。
     * @param toolCall 模型工具调用。
     * @param toolStepDisplayName 持久化步骤展示名，优先使用工具规格描述，便于历史过程卡片展示中文名称。
     * @return 工具执行结果。
     */
    private ChatToolExecutionResult executeModelToolCall(
        SendChatMessageCommand command,
        Long runId,
        AiToolCall toolCall,
        String toolStepDisplayName
    ) {
        // 步骤 1：保存调用前线程上下文，再按当前消息绑定本地 workspace，防止工具执行目录错乱。
        Optional<Path> previousWorkingDirectory = ChatToolExecutionContext.currentToolWorkingDirectory();
        Map<String, Path> previousSkillDirectories = ChatToolExecutionContext.currentSkillDirectories();
        Path workspacePath = resolveToolWorkingDirectory(command);
        if (workspacePath != null) {
            ChatToolExecutionContext.bindToolWorkingDirectory(workspacePath);
        }
        LocalDateTime startedAt = LocalDateTime.now();
        publishLocalToolCallEvent(command.conversationId(), toolCall, "start", startedAt, null, null);
        try {
            // 步骤 2：执行模型指定工具，并把工具输出转成 chat_execution_step 供前端时间线展示。
            ChatToolExecutionResult toolResult = chatToolExecutionService.execute(toolCall.toolCode(), toolCall.arguments());
            ChatExecutionStep toolStep = ChatExecutionStep.builder()
                .id(cn.hutool.core.util.IdUtil.getSnowflakeNextId())
                .runId(runId)
                .stepType("tool")
                .stepTitle("执行本地工具 " + toolCall.toolCode())
                .stepStatus("COMPLETED")
                .sequenceNo(1L)
                .content(toolResult.content())
                .metadataJson(cn.hutool.json.JSONUtil.toJsonStr(
                    buildLocalToolStepMetadata(toolCall, toolStepDisplayName, toolResult)
                ))
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();
            if (!command.localOnly()) {
                chatExecutionStepRepository.save(toolStep);
                chatStreamPublisher.publishStep(command.conversationId(), buildExecutionStepPayload(toolStep));
            }
            persistPlanStepsIfNeeded(command, runId, toolResult);
            // 步骤 3：发布工具完成事件并返回工具内容给模型循环，模型可继续基于结果生成回答。
            publishLocalToolCallEvent(command.conversationId(), toolCall, "complete", startedAt, LocalDateTime.now(), toolResult);
            return toolResult;
        } catch (BusinessException exception) {
            // 步骤 4：权限策略要求用户确认时触发任务级 Hook，供后续桌面通知或宠物联动提醒用户处理。
            if ("GOVERNANCE_PERMISSION_CONFIRM_REQUIRED".equals(exception.getCode())) {
                triggerGovernanceHook(
                    "TASK_CONFIRM_REQUIRED",
                    command.conversationId(),
                    runId,
                    toolCall.toolCode(),
                    "任务需要确认：" + exception.getMessage()
                );
            }
            publishLocalToolCallError(command.conversationId(), toolCall, startedAt, exception);
            throw exception;
        } catch (RuntimeException exception) {
            // 步骤 5：工具执行失败时先通知前端工具错误，再把异常交给模型循环外层收口。
            publishLocalToolCallError(command.conversationId(), toolCall, startedAt, exception);
            throw exception;
        } finally {
            // 步骤 6：恢复进入工具前的线程上下文，避免后续工具调用沿用错误目录。
            restoreToolExecutionContext(previousWorkingDirectory, previousSkillDirectories);
        }
    }

    /**
     * 构造执行步骤 SSE 载荷。
     * 业务意图：在线流仍保留前端既有 step 字段，同时携带 metadataJson/metadata，
     * 便于工具过程卡片在刷新前后使用同一份结构化参数和结果元数据。
     *
     * @param step 执行步骤快照。
     * @return 可直接推送给前端的步骤载荷。
     */
    private Map<String, Object> buildExecutionStepPayload(ChatExecutionStep step) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", step.getId());
        payload.put("runId", step.getRunId());
        payload.put("stepType", step.getStepType());
        payload.put("stepTitle", step.getStepTitle());
        payload.put("stepStatus", step.getStepStatus());
        payload.put("sequenceNo", step.getSequenceNo());
        payload.put("content", step.getContent());
        if (StrUtil.isNotBlank(step.getMetadataJson())) {
            payload.put("metadataJson", step.getMetadataJson());
            try {
                payload.put("metadata", cn.hutool.json.JSONUtil.parseObj(step.getMetadataJson()));
            } catch (RuntimeException ignored) {
                // metadataJson 已经是可回放原文，解析失败不应影响实时步骤事件推送。
            }
        }
        return payload;
    }

    /**
     * 将 update_plan 工具返回的步骤持久化为 plan 类型执行步骤，并同步推送给前端时间线。
     */
    private void persistPlanStepsIfNeeded(SendChatMessageCommand command, Long runId, ChatToolExecutionResult toolResult) {
        if (command.localOnly() || toolResult == null || toolResult.metadata() == null) {
            return;
        }
        Object canonicalToolCode = toolResult.metadata().get("canonicalToolCode");
        boolean updatePlanTool = StrUtil.equalsIgnoreCase(toolResult.toolCode(), "update_plan")
            || StrUtil.equalsIgnoreCase(String.valueOf(canonicalToolCode), "update_plan");
        if (!updatePlanTool || !(toolResult.metadata().get("steps") instanceof Iterable<?> steps)) {
            return;
        }
        long sequenceNo = 1L;
        for (Object stepItem : steps) {
            cn.hutool.json.JSONObject stepObject = cn.hutool.json.JSONUtil.parseObj(stepItem);
            String title = StrUtil.blankToDefault(stepObject.getStr("step"), stepObject.getStr("title"));
            if (StrUtil.isBlank(title)) {
                continue;
            }
            String status = normalizePlanStepStatus(stepObject.getStr("status"));
            ChatExecutionStep planStep = ChatExecutionStep.builder()
                .id(IdUtil.getSnowflakeNextId())
                .runId(runId)
                .stepType("plan")
                .stepTitle(title)
                .stepStatus(status)
                .sequenceNo(sequenceNo++)
                .content(toolResult.content())
                .metadataJson(cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                    "planId",
                    String.valueOf(toolResult.metadata().getOrDefault("planId", "default")),
                    "sourceTool",
                    StrUtil.blankToDefault(toolResult.toolCode(), "update_plan")
                )))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
            chatExecutionStepRepository.save(planStep);
            chatStreamPublisher.publishStep(command.conversationId(), Map.of(
                "id", planStep.getId(),
                "runId", planStep.getRunId(),
                "stepType", planStep.getStepType(),
                "stepTitle", planStep.getStepTitle(),
                "stepStatus", planStep.getStepStatus(),
                "sequenceNo", planStep.getSequenceNo(),
                "content", planStep.getContent()
            ));
        }
    }

    /**
     * 归一化模型传入的计划步骤状态，保持前端时间线使用既有大写状态。
     */
    private String normalizePlanStepStatus(String rawStatus) {
        String status = StrUtil.trimToEmpty(rawStatus).toLowerCase(java.util.Locale.ROOT);
        return switch (status) {
            case "completed", "complete", "done" -> "COMPLETED";
            case "in_progress", "running", "active" -> "RUNNING";
            default -> "PENDING";
        };
    }

    /**
     * 触发自动化 Hook；规则匹配失败不能反向中断聊天主流程。
     */
    private void triggerGovernanceHook(
        String triggerPoint,
        Long conversationId,
        Long runId,
        String toolCode,
        String contextText
    ) {
        if (hookRuleService == null) {
            return;
        }
        try {
            hookRuleService.trigger(triggerPoint, conversationId, runId, toolCode, contextText);
        } catch (RuntimeException exception) {
            log.warn(
                "治理 Hook 触发失败: triggerPoint={}, conversationId={}, runId={}",
                triggerPoint,
                conversationId,
                runId,
                exception
            );
        }
    }

    /**
     * 恢复本次工具调用前的线程上下文，避免连续工具调用丢失由外层聊天执行器绑定的 skill 目录。
     *
     * @param previousWorkingDirectory 调用前的工具工作目录。
     * @param previousSkillDirectories 调用前的 skill 目录映射。
     */
    private void restoreToolExecutionContext(
        Optional<Path> previousWorkingDirectory,
        Map<String, Path> previousSkillDirectories
    ) {
        if (previousWorkingDirectory.isPresent()) {
            ChatToolExecutionContext.bindToolWorkingDirectory(previousWorkingDirectory.get());
        } else {
            ChatToolExecutionContext.bindToolWorkingDirectory(null);
        }
        if (previousSkillDirectories == null || previousSkillDirectories.isEmpty()) {
            ChatToolExecutionContext.bindSkillDirectories(Map.of());
            return;
        }
        ChatToolExecutionContext.bindSkillDirectories(previousSkillDirectories);
    }

    /**
     * 发布本地工具调用生命周期事件；事件结构与前端现有工具过程链路字段保持一致。
     * @param conversationId 会话标识。
     * @param toolCall 模型请求执行的工具调用。
     * @param phase start 或 complete。
     * @param startedAt 工具开始时间。
     * @param finishedAt 工具结束时间，开始事件为空。
     * @param toolResult 工具执行结果，开始事件为空。
     */
    private void publishLocalToolCallEvent(
        Long conversationId,
        AiToolCall toolCall,
        String phase,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        ChatToolExecutionResult toolResult
    ) {
        Map<String, Object> payload = baseLocalToolCallPayload(toolCall, phase, startedAt);
        if (StrUtil.equalsIgnoreCase(phase, "start")) {
            payload.put("reactThought", buildLocalToolReactThought(toolCall));
            payload.put("reactAction", buildLocalToolReactAction(toolCall));
        }
        if (finishedAt != null) {
            payload.put("finishedAt", finishedAt.toString());
        }
        if (toolResult != null) {
            payload.put("content", StrUtil.blankToDefault(toolResult.content(), ""));
            payload.put("rawResult", StrUtil.blankToDefault(toolResult.content(), ""));
            payload.put("reactObservation", buildLocalToolReactObservation("工具返回", toolResult.content()));
            if (toolResult.metadata() != null && !toolResult.metadata().isEmpty()) {
                payload.put("resultMetadata", toolResult.metadata());
            }
        }
        chatStreamPublisher.publishToolCall(conversationId, payload);
    }

    /**
     * 构造本地工具执行步骤的持久化元数据。
     * 业务意图：SSE 工具事件只覆盖在线流，刷新或历史回放需要从 chat_execution_step.metadata_json
     * 还原 callId、参数和结构化返回元数据，避免过程卡片退化成只有纯文本输出。
     *
     * @param toolCall 模型请求执行的工具调用。
     * @param toolStepDisplayName 持久化步骤展示名，通常来自工具规格 description。
     * @param toolResult 工具执行结果。
     * @return 可序列化到步骤 metadata_json 的结构化元数据。
     */
    private Map<String, Object> buildLocalToolStepMetadata(
        AiToolCall toolCall,
        String toolStepDisplayName,
        ChatToolExecutionResult toolResult
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("callId", StrUtil.blankToDefault(toolCall.callId(), "tool-call-" + System.nanoTime()));
        metadata.put("toolId", StrUtil.blankToDefault(toolCall.toolCode(), "unknown"));
        metadata.put("displayName", StrUtil.blankToDefault(toolStepDisplayName, resolveLocalToolDisplayName(toolCall)));
        metadata.put("input", StrUtil.blankToDefault(toolCall.arguments(), ""));
        metadata.put("params", parseToolCallParams(toolCall.arguments()));
        metadata.put("rawResult", toolResult == null ? "" : StrUtil.blankToDefault(toolResult.content(), ""));
        metadata.put("resultMetadata", toolResult == null || toolResult.metadata() == null ? Map.of() : toolResult.metadata());
        return metadata;
    }

    /**
     * 发布本地工具执行失败事件，保证前端能在最终错误前看到是哪一个工具失败。
     * @param conversationId 会话标识。
     * @param toolCall 模型请求执行的工具调用。
     * @param startedAt 工具开始时间。
     * @param exception 工具执行异常。
     */
    private void publishLocalToolCallError(
        Long conversationId,
        AiToolCall toolCall,
        LocalDateTime startedAt,
        RuntimeException exception
    ) {
        Map<String, Object> payload = baseLocalToolCallPayload(toolCall, "error", startedAt);
        String message = StrUtil.blankToDefault(exception.getMessage(), "工具执行失败");
        payload.put("finishedAt", LocalDateTime.now().toString());
        payload.put("content", message);
        payload.put("errorMessage", message);
        payload.put("reactObservation", buildLocalToolReactObservation("工具异常", message));
        chatStreamPublisher.publishToolCall(conversationId, payload);
    }

    /**
     * 构造本地工具事件公共字段；参数解析失败时保留原始字符串，避免坏 JSON 中断工具链路展示。
     * @param toolCall 模型请求执行的工具调用。
     * @param phase 当前工具阶段。
     * @param startedAt 工具开始时间。
     * @return 可直接发布的事件载荷。
     */
    private Map<String, Object> baseLocalToolCallPayload(AiToolCall toolCall, String phase, LocalDateTime startedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("callId", StrUtil.blankToDefault(toolCall.callId(), "tool-call-" + System.nanoTime()));
        payload.put("phase", phase);
        payload.put("toolId", StrUtil.blankToDefault(toolCall.toolCode(), "unknown"));
        payload.put("displayName", resolveLocalToolDisplayName(toolCall));
        payload.put("input", StrUtil.blankToDefault(toolCall.arguments(), ""));
        payload.put("params", parseToolCallParams(toolCall.arguments()));
        payload.put("startedAt", startedAt.toString());
        return payload;
    }

    /**
     * 构造可公开展示的 ReAct 思考摘要；该文案只描述工具选择原因，不暴露模型私有思维链。
     * @param toolCall 模型请求执行的工具调用。
     * @return ReAct 思考摘要。
     */
    private String buildLocalToolReactThought(AiToolCall toolCall) {
        return "需要调用 " + resolveLocalToolDisplayName(toolCall) + " 获取或处理当前问题所需的信息。";
    }

    /**
     * 构造 ReAct 行动摘要，供前端把结构化工具调用显示成“行动”节点。
     * @param toolCall 模型请求执行的工具调用。
     * @return ReAct 行动摘要。
     */
    private String buildLocalToolReactAction(AiToolCall toolCall) {
        return "调用 " + resolveLocalToolDisplayName(toolCall);
    }

    /**
     * 构造 ReAct 观察摘要，避免把超长工具输出直接挤占主消息区。
     * @param prefix 观察前缀。
     * @param content 工具输出或错误文案。
     * @return ReAct 观察摘要。
     */
    private String buildLocalToolReactObservation(String prefix, String content) {
        String normalizedContent = StrUtil.blankToDefault(content, "无输出").replaceAll("\\s+", " ").trim();
        String clippedContent = normalizedContent.length() > 160
            ? normalizedContent.substring(0, 160) + "..."
            : normalizedContent;
        return prefix + "：" + clippedContent;
    }

    /**
     * 解析持久化步骤展示名；历史过程卡片优先使用工具规格描述，无法匹配时退回工具编码。
     * @param toolCall 模型请求执行的工具调用。
     * @param toolSpecs 本轮模型可见工具规格。
     * @return 工具执行步骤展示名。
     */
    private String resolveLocalToolDisplayName(AiToolCall toolCall, List<ChatToolSpec> toolSpecs) {
        if (toolCall == null || CollUtil.isEmpty(toolSpecs)) {
            return resolveLocalToolDisplayName(toolCall);
        }
        return toolSpecs.stream()
            .filter(toolSpec -> StrUtil.equalsIgnoreCase(toolSpec.name(), toolCall.toolCode()))
            .map(ChatToolSpec::description)
            .filter(StrUtil::isNotBlank)
            .findFirst()
            .orElseGet(() -> resolveLocalToolDisplayName(toolCall));
    }

    /**
     * 解析本地工具展示名；当前工具 schema 尚未回传显示名时，使用工具编码保持稳定可追踪。
     * @param toolCall 模型请求执行的工具调用。
     * @return 工具展示名。
     */
    private String resolveLocalToolDisplayName(AiToolCall toolCall) {
        return StrUtil.blankToDefault(toolCall.toolCode(), "本地工具");
    }

    /**
     * 将模型工具参数解析为前端可折叠展示的结构化对象。
     * @param argumentsText 模型返回的工具参数。
     * @return 结构化参数或原始文本。
     */
    private Object parseToolCallParams(String argumentsText) {
        if (StrUtil.isBlank(argumentsText)) {
            return Map.of();
        }
        try {
            Object parsed = cn.hutool.json.JSONUtil.parse(argumentsText);
            if (parsed instanceof cn.hutool.json.JSONObject jsonObject) {
                return new LinkedHashMap<>(jsonObject);
            }
            return parsed;
        } catch (Exception ignored) {
            // 模型偶发返回非 JSON 参数时仍需展示原始输入，不能影响工具执行主流程。
            return argumentsText;
        }
    }

    /**
     * 解析工具参数进度；完整 JSON 优先，write/edit 的半截 JSON 走有限字段恢复。
     *
     * @param toolCallDelta 工具参数进度。
     * @return 前端可展示参数。
     */
    private Object parseToolCallProgressParams(AiToolCallDelta toolCallDelta) {
        String argumentsText = StrUtil.blankToDefault(toolCallDelta.accumulatedArguments(), "");
        Object parsed = parseToolCallParams(argumentsText);
        if (!(parsed instanceof String) || !isFileEditTool(toolCallDelta.toolCode())) {
            return parsed;
        }
        return parsePartialFileEditArguments(argumentsText);
    }

    /**
     * 从尚未闭合的 write/edit JSON 参数中恢复 path/content 字段。
     * 关键约束：这里只服务 UI 进度预览，不参与真实工具执行，因此宁可少展示，也不能抛异常中断聊天。
     *
     * @param argumentsText 已累积参数前缀。
     * @return 可展示参数或原始文本。
     */
    private Object parsePartialFileEditArguments(String argumentsText) {
        Map<String, Object> params = new LinkedHashMap<>();
        String path = firstNotBlank(
            extractJsonStringFieldPrefix(argumentsText, "path"),
            extractJsonStringFieldPrefix(argumentsText, "filePath"),
            extractJsonStringFieldPrefix(argumentsText, "targetPath"),
            extractJsonStringFieldPrefix(argumentsText, "filename")
        );
        String content = firstNotBlank(
            extractJsonStringFieldPrefix(argumentsText, "content"),
            extractJsonStringFieldPrefix(argumentsText, "newString"),
            extractJsonStringFieldPrefix(argumentsText, "patch")
        );
        if (StrUtil.isNotBlank(path)) {
            params.put("path", path);
        }
        if (StrUtil.isNotBlank(content)) {
            params.put("content", content);
        }
        return params.isEmpty() ? argumentsText : params;
    }

    /**
     * 从 JSON 字符串前缀中提取字段值，允许目标字符串尚未闭合。
     *
     * @param source JSON 前缀。
     * @param fieldName 字段名。
     * @return 字段值前缀。
     */
    private String extractJsonStringFieldPrefix(String source, String fieldName) {
        if (StrUtil.isBlank(source) || StrUtil.isBlank(fieldName)) {
            return "";
        }
        int fieldIndex = source.indexOf("\"" + fieldName + "\"");
        if (fieldIndex < 0) {
            return "";
        }
        int colonIndex = source.indexOf(':', fieldIndex + fieldName.length() + 2);
        if (colonIndex < 0) {
            return "";
        }
        int quoteIndex = source.indexOf('"', colonIndex + 1);
        if (quoteIndex < 0) {
            return "";
        }
        StringBuilder valueBuilder = new StringBuilder();
        boolean escaping = false;
        for (int index = quoteIndex + 1; index < source.length(); index++) {
            char current = source.charAt(index);
            if (escaping) {
                valueBuilder.append(unescapeJsonStringChar(current));
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            if (current == '"') {
                return valueBuilder.toString();
            }
            valueBuilder.append(current);
        }
        return valueBuilder.toString();
    }

    /**
     * 处理 JSON 字符串中常见转义字符，保证临时 diff 使用用户可读文本。
     *
     * @param current 转义字符。
     * @return 反转义后的字符。
     */
    private char unescapeJsonStringChar(char current) {
        return switch (current) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case '"' -> '"';
            case '\\' -> '\\';
            default -> current;
        };
    }

    /**
     * 判断工具是否属于文件编辑类，只有这些工具才需要从半截参数中恢复文件路径和内容。
     *
     * @param toolCode 工具编码。
     * @return 是否文件编辑工具。
     */
    private boolean isFileEditTool(String toolCode) {
        return StrUtil.equalsAnyIgnoreCase(toolCode, "write", "edit", "apply_patch");
    }

    /**
     * 返回首个非空字符串。
     *
     * @param values 候选值。
     * @return 首个非空值。
     */
    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    /**
     * 构造工具参数进度文案。
     *
     * @param toolCallDelta 工具参数进度。
     * @param progressParams 已解析参数。
     * @return 用户可读进度文案。
     */
    private String buildToolArgumentProgressText(AiToolCallDelta toolCallDelta, Object progressParams) {
        String path = extractProgressPath(progressParams);
        int argumentLength = StrUtil.length(toolCallDelta.accumulatedArguments());
        if (StrUtil.isNotBlank(path)) {
            return "正在生成 " + path + "，已接收 " + argumentLength + " 个字符";
        }
        return "正在准备 " + StrUtil.blankToDefault(toolCallDelta.toolCode(), "工具") + " 参数，已接收 " + argumentLength + " 个字符";
    }

    /**
     * 构造工具参数进度的行动摘要。
     *
     * @param toolCallDelta 工具参数进度。
     * @param progressParams 已解析参数。
     * @return 行动摘要。
     */
    private String buildLocalToolProgressAction(AiToolCallDelta toolCallDelta, Object progressParams) {
        String path = extractProgressPath(progressParams);
        if (isFileEditTool(toolCallDelta.toolCode()) && StrUtil.isNotBlank(path)) {
            return "正在编辑 " + path;
        }
        return "正在准备调用 " + StrUtil.blankToDefault(toolCallDelta.toolCode(), "工具");
    }

    /**
     * 从进度参数中提取文件路径。
     *
     * @param progressParams 已解析参数。
     * @return 文件路径。
     */
    private String extractProgressPath(Object progressParams) {
        if (!(progressParams instanceof Map<?, ?> params)) {
            return "";
        }
        Object path = Optional.ofNullable(params.get("path"))
            .orElseGet(() -> Optional.ofNullable(params.get("filePath"))
                .orElseGet(() -> Optional.ofNullable(params.get("targetPath")).orElse(params.get("filename"))));
        return path == null ? "" : StrUtil.trimToEmpty(String.valueOf(path));
    }

    /**
     * 解析本次工具调用应使用的工作目录。
     * @param command 当前消息命令。
     * @return 可用工作目录，缺失时返回 null。
     */
    private Path resolveToolWorkingDirectory(SendChatMessageCommand command) {
        if (StrUtil.isNotBlank(command.repositoryPath())) {
            Path explicitPath = normalizeExistingDirectory(command.repositoryPath());
            if (explicitPath != null) {
                return explicitPath;
            }
        }
        if (command.localOnly()) {
            return null;
        }
        ChatConversation conversation = chatConversationRepository.requireById(command.conversationId());
        if (conversation.getWorkspaceId() == null || chatWorkspaceBindingService == null) {
            return null;
        }
        Optional<Path> workspacePath = chatWorkspaceBindingService.findRepositoryPathByWorkspaceId(conversation.getWorkspaceId());
        return workspacePath.map(Path::toAbsolutePath).map(Path::normalize).orElse(null);
    }

    /**
     * 将路径规范化为存在的目录；非法路径只表示不能绑定工具目录，不应中断模型回答链路。
     * @param pathText 原始路径文本。
     * @return 规范化目录，非法时返回 null。
     */
    private Path normalizeExistingDirectory(String pathText) {
        Path path = Path.of(pathText).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            return null;
        }
        return path;
    }

    /**
     * 将本地工具结果转换为下一轮模型可消费证据。
     * @param toolResult 工具执行结果。
     * @return 系统证据文本。
     */
    private String buildLocalToolEvidenceContext(ChatToolExecutionResult toolResult) {
        String workingDirectorySection = "";
        String toolMetadataSection = "";
        if (toolResult.metadata() != null && !toolResult.metadata().isEmpty()) {
            String workingDirectory = Optional.ofNullable(toolResult.metadata().get("workingDirectory"))
                .map(String::valueOf)
                .filter(StrUtil::isNotBlank)
                .orElse(null);
            if (StrUtil.isNotBlank(workingDirectory)) {
                workingDirectorySection = "当前真实工作目录：" + workingDirectory + '\n'
                    + "路径约束：后续读写文件必须基于当前真实工作目录；优先使用相对路径，不要编造 C:\\workspace 等虚拟根目录。";
            }
            toolMetadataSection = "工具元数据："
                + normalizeEvidenceText(cn.hutool.json.JSONUtil.toJsonStr(toolResult.metadata()), 2000);
        }
        return promptTemplateLoader.render("local-tool-evidence-context", Map.of(
            "tool_code", StrUtil.blankToDefault(toolResult.toolCode(), "unknown"),
            "tool_output", normalizeEvidenceText(toolResult.content(), 4000),
            "working_directory_section", workingDirectorySection,
            "tool_metadata_section", toolMetadataSection
        )).trim();
    }

    /**
     * 构造本轮工具调用去重键，避免模型在同一参数下反复执行同一个本地工具。
     * @param toolCall 模型工具调用。
     * @return 去重键。
     */
    private String deduplicateToolCallKey(AiToolCall toolCall) {
        if (toolCall == null) {
            return "";
        }
        return StrUtil.blankToDefault(toolCall.toolCode(), "unknown") + "\n" + StrUtil.blankToDefault(toolCall.arguments(), "");
    }

    /**
     * 对重复非 write 工具调用切换到无工具最终生成，避免“重复已跳过”结果继续诱导模型发起同一 tool_call。
     * @param toolCall 当前重复工具调用。
     * @return 无工具最终生成阶段的系统收口指令。
     */
    private String buildRepeatedNonWriteToolFinalGuidance(AiToolCall toolCall) {
        return """
            重复本地工具调用已拦截：模型再次请求相同工具和参数，后端不会重复执行。
            被拦截工具：%s

            继续执行要求：
            - 上文已经包含该工具的真实执行结果，必须直接基于已有工具结果回答用户。
            - 当前最终生成阶段不再开放本地工具；不要再声明需要读取、搜索或执行同一工具。
            - 如果已有工具结果不足以完成用户请求，应说明缺少的具体信息，并给出基于现有信息的可执行结论。
            """.formatted(StrUtil.blankToDefault(toolCall == null ? null : toolCall.toolCode(), "unknown"));
    }

    /**
     * 对已成功写入的同一路径重复 write 做确定性收口。
     * 业务意图：模型可能在拿到“文件已写入”证据后继续微调同一个 content 字段；
     * 此时继续把“重复调用已跳过”回灌给模型容易形成循环，后端直接给出用户可见完成结果。
     *
     * @param previousToolResult 上一次成功写入结果。
     * @return 面向用户的完成答复。
     */
    private String buildRepeatedWriteFinalAnswer(ChatToolExecutionResult previousToolResult) {
        String path = Optional.ofNullable(previousToolResult.metadata())
            .map(metadata -> metadata.get("path"))
            .map(String::valueOf)
            .filter(StrUtil::isNotBlank)
            .orElse(null);
        if (StrUtil.isNotBlank(path)) {
            return "已完成，文件已写入 `" + path + "`。";
        }
        return "已完成，文件已写入。";
    }

    /**
     * 追加确定性助手正文并实时推送给前端，保持与普通模型 delta 相同的最终消息缓冲。
     *
     * @param conversationId 当前会话标识。
     * @param builder 最终助手消息正文缓冲。
     * @param delta 要追加的正文。
     */
    private void appendAssistantDelta(Long conversationId, StringBuilder builder, String delta) {
        if (StrUtil.isBlank(delta)) {
            return;
        }
        builder.append(delta);
        chatStreamPublisher.publishAssistantDelta(conversationId, delta);
    }

    /**
     * 获取 Agent Loop 协调器。
     * 兼容约束：部分旧单测通过 Mockito 构造服务，新增依赖可能未显式注入；这里用默认实例保持旧测试稳定。
     *
     * @return 可用的 Agent Loop 协调器。
     */
    private AgentLoopCoordinator resolveAgentLoopCoordinator() {
        return agentLoopCoordinator == null ? new AgentLoopCoordinator() : agentLoopCoordinator;
    }

    private void recordExecutionOutcome(
        ChatConversation conversation,
        Long requestMessageId,
        Long responseMessageId,
        String intentCode,
        boolean searchEnabled,
        boolean artifactEnabled,
        ChatMessageStatus status,
        String errorMessage
    ) {
        Long runId = currentRunId(conversation.getId());
        ChatExecutionRun run = ChatExecutionRun.builder()
            .id(runId)
            .conversationId(conversation.getId())
            .requestMessageId(requestMessageId)
            .responseMessageId(responseMessageId)
            .taskId(runId)
            .intentCode(intentCode)
            .status(status.name())
            .queueStatus("ACQUIRED")
            .searchEnabled(searchEnabled)
            .artifactEnabled(artifactEnabled)
            .errorMessage(errorMessage)
            .startedAt(conversation.getLastMessageAt())
            .createdAt(conversation.getLastMessageAt())
            .finishedAt(java.time.LocalDateTime.now())
            .updatedAt(java.time.LocalDateTime.now())
            .build();
        chatExecutionRunRepository.save(run);
        if (status == ChatMessageStatus.COMPLETED) {
            triggerGovernanceHook(
                "TASK_COMPLETED",
                conversation.getId(),
                runId,
                null,
                "任务完成，意图=" + StrUtil.blankToDefault(intentCode, "unknown")
            );
        }
        log.info(
            "执行收口: runId={}, 会话={}, 状态={}, 意图={}, 搜索={}, 产物={}, 错误={}",
            runId,
            conversation.getId(),
            status,
            intentCode,
            searchEnabled,
            artifactEnabled,
            StrUtil.maxLength(errorMessage, 120)
        );
    }

    /**
     * 按当前 run 主键收口 Trace，保证真实链路不会永久停留在 RUNNING。
     * @param runId 运行标识。
     * @param status Trace 终态。
     * @param errorMessage 错误信息。
     */
    private void finishTrace(Long runId, String status, String errorMessage) {
        ChatTraceRun traceRun = ConversationTraceContext.current();
        if (traceRun == null) {
            return;
        }
        conversationTraceRecordService.finishTrace(traceRun.getTraceId(), runId, status, errorMessage);
    }

    /**
     * 构造模型可消费的历史消息副本，剥离用户消息开头的 @skill 标记。
     * @param history 原始历史。
     * @return 模型输入历史。
     */
    private List<ChatMessage> plainAiMessages(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        return history.stream()
            .map(ChatCapabilityMentionSupport::toPlainAiMessage)
            .toList();
    }

    /**
     * 构造真正送入模型的历史消息，剥离历史中已落库的内部工具执行叙述。
     * 业务约束：用户仍能在界面看到旧消息，但模型不能继续把“现在执行/准备执行”当成上轮已确认计划，
     * 否则用户回复“同意/继续”会再次进入 read/grep 循环并触发工具轮次上限。
     *
     * @param history 原始历史。
     * @return 模型可见历史。
     */
    private List<ChatMessage> modelVisibleAiMessages(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        return history.stream()
            .map(ChatCapabilityMentionSupport::toPlainAiMessage)
            .filter(message -> !isPersistedInternalToolNarration(message))
            .toList();
    }

    /**
     * 判断历史助手消息是否为之前错误落库的内部工具执行叙述。
     *
     * @param message 历史消息。
     * @return true 表示该消息不应进入后续模型上下文。
     */
    private boolean isPersistedInternalToolNarration(ChatMessage message) {
        if (message == null || message.getRole() != ChatMessageRole.ASSISTANT || StrUtil.isBlank(message.getContent())) {
            return false;
        }
        String normalizedContent = message.getContent().replaceAll("\\s+", "");
        boolean hasPersistedExecutionMarker = normalizedContent.contains("现在执行")
            || normalizedContent.contains("准备执行")
            || normalizedContent.contains("确认动作")
            || normalizedContent.contains("执行命令");
        return hasPersistedExecutionMarker
            && (isExecutionNarrationOnlyToolRoundContent(message.getContent())
                || isCommandPlanOnlyToolRoundContent(message.getContent()));
    }

    /**
     * 提取纯用户问题列表，避免 rewrite/intent 把 @skill 当成自然语言。
     * @param history 原始历史。
     * @return 纯文本用户问题。
     */
    private List<String> plainUserContents(List<ChatMessage> history) {
        return plainAiMessages(history).stream()
            .filter(message -> message.getRole() == ChatMessageRole.USER)
            .map(ChatMessage::getContent)
            .toList();
    }

    /**
     * 为系统意图补充专用 system prompt，其余链路沿用原始会话历史。
     * @param history 原始会话历史。
     * @param intentDecision 意图决策。
     * @param conversationId 会话标识。
     * @return 送入模型层的实际历史。
     */
    private List<ChatMessage> buildAiHistory(
        List<ChatMessage> history,
        ConversationIntentDecision intentDecision,
        Long conversationId,
        List<String> selectedSkillCodes,
        String selectedExpertCode,
        List<SearchReferenceCandidate> searchReferences,
        String governanceContext,
        boolean deepThinking,
        boolean planMode
    ) {
        String systemPrompt = resolveSystemPromptFromIntent(intentDecision);
        String planModeContext = buildPlanModeContext(planMode);
        String expertContext = chatExpertContextService.buildExpertContext(selectedExpertCode);
        String skillContext = chatSkillContextService.buildSkillContext(selectedSkillCodes);
        String searchEvidenceContext = buildSearchEvidenceContext(intentDecision, searchReferences);
        String chineseThinkingGuidance = buildChineseThinkingGuidance(deepThinking);
        if (
            StrUtil.isBlank(systemPrompt)
            && StrUtil.isBlank(planModeContext)
            && StrUtil.isBlank(expertContext)
            && StrUtil.isBlank(skillContext)
            && StrUtil.isBlank(searchEvidenceContext)
            && StrUtil.isBlank(governanceContext)
            && StrUtil.isBlank(chineseThinkingGuidance)
        ) {
            return modelVisibleAiMessages(history);
        }
        List<String> promptSegments = new ArrayList<>();
        if (StrUtil.isNotBlank(systemPrompt)) {
            promptSegments.add(systemPrompt);
        }
        if (StrUtil.isNotBlank(chineseThinkingGuidance)) {
            promptSegments.add(chineseThinkingGuidance);
        }
        if (StrUtil.isNotBlank(planModeContext)) {
            promptSegments.add(planModeContext);
        }
        if (StrUtil.isNotBlank(searchEvidenceContext)) {
            promptSegments.add(searchEvidenceContext);
        }
        if (StrUtil.isNotBlank(governanceContext)) {
            promptSegments.add(governanceContext);
        }
        if (StrUtil.isNotBlank(expertContext)) {
            promptSegments.add(expertContext);
        }
        if (StrUtil.isNotBlank(skillContext)) {
            promptSegments.add(skillContext);
        }
        String composedSystemPrompt = String.join("\n\n", promptSegments);
        List<ChatMessage> aiHistory = new ArrayList<>();
        aiHistory.add(ChatMessage.create(
            cn.hutool.core.util.IdUtil.getSnowflakeNextId(),
            conversationId,
            ChatMessageRole.SYSTEM,
            composedSystemPrompt,
            ChatMessageStatus.COMPLETED,
            null,
            null,
            null
        ));
        aiHistory.addAll(modelVisibleAiMessages(history));
        return aiHistory;
    }

    /**
     * 深度思考模式下统一约束模型把对外可见 reasoning 输出为中文，避免前端展示英文思考。
     * @param deepThinking 是否开启深度思考。
     * @return 可注入模型的中文思考约束片段。
     */
    private String buildChineseThinkingGuidance(boolean deepThinking) {
        return deepThinking ? StrUtil.trim(promptTemplateLoader.load("deep-thinking-language-guidance")) : "";
    }

    /**
     * 构造规划/目标模式运行约束，统一覆盖 CLI 与桌面端入口。
     * @param planMode 是否开启规划/目标模式。
     * @return 可注入模型的系统提示片段。
     */
    private String buildPlanModeContext(boolean planMode) {
        if (!planMode) {
            return "";
        }
        // 目标模式提示词由资源文件维护，避免在流程编排代码里散落长文本业务约束。
        return StrUtil.trim(promptTemplateLoader.load("plan-mode-goal-context"));
    }

    /**
     * 把联网检索候选转换成系统证据上下文，确保模型优先基于实时来源作答。
     * @param intentDecision 意图决策。
     * @param references 搜索来源候选。
     * @return 可注入模型 System 消息的证据上下文。
     */
    private String buildSearchEvidenceContext(
        ConversationIntentDecision intentDecision,
        List<SearchReferenceCandidate> references
    ) {
        if (intentDecision.action() != ConversationIntentAction.SEARCH || references == null || references.isEmpty()) {
            return "";
        }
        StringBuilder searchResultsBuilder = new StringBuilder();
        int rank = 1;
        for (SearchReferenceCandidate reference : references) {
            searchResultsBuilder.append(rank++).append(". ");
            searchResultsBuilder.append("标题：").append(normalizeEvidenceText(reference.title(), 120));
            if (StrUtil.isNotBlank(reference.siteName())) {
                searchResultsBuilder.append("；站点：").append(normalizeEvidenceText(reference.siteName(), 80));
            }
            if (StrUtil.isNotBlank(reference.url())) {
                searchResultsBuilder.append("；链接：").append(normalizeEvidenceText(reference.url(), 300));
            }
            if (StrUtil.isNotBlank(reference.snippet())) {
                searchResultsBuilder.append("；摘要：").append(normalizeEvidenceText(reference.snippet(), 220));
            }
            searchResultsBuilder.append('\n');
        }
        return promptTemplateLoader.render("search-evidence-context", Map.of(
            "current_date", DateUtil.today(),
            "search_results", searchResultsBuilder.toString().trim()
        )).trim();
    }

    /**
     * 将工具原始结果转换为模型后续回答可消费的系统证据，保证“工具完成后再整理回答”。
     * @param toolResult 工具执行结果。
     * @return 可注入模型上下文的证据文本。
     */
    private String buildToolEvidenceContext(ChatMcpToolResult toolResult) {
        String toolMetadataSection = "";
        if (toolResult.metadata() != null && !toolResult.metadata().isEmpty()) {
            toolMetadataSection = "工具元数据：\n"
                + normalizeEvidenceText(cn.hutool.json.JSONUtil.toJsonStr(toolResult.metadata()), 2000);
        }
        return promptTemplateLoader.render("tool-evidence-context", Map.of(
            "tool_id", StrUtil.blankToDefault(toolResult.toolId(), "unknown"),
            "tool_result", normalizeEvidenceText(toolResult.content(), 4000),
            "tool_metadata_section", toolMetadataSection
        )).trim();
    }

    /**
     * 规整搜索证据文本，避免换行和超长内容污染系统提示。
     * @param text 原始文本。
     * @param maxLength 最大长度。
     * @return 规整后文本。
     */
    private String normalizeEvidenceText(String text, int maxLength) {
        String normalized = StrUtil.blankToDefault(text, "")
            .replace('\r', ' ')
            .replace('\n', ' ')
            .trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return StrUtil.sub(normalized, 0, maxLength);
    }

    /**
     * 根据意图解析系统提示，未命中 system 意图时返回空字符串。
     * @param intentDecision 意图决策。
     * @return 系统提示。
     */
    private String resolveSystemPromptFromIntent(ConversationIntentDecision intentDecision) {
        if (intentDecision.intentCode() == null) {
            return "";
        }
        com.codingx.chat.domain.model.ChatIntentNode intentNode = chatIntentNodeRepository.findByIntentCode(intentDecision.intentCode());
        if (intentNode == null || !"system".equalsIgnoreCase(intentNode.getIntentType())) {
            return "";
        }
        return resolveSystemPrompt(intentNode);
    }

    /**
     * 系统意图优先使用节点自定义 Prompt，未配置时回退到全局系统模板。
     * @param intentNode 命中的系统节点。
     * @return 最终送入模型的 system prompt。
     */
    private String resolveSystemPrompt(com.codingx.chat.domain.model.ChatIntentNode intentNode) {
        if (StrUtil.isNotBlank(intentNode.getPromptTemplate())) {
            return intentNode.getPromptTemplate().trim();
        }
        String basePrompt = promptTemplateLoader.load("answer-chat-system");
        if (StrUtil.isNotBlank(intentNode.getPromptSnippet())) {
            return basePrompt + "\n\n# 节点补充规则\n" + intentNode.getPromptSnippet().trim();
        }
        return basePrompt;
    }

    /**
     * 子问题与其独立意图决策的绑定结果。
     * 业务意图：对齐 ragent 的“先拆分、再逐题识别”，避免整体搜索意图吞掉天气等 MCP 子问题。
     */
    private record SubQuestionIntentDecision(String question, ConversationIntentDecision intentDecision) {
    }

    /**
     * 从改写结果提取实际参与路由的问题列表，并逐题调用意图服务。
     * @param rewriteResult 改写与拆分结果。
     * @param mcpEnabled 本轮是否启用了 MCP。
     * @return 子问题级意图决策。
     */
    private List<SubQuestionIntentDecision> resolveSubQuestionDecisions(
        ConversationRewriteResult rewriteResult,
        boolean mcpEnabled
    ) {
        List<String> questions = routedQuestions(rewriteResult);
        List<SubQuestionIntentDecision> decisions = new ArrayList<>();
        for (String question : questions) {
            ConversationIntentDecision decision = conversationIntentService.route(question, mcpEnabled);
            decisions.add(new SubQuestionIntentDecision(question, decision));
        }
        return decisions;
    }

    /**
     * 拆分结果为空时回退到改写问题，保证单问题链路和旧数据兼容。
     */
    private List<String> routedQuestions(ConversationRewriteResult rewriteResult) {
        List<String> candidates = rewriteResult.shouldSplit() && CollUtil.isNotEmpty(rewriteResult.subQuestions())
            ? rewriteResult.subQuestions()
            : List.of(rewriteResult.rewrite());
        List<String> questions = candidates.stream()
            .filter(StrUtil::isNotBlank)
            .map(String::trim)
            .toList();
        if (CollUtil.isNotEmpty(questions)) {
            return questions;
        }
        return List.of(StrUtil.blankToDefault(rewriteResult.rewrite(), ""));
    }

    /**
     * 已选技能下的短指代问题不能再套用“关于助手”系统意图，否则模型会被系统介绍 Prompt 带回能力说明。
     * 业务约束：这里只降级意图和系统 Prompt；是否缺少执行目标由技能上下文约束模型解释或追问。
     */
    private List<SubQuestionIntentDecision> normalizeSelectedSkillShortQuestionDecisions(
        List<SubQuestionIntentDecision> decisions,
        List<String> selectedSkillCodes
    ) {
        if (CollUtil.isEmpty(decisions) || CollUtil.isEmpty(selectedSkillCodes)) {
            return decisions;
        }
        return decisions.stream()
            .map(decision -> new SubQuestionIntentDecision(
                decision.question(),
                normalizeSelectedSkillShortQuestionDecision(decision.question(), decision.intentDecision(), selectedSkillCodes)
            ))
            .toList();
    }

    /**
     * 单条子问题的技能短指代归一化，保留搜索/MCP/澄清等真实业务分支，只处理 system DIRECT 误判。
     */
    private ConversationIntentDecision normalizeSelectedSkillShortQuestionDecision(
        String question,
        ConversationIntentDecision decision,
        List<String> selectedSkillCodes
    ) {
        if (
            decision == null
                || CollUtil.isEmpty(selectedSkillCodes)
                || decision.action() != ConversationIntentAction.DIRECT
                || !isSystemIntentCode(decision.intentCode())
                || !isSelectedSkillShortReferenceQuestion(question)
        ) {
            return decision;
        }
        log.info(
            "技能短句意图降级: 原意图={}, 问题={}, 技能={}",
            decision.intentCode(),
            logPreview(question),
            selectedSkillCodes
        );
        return new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null);
    }

    /**
     * 判断意图编码是否属于系统介绍类，避免已选技能短句复用系统自我介绍 Prompt。
     */
    private boolean isSystemIntentCode(String intentCode) {
        return StrUtil.isNotBlank(intentCode)
            && (
                StrUtil.startWithIgnoreCase(intentCode, "sys-")
                    || StrUtil.startWithIgnoreCase(intentCode, "system-")
            );
    }

    /**
     * 识别“这是什么/这是啥/有什么用”等短指代问法，范围保持收窄，避免改写普通任务问题。
     */
    private boolean isSelectedSkillShortReferenceQuestion(String question) {
        String normalizedQuestion = StrUtil.blankToDefault(question, "")
            .replaceAll("[\\p{Punct}\\s，。？！、：；“”‘’（）【】《》]+", "")
            .toLowerCase(java.util.Locale.ROOT);
        return StrUtil.equalsAny(
            normalizedQuestion,
            "这是什么",
            "这是啥",
            "这啥",
            "这个是什么",
            "这个是啥",
            "它是什么",
            "它是啥",
            "介绍一下",
            "介绍下",
            "有什么用",
            "这个有什么用",
            "这是干什么的",
            "这个是干什么的"
        );
    }

    /**
     * 选择本轮落库和系统提示使用的主意图；搜索优先，确保混合问题保留联网证据约束。
     */
    private ConversationIntentDecision primaryIntentDecision(List<SubQuestionIntentDecision> decisions) {
        return decisions.stream()
            .filter(decision -> decision.intentDecision().action() == ConversationIntentAction.SEARCH)
            .map(SubQuestionIntentDecision::intentDecision)
            .findFirst()
            .or(() -> decisions.stream()
                .filter(decision -> decision.intentDecision().action() == ConversationIntentAction.MCP)
                .map(SubQuestionIntentDecision::intentDecision)
                .findFirst())
            .orElseGet(() -> decisions.getFirst().intentDecision());
    }

    /**
     * 查找首个指定动作的子问题决策，用于澄清和 MCP 未启用等短路分支。
     */
    private Optional<SubQuestionIntentDecision> firstDecisionWithAction(
        List<SubQuestionIntentDecision> decisions,
        ConversationIntentAction action
    ) {
        return decisions.stream()
            .filter(decision -> decision.intentDecision().action() == action)
            .findFirst();
    }

    /**
     * 单问题直答保持原有短路行为；混合问题中的直答子项交给最终模型综合处理。
     */
    private boolean isSingleDirectReply(List<SubQuestionIntentDecision> decisions) {
        if (decisions.size() != 1) {
            return false;
        }
        ConversationIntentDecision decision = decisions.getFirst().intentDecision();
        return decision.action() == ConversationIntentAction.DIRECT && StrUtil.isNotBlank(decision.reply());
    }

    /**
     * 提前校验所有 MCP 子问题，避免已经执行部分搜索或工具后才发现本轮未启用对应 MCP。
     */
    private Optional<SubQuestionIntentDecision> firstUnavailableMcpDecision(
        List<SubQuestionIntentDecision> decisions,
        List<String> selectedMcpCodes
    ) {
        for (SubQuestionIntentDecision decision : decisions) {
            if (decision.intentDecision().action() != ConversationIntentAction.MCP) {
                continue;
            }
            ChatIntentNode intentNode = resolveMcpIntentNode(decision.intentDecision());
            if (!isMcpEnabledForCurrentMessage(intentNode.getMcpToolId(), selectedMcpCodes)) {
                return Optional.of(decision);
            }
        }
        return Optional.empty();
    }

    /**
     * 提取所有搜索子问题，天气等 MCP 子问题不会再进入网页搜索。
     */
    private List<String> searchQuestions(List<SubQuestionIntentDecision> decisions) {
        return decisions.stream()
            .filter(decision -> decision.intentDecision().action() == ConversationIntentAction.SEARCH)
            .map(SubQuestionIntentDecision::question)
            .filter(StrUtil::isNotBlank)
            .toList();
    }

    /**
     * 代码产物续写短句容易被通用搜索意图误判；这里在执行搜索前做窄口径降级。
     * 业务约束：只有当前问题是短编辑指令、没有显式搜索/时效诉求，且历史能证明存在代码或文件产物时才降级。
     */
    private List<SubQuestionIntentDecision> suppressCodeArtifactFollowUpSearchDecisions(
        List<SubQuestionIntentDecision> decisions,
        String originalQuestion,
        List<ChatMessage> history
    ) {
        if (CollUtil.isEmpty(decisions)) {
            return decisions;
        }
        boolean hasCodeArtifactContext = historyHasCodeArtifactSignal(history);
        boolean explicitSearchIntent = hasExplicitSearchOrFreshnessIntent(originalQuestion);
        if (!hasCodeArtifactContext || explicitSearchIntent) {
            return decisions;
        }
        return decisions.stream()
            .map(decision -> suppressCodeArtifactFollowUpSearchDecision(decision, originalQuestion))
            .toList();
    }

    /**
     * 单条子问题级降级，避免混合问题中的其他搜索或 MCP 子问题被误伤。
     */
    private SubQuestionIntentDecision suppressCodeArtifactFollowUpSearchDecision(
        SubQuestionIntentDecision decision,
        String originalQuestion
    ) {
        if (
            decision.intentDecision().action() != ConversationIntentAction.SEARCH
                || hasExplicitSearchOrFreshnessIntent(decision.question())
                || !isCodeArtifactFollowUpQuestion(originalQuestion, decision.question())
        ) {
            return decision;
        }
        log.info(
            "代码产物续写搜索降级: 原意图={}, 原问题={}, 子问题={}",
            decision.intentDecision().intentCode(),
            logPreview(originalQuestion),
            logPreview(decision.question())
        );
        return new SubQuestionIntentDecision(
            decision.question(),
            new ConversationIntentDecision(
                decision.intentDecision().intentCode(),
                ConversationIntentAction.DIRECT,
                null
            )
        );
    }

    /**
     * 识别“丰富一下/继续优化/美化一下”等依赖上一轮产物的短编辑指令。
     */
    private boolean isCodeArtifactFollowUpQuestion(String originalQuestion, String routedQuestion) {
        return isShortCodeArtifactEditQuestion(originalQuestion) || isShortCodeArtifactEditQuestion(routedQuestion);
    }

    /**
     * 短句集合保持保守，避免把真实开放问题误降级为普通直答。
     */
    private boolean isShortCodeArtifactEditQuestion(String question) {
        String normalizedQuestion = normalizeCompactText(question);
        if (StrUtil.isBlank(normalizedQuestion)) {
            return false;
        }
        if (StrUtil.equalsAny(
            normalizedQuestion,
            "丰富一下",
            "丰富下",
            "再丰富一下",
            "继续丰富",
            "完善一下",
            "完善下",
            "继续完善",
            "优化一下",
            "优化下",
            "继续优化",
            "扩展一下",
            "扩展下",
            "增强一下",
            "增强下",
            "美化一下",
            "美化下",
            "改好看点",
            "加点内容",
            "多加点内容",
            "继续",
            "接着来",
            "接着写",
            "继续写"
        )) {
            return true;
        }
        // 兼容模型把“丰富一下”改写成“丰富页面/优化页面”等短表达，超过长度阈值交回正常意图链路。
        return normalizedQuestion.length() <= 12
            && (
                StrUtil.startWithAny(normalizedQuestion, "丰富", "完善", "优化", "扩展", "增强", "美化")
                    || StrUtil.endWithAny(normalizedQuestion, "好看点", "丰富点", "完善点", "优化点")
            );
    }

    /**
     * 当前问题显式要求检索或时效信息时，必须保留搜索链路。
     */
    private boolean hasExplicitSearchOrFreshnessIntent(String question) {
        String normalizedQuestion = normalizeCompactText(question);
        if (StrUtil.isBlank(normalizedQuestion)) {
            return false;
        }
        return StrUtil.containsAny(
            normalizedQuestion,
            "搜索",
            "搜一下",
            "帮我搜",
            "查询",
            "查一下",
            "帮我查",
            "最新",
            "今天",
            "当前",
            "现在",
            "联网",
            "网页",
            "新闻",
            "资料",
            "版本"
        );
    }

    /**
     * 历史中出现代码块、HTML 文件、补丁或本地工具产物时，说明短句更可能是在续写上一轮代码成果。
     */
    private boolean historyHasCodeArtifactSignal(List<ChatMessage> history) {
        if (CollUtil.isEmpty(history)) {
            return false;
        }
        return history.stream()
            // 只把助手或系统消息视为已产生的代码产物证据，避免用户上一轮提到“代码”就误触发续写保护。
            .filter(message -> message.getRole() != ChatMessageRole.USER)
            .map(ChatMessage::getContent)
            .filter(StrUtil::isNotBlank)
            .anyMatch(this::hasCodeArtifactSignal);
    }

    /**
     * 识别代码产物信号；使用关键词而不读取文件系统，避免聊天路由阶段产生额外 IO。
     */
    private boolean hasCodeArtifactSignal(String content) {
        String lowerContent = StrUtil.blankToDefault(content, "").toLowerCase(java.util.Locale.ROOT);
        String compactContent = normalizeCompactText(content);
        return StrUtil.containsAny(
            lowerContent,
            "```html",
            "```css",
            "```javascript",
            "```typescript",
            "index.html",
            ".html",
            ".css",
            ".js",
            "apply_patch",
            "shell_command"
        ) || StrUtil.containsAny(
            compactContent,
            "文件已写入",
            "文件已更新",
            "文件已创建",
            "已写入",
            "已更新",
            "已创建",
            "html页面",
            "代码",
            "补丁"
        );
    }

    /**
     * 压缩标点、空白和中文符号，统一短句判断入口。
     */
    private String normalizeCompactText(String value) {
        return StrUtil.blankToDefault(value, "")
            .replaceAll("[\\p{Punct}\\s，。？！、：；“”‘’（）【】《》]+", "")
            .toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 在执行阶段统一拦截自动搜索，保证系统总开关关闭时不会再落搜索步骤。
     * 关键约束：显式选择 web-access 不能禁用 CodingX 系统搜索；搜索类问题仍要先拿真实搜索证据，
     * 再把证据和技能上下文一起交给模型总结，避免模型只输出“正在搜索”过程文案。
     * @param decisions 子问题意图决策。
     * @return 搜索决策被降级后的子问题意图决策。
     */
    private List<SubQuestionIntentDecision> suppressAutomaticSearchDecisions(
        List<SubQuestionIntentDecision> decisions
    ) {
        if (CollUtil.isEmpty(decisions)) {
            return decisions;
        }
        boolean hasSearchDecision = decisions.stream()
            .anyMatch(decision -> decision.intentDecision().action() == ConversationIntentAction.SEARCH);
        if (!hasSearchDecision) {
            return decisions;
        }
        String disabledReason = automaticSearchDisabledReason();
        if (StrUtil.isBlank(disabledReason)) {
            return decisions;
        }
        return decisions.stream()
            .map(decision -> {
                if (decision.intentDecision().action() != ConversationIntentAction.SEARCH) {
                    return decision;
                }
                log.info(
                    "搜索决策跳过: 原因={}, 子问题={}, 意图={}",
                    disabledReason,
                    logPreview(decision.question()),
                    decision.intentDecision().intentCode()
                );
                return new SubQuestionIntentDecision(
                    decision.question(),
                    new ConversationIntentDecision(
                        decision.intentDecision().intentCode(),
                        ConversationIntentAction.DIRECT,
                        null
                    )
                );
            })
            .toList();
    }

    /**
     * 计算本轮自动搜索禁用原因；返回空字符串表示允许系统搜索链路执行。
     */
    private String automaticSearchDisabledReason() {
        if (!runtimeSettingService.webSearchEnabled()) {
            return "系统联网搜索已关闭";
        }
        return "";
    }

    /**
     * 顺序执行 MCP 子问题，返回下一条执行步骤应使用的序号。
     */
    private long executeMcpDecisions(
        List<SubQuestionIntentDecision> decisions,
        SendChatMessageCommand command,
        Long runId,
        List<ChatMessage> history,
        long startSequenceNo
    ) {
        long sequenceNo = startSequenceNo;
        for (SubQuestionIntentDecision decision : decisions) {
            if (decision.intentDecision().action() != ConversationIntentAction.MCP) {
                continue;
            }
            executeMcpDecision(decision, command, runId, history, sequenceNo++);
        }
        return sequenceNo;
    }

    /**
     * 执行单个 MCP 子问题并发布兼容的 mcp-call 与 step 事件。
     */
    private void executeMcpDecision(
        SubQuestionIntentDecision subQuestionDecision,
        SendChatMessageCommand command,
        Long runId,
        List<ChatMessage> history,
        long sequenceNo
    ) {
        ConversationIntentDecision intentDecision = subQuestionDecision.intentDecision();
        String question = subQuestionDecision.question();
        ChatIntentNode intentNode = resolveMcpIntentNode(intentDecision);
        Long mcpCallId = IdUtil.getSnowflakeNextId();
        log.info(
            "MCP执行: 工具={}, 问题={}",
            intentNode.getMcpToolId(),
            logPreview(question)
        );
        LocalDateTime mcpStartedAt = LocalDateTime.now();
        Map<String, Object> mcpParams = new LinkedHashMap<>();
        mcpParams.put("question", question);
        if (StrUtil.isNotBlank(intentDecision.intentCode())) {
            mcpParams.put("intentCode", intentDecision.intentCode());
        }
        Map<String, Object> mcpStartPayload = new LinkedHashMap<>();
        mcpStartPayload.put("callId", String.valueOf(mcpCallId));
        mcpStartPayload.put("phase", "start");
        mcpStartPayload.put("toolId", intentNode.getMcpToolId());
        mcpStartPayload.put("displayName", resolveMcpDisplayName(intentNode.getMcpToolId()));
        mcpStartPayload.put("params", mcpParams);
        mcpStartPayload.put("startedAt", mcpStartedAt.toString());
        // 兼容旧前端字段：即便在开始阶段也保持 input 可回显。
        mcpStartPayload.put("input", question);
        chatStreamPublisher.publishMcpCall(command.conversationId(), mcpStartPayload);
        ChatMcpProgressListener progressListener = (stage, message, detail) -> {
            Map<String, Object> mcpProgressPayload = new LinkedHashMap<>();
            mcpProgressPayload.put("callId", String.valueOf(mcpCallId));
            mcpProgressPayload.put("phase", "progress");
            mcpProgressPayload.put("toolId", intentNode.getMcpToolId());
            mcpProgressPayload.put("displayName", resolveMcpDisplayName(intentNode.getMcpToolId()));
            mcpProgressPayload.put("params", mcpParams);
            mcpProgressPayload.put("progressStage", stage);
            mcpProgressPayload.put("progressText", message);
            mcpProgressPayload.put("progressDetail", detail == null ? Map.of() : detail);
            mcpProgressPayload.put("updatedAt", LocalDateTime.now().toString());
            // 兼容旧前端字段，保留 input 以便回显本次提问。
            mcpProgressPayload.put("input", question);
            chatStreamPublisher.publishMcpCall(command.conversationId(), mcpProgressPayload);
        };
        ChatMcpToolResult toolResult = chatMcpExecutionService.execute(
            intentNode.getMcpToolId(),
            question,
            progressListener
        );
        log.info(
            "MCP完成: 工具={}, 输出长度={}",
            toolResult.toolId(),
            StrUtil.length(toolResult.content())
        );
        ChatExecutionStep mcpStep = ChatExecutionStep.builder()
            .id(IdUtil.getSnowflakeNextId())
            .runId(runId)
            .stepType("mcp")
            .stepTitle("执行 MCP 工具")
            .stepStatus("COMPLETED")
            .sequenceNo(sequenceNo)
            .content(toolResult.content())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        chatExecutionStepRepository.save(mcpStep);
        chatStreamPublisher.publishStep(command.conversationId(), Map.of(
            "id", mcpStep.getId(),
            "runId", mcpStep.getRunId(),
            "stepType", mcpStep.getStepType(),
            "stepTitle", mcpStep.getStepTitle(),
            "stepStatus", mcpStep.getStepStatus(),
            "sequenceNo", mcpStep.getSequenceNo(),
            "content", mcpStep.getContent()
        ));
        Map<String, Object> mcpCompletePayload = new LinkedHashMap<>();
        mcpCompletePayload.put("callId", String.valueOf(mcpCallId));
        mcpCompletePayload.put("phase", "complete");
        mcpCompletePayload.put("toolId", toolResult.toolId());
        mcpCompletePayload.put("displayName", resolveMcpDisplayName(toolResult.toolId()));
        mcpCompletePayload.put("params", mcpParams);
        mcpCompletePayload.put("rawResult", toolResult.content());
        mcpCompletePayload.put("resultMetadata", toolResult.metadata() == null ? Map.of() : toolResult.metadata());
        mcpCompletePayload.put("finishedAt", LocalDateTime.now().toString());
        // 兼容旧前端字段：继续保留 input/content/metadata。
        mcpCompletePayload.put("input", question);
        mcpCompletePayload.put("content", toolResult.content());
        mcpCompletePayload.put("metadata", toolResult.metadata() == null ? Map.of() : toolResult.metadata());
        chatStreamPublisher.publishMcpCall(command.conversationId(), mcpCompletePayload);
        history.add(ChatMessage.create(
            IdUtil.getSnowflakeNextId(),
            command.conversationId(),
            ChatMessageRole.SYSTEM,
            buildToolEvidenceContext(toolResult),
            ChatMessageStatus.COMPLETED,
            null,
            null,
            null
        ).attachRun(runId));
    }

    /**
     * 读取 MCP 意图节点，并集中处理缺失配置的异常文案。
     */
    private ChatIntentNode resolveMcpIntentNode(ConversationIntentDecision intentDecision) {
        ChatIntentNode intentNode = chatIntentNodeRepository.findByIntentCode(intentDecision.intentCode());
        if (intentNode == null || StrUtil.isBlank(intentNode.getMcpToolId())) {
            throw new IllegalStateException(
                ErrorMessageCatalog.CHAT_MCP_TOOL_CONFIG_MISSING + "，意图编码: " + intentDecision.intentCode()
            );
        }
        return intentNode;
    }

    /**
     * 按子问题并行执行搜索，并为每个子问题推送独立步骤事件。
     * @param searchQuestions 搜索问题集合。
     * @param runId 运行标识。
     * @param conversationId 会话标识。
     * @return 合并去重后的来源候选。
     */
    private List<SearchReferenceCandidate> executeSearchQuestions(List<String> searchQuestions, Long runId, Long conversationId) {
        return executeSearchQuestions(searchQuestions, runId, conversationId, 1L);
    }

    /**
     * 按指定起始序号执行搜索，确保混合 MCP + 搜索时过程步骤顺序稳定。
     */
    private List<SearchReferenceCandidate> executeSearchQuestions(
        List<String> searchQuestions,
        Long runId,
        Long conversationId,
        long startSequenceNo
    ) {
        List<CompletableFuture<List<SearchReferenceCandidate>>> futures = new ArrayList<>();
        int maxParallelQuestions = Math.max(1, runtimeSettingService.searchMaxParallelQuestions());
        List<String> effectiveQuestions = searchQuestions;
        if (searchQuestions.size() > maxParallelQuestions) {
            effectiveQuestions = searchQuestions.subList(0, maxParallelQuestions);
        }
        log.info(
            "搜索决策/执行: 请求子问题数={}, 实际子问题数={}, 并发上限={}",
            searchQuestions.size(),
            effectiveQuestions.size(),
            maxParallelQuestions
        );
        long sequenceNo = startSequenceNo;
        for (String searchQuestion : effectiveQuestions) {
            ChatExecutionStep searchStep = ChatExecutionStep.builder()
                .id(cn.hutool.core.util.IdUtil.getSnowflakeNextId())
                .runId(runId)
                .stepType("search")
                .stepTitle(effectiveQuestions.size() > 1 ? "搜索子问题 " + sequenceNo : "搜索资料")
                .stepStatus("COMPLETED")
                .sequenceNo(sequenceNo++)
                .content(searchQuestion)
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();
            chatExecutionStepRepository.save(searchStep);
            chatStreamPublisher.publishStep(conversationId, Map.of(
                "id", searchStep.getId(),
                "runId", searchStep.getRunId(),
                "stepType", searchStep.getStepType(),
                "stepTitle", searchStep.getStepTitle(),
                "stepStatus", searchStep.getStepStatus(),
                "sequenceNo", searchStep.getSequenceNo(),
                "content", searchStep.getContent()
            ));
            futures.add(CompletableFuture.supplyAsync(
                () -> webSearchExecutionService.search(searchQuestion),
                searchExecutor == null ? ForkJoinPool.commonPool() : searchExecutor
            ));
        }
        LinkedHashMap<String, SearchReferenceCandidate> merged = new LinkedHashMap<>();
        for (CompletableFuture<List<SearchReferenceCandidate>> future : futures) {
            for (SearchReferenceCandidate candidate : future.join()) {
                String key = candidate.url() == null ? candidate.title() : candidate.url();
                merged.putIfAbsent(key, candidate);
            }
        }
        List<SearchReferenceCandidate> references = new ArrayList<>(merged.values());
        return references;
    }

    /**
     * 判断当前意图命中的 MCP 是否在本次会话显式启用列表内。
     * @param toolId 命中的工具标识。
     * @param selectedMcpCodes 当前消息携带的 MCP 编码。
     * @return 是否允许执行。
     */
    private boolean isMcpEnabledForCurrentMessage(String toolId, List<String> selectedMcpCodes) {
        if (StrUtil.isBlank(toolId)) {
            return false;
        }
        if (selectedMcpCodes == null || selectedMcpCodes.isEmpty()) {
            return false;
        }
        ChatMcp configuredMcp = chatMcpRepository.findByMcpCode(toolId);
        if (configuredMcp == null || configuredMcp.getEnabled() == null || configuredMcp.getEnabled() != 1) {
            return false;
        }
        return selectedMcpCodes.stream()
            .filter(StrUtil::isNotBlank)
            .map(String::trim)
            .anyMatch(code -> StrUtil.equalsIgnoreCase(code, toolId));
    }

    /**
     * 从配置表解析 MCP 展示名称，缺失时回退为工具编码。
     * @param toolId MCP 工具编码。
     * @return 展示名称。
     */
    private String resolveMcpDisplayName(String toolId) {
        ChatMcp configuredMcp = chatMcpRepository.findByMcpCode(toolId);
        if (configuredMcp == null || StrUtil.isBlank(configuredMcp.getDisplayName())) {
            return toolId;
        }
        return configuredMcp.getDisplayName();
    }

}
