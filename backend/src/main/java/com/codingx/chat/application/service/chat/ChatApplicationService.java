package com.codingx.chat.application.service;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
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
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.common.support.ai.AiToolCall;
import com.codingx.expert.domain.model.ChatExpert;
import com.codingx.expert.domain.repository.ChatExpertRepository;
import com.codingx.expert.application.service.ChatExpertContextService;
import com.codingx.mcp.application.service.ChatMcpExecutionService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
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
public class ChatApplicationService {

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
    /** MCP 配置仓储，读取工具启用状态与展示信息 */
    private final ChatMcpRepository chatMcpRepository;
    /** 技能绑定仓储，供重新生成时复用原始 run 的技能选择 */
    private final ChatSkillRepository chatSkillRepository;
    /** 专家绑定仓储，供重新生成时复用原始 run 的专家选择 */
    private final ChatExpertRepository chatExpertRepository;
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
    /** 会话 workspace 绑定服务，负责把本地空间映射为真实仓库目录 */
    private final ChatWorkspaceBindingService chatWorkspaceBindingService;

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
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        chatRuntimeGuardService.ensureAccepted(conversationId);
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
        List<ChatMessage> history = new ArrayList<>(chatMessageRepository.findByConversationId(conversationId));
        ChatMessage lastUserMessage = findLastMessageByRole(history, ChatMessageRole.USER)
            .orElseThrow(() -> new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN));
        ChatMessage lastAssistantMessage = findLastMessageByRole(history, ChatMessageRole.ASSISTANT)
            .orElseThrow(() -> new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN));
        Long sourceRunId = lastAssistantMessage.getRunId() != null ? lastAssistantMessage.getRunId() : resolveRegenerateSourceRunId(conversation);
        List<String> selectedSkillCodes = loadSelectedSkillCodes(sourceRunId);
        List<String> selectedMcpCodes = loadSelectedMcpCodes(sourceRunId);
        String selectedExpertCode = loadSelectedExpertCode(sourceRunId);
        SendChatMessageCommand command = new SendChatMessageCommand(
            conversationId,
            lastUserMessage.getContent(),
            false,
            selectedMcpCodes,
            selectedSkillCodes,
            selectedExpertCode,
            null,
            List.of()
        );
        bindSelectedContextToRun(runId, selectedMcpCodes, selectedSkillCodes, selectedExpertCode);
        try {
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
            history.stream().filter(message -> message.getRole() == ChatMessageRole.USER)
                .map(ChatMessage::getContent)
                .toList(),
            command.content()
        );
        List<SearchReferenceCandidate> searchReferences = List.of();
        String rewrittenQuestion = rewriteResult.rewrite();
        boolean mcpEnabled = command.mcpCodes() != null && !command.mcpCodes().isEmpty();
        List<SubQuestionIntentDecision> subQuestionDecisions = resolveSubQuestionDecisions(rewriteResult, mcpEnabled);
        ConversationIntentDecision intentDecision = primaryIntentDecision(subQuestionDecisions);
        Optional<SubQuestionIntentDecision> clarifyDecision = firstDecisionWithAction(
            subQuestionDecisions,
            ConversationIntentAction.CLARIFY
        );
        if (clarifyDecision.isPresent()) {
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
            conversation.rename(conversationTitleService.generateTitle(conversation, history));
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, requestMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), false, false, ChatMessageStatus.COMPLETED, null);
            finishTrace(runId, "SUCCESS", null);
            chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getContent(), conversation.getTitle());
            return;
        }
        if (isSingleDirectReply(subQuestionDecisions)) {
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
            conversation.rename(conversationTitleService.generateTitle(conversation, history));
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, requestMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), false, false, ChatMessageStatus.COMPLETED, null);
            finishTrace(runId, "SUCCESS", null);
            chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getContent(), conversation.getTitle());
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
            conversation.rename(conversationTitleService.generateTitle(conversation, history));
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, requestMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), false, false, ChatMessageStatus.COMPLETED, null);
            finishTrace(runId, "SUCCESS", null);
            chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getContent(), conversation.getTitle());
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
            conversation.rename(conversationTitleService.generateTitle(conversation, history));
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, requestMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), false, false, ChatMessageStatus.COMPLETED, null);
            finishTrace(runId, "SUCCESS", null);
            chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getContent(), conversation.getTitle());
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
            conversationSummaryService.buildModelHistory(command.conversationId(), history),
            intentDecision,
            command.conversationId(),
            command.skillCodes(),
            command.expertCode(),
            searchReferences
        );
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
                List.of()
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
        conversation.rename(conversationTitleService.generateTitle(conversation, history));
        conversationSummaryService.refreshSummaryIfNeeded(conversation, history);
        conversation.touch();
        conversation.recordLastRunId(runId);
        chatConversationRepository.save(conversation);
        recordExecutionOutcome(conversation, requestMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), intentDecision.action() == ConversationIntentAction.SEARCH, true, ChatMessageStatus.COMPLETED, null);
        finishTrace(runId, "SUCCESS", null);
        chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getContent(), conversation.getTitle());
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
            .map(run -> run.getTaskId() == null ? run.getId() : run.getTaskId())
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
        return chatSkillRepository.findByTaskId(sourceRunId).stream()
            .map(ChatSkill::getSkillCode)
            .filter(StrUtil::isNotBlank)
            .distinct()
            .toList();
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
        return chatMcpRepository.findByTaskId(sourceRunId).stream()
            .map(ChatMcp::getMcpCode)
            .filter(StrUtil::isNotBlank)
            .distinct()
            .toList();
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
        return chatExpertRepository.findByTaskId(sourceRunId).stream()
            .map(ChatExpert::getExpertCode)
            .filter(StrUtil::isNotBlank)
            .findFirst()
            .orElse(null);
    }

    /**
     * 将重新生成使用的上下文显式绑定到新 run，确保右侧回放能读取到同一批能力配置。
     * @param runId 新的执行标识。
     * @param selectedMcpCodes 绑定的 MCP 编码。
     * @param selectedSkillCodes 绑定的技能编码。
     * @param selectedExpertCode 绑定的专家编码。
     */
    private void bindSelectedContextToRun(Long runId, List<String> selectedMcpCodes, List<String> selectedSkillCodes, String selectedExpertCode) {
        chatMcpRepository.bindTaskMcps(runId, selectedMcpCodes);
        chatSkillRepository.bindTaskSkills(runId, selectedSkillCodes);
        chatExpertRepository.bindTaskExpert(runId, selectedExpertCode);
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
        if (StrUtil.isBlank(command.content())) {
            throw new IllegalArgumentException(ErrorMessageCatalog.CHAT_MESSAGE_CONTENT_REQUIRED);
        }
        if (command.localOnly()) {
            sendLocalOnlyMessage(command, userId);
            return;
        }
        Long runId = currentRunId(command.conversationId());
        ChatConversation conversation = chatConversationRepository.requireById(command.conversationId());
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        chatRuntimeGuardService.ensureAccepted(command.conversationId());
        List<ChatMessage> history = new ArrayList<>(chatMessageRepository.findByConversationId(command.conversationId()));
        List<ChatAttachment> validatedAttachments = chatAttachmentService.requireOwnedAttachments(
            command.attachmentIds(),
            command.conversationId(),
            userId
        );
        ChatMessage userMessage = ChatMessage.userMessage(command.conversationId(), command.content()).attachRun(runId);
        chatMessageRepository.save(userMessage);
        for (ChatAttachment attachment : validatedAttachments) {
            chatAttachmentService.bindToMessage(attachment, command.conversationId(), userMessage.getId(), runId);
        }
        chatStreamPublisher.publishUserMessage(command.conversationId(), userMessage.getContent());
        history.add(userMessage);
        ConversationRewriteResult rewriteResult = conversationRewriteService.rewriteResult(
            history.stream().filter(message -> message.getRole() == com.codingx.chat.domain.model.ChatMessageRole.USER)
                .map(ChatMessage::getContent)
                .toList(),
            command.content()
        );
        List<SearchReferenceCandidate> searchReferences = List.of();
        String rewrittenQuestion = rewriteResult.rewrite();
        boolean mcpEnabled = command.mcpCodes() != null && !command.mcpCodes().isEmpty();
        List<SubQuestionIntentDecision> subQuestionDecisions = resolveSubQuestionDecisions(rewriteResult, mcpEnabled);
        ConversationIntentDecision intentDecision = primaryIntentDecision(subQuestionDecisions);
        Optional<SubQuestionIntentDecision> clarifyDecision = firstDecisionWithAction(
            subQuestionDecisions,
            ConversationIntentAction.CLARIFY
        );
        if (clarifyDecision.isPresent()) {
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
            conversation.rename(conversationTitleService.generateTitle(conversation, history));
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, userMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), false, false, ChatMessageStatus.COMPLETED, null);
            finishTrace(runId, "SUCCESS", null);
            chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getContent(), conversation.getTitle());
            return;
        }
        if (isSingleDirectReply(subQuestionDecisions)) {
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
            conversation.rename(conversationTitleService.generateTitle(conversation, history));
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, userMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), false, false, ChatMessageStatus.COMPLETED, null);
            finishTrace(runId, "SUCCESS", null);
            chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getContent(), conversation.getTitle());
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
            conversation.rename(conversationTitleService.generateTitle(conversation, history));
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, userMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), false, false, ChatMessageStatus.COMPLETED, null);
            finishTrace(runId, "SUCCESS", null);
            chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getContent(), conversation.getTitle());
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
            conversation.rename(conversationTitleService.generateTitle(conversation, history));
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, userMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), false, false, ChatMessageStatus.COMPLETED, null);
            finishTrace(runId, "SUCCESS", null);
            chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getContent(), conversation.getTitle());
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
            searchReferenceCollector.collect(runId, userMessage.getId(), command.conversationId(), searchReferences);
            documentArtifactService.createDocxArtifact(runId, userMessage.getId(), command.conversationId(), "搜索结果整理中");
        }
        StringBuilder builder = new StringBuilder();
        StringBuilder thinkingBuilder = new StringBuilder();
        AtomicReference<LocalDateTime> thinkingStartedAt = new AtomicReference<>();
        final Throwable[] streamError = new Throwable[1];
        final String[] selectedProvider = new String[1];
        final String[] selectedModel = new String[1];
        List<ChatMessage> aiHistory = buildAiHistory(
            conversationSummaryService.buildModelHistory(command.conversationId(), history),
            intentDecision,
            command.conversationId(),
            command.skillCodes(),
            command.expertCode(),
            searchReferences
        );
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
                validatedAttachments
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
                recordExecutionOutcome(conversation, userMessage.getId(), cancelledMessage.getId(), intentDecision.intentCode(), intentDecision.action() == ConversationIntentAction.SEARCH, true, ChatMessageStatus.CANCELLED, null);
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
            recordExecutionOutcome(conversation, userMessage.getId(), cancelledMessage.getId(), intentDecision.intentCode(), intentDecision.action() == ConversationIntentAction.SEARCH, true, ChatMessageStatus.CANCELLED, null);
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
            recordExecutionOutcome(conversation, userMessage.getId(), failedMessage.getId(), intentDecision.intentCode(), intentDecision.action() == ConversationIntentAction.SEARCH, false, ChatMessageStatus.FAILED, streamError[0].getMessage());
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
        conversation.rename(conversationTitleService.generateTitle(conversation, history));
        conversationSummaryService.refreshSummaryIfNeeded(conversation, history);
        conversation.touch();
        conversation.recordLastRunId(runId);
        chatConversationRepository.save(conversation);
        recordExecutionOutcome(conversation, userMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), intentDecision.action() == ConversationIntentAction.SEARCH, true, ChatMessageStatus.COMPLETED, null);
        finishTrace(runId, "SUCCESS", null);
        chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getContent(), conversation.getTitle());
    }

    /**
     * 执行本地临时聊天链路，只向 SSE 推送运行结果，不读写云端会话、消息、run 或任务相关表。
     * 关键约束：本地历史的 source of truth 是客户端本地快照，后端仅用临时 conversationId 做流式路由。
     *
     * @param command 本地运行命令。
     * @param userId 当前用户标识，预留给后续本地权限约束，当前不落库。
     */
    private void sendLocalOnlyMessage(SendChatMessageCommand command, Long userId) {
        Long runId = currentRunId(command.conversationId());
        chatRuntimeGuardService.ensureAccepted(command.conversationId());
        List<ChatMessage> history = new ArrayList<>();
        ChatMessage userMessage = ChatMessage.userMessage(command.conversationId(), command.content()).attachRun(runId);
        history.add(userMessage);
        chatStreamPublisher.publishUserMessage(command.conversationId(), userMessage.getContent());
        ConversationRewriteResult rewriteResult = conversationRewriteService.rewriteResult(
            List.of(command.content()),
            command.content()
        );
        String rewrittenQuestion = rewriteResult.rewrite();
        boolean mcpEnabled = command.mcpCodes() != null && !command.mcpCodes().isEmpty();
        ConversationIntentDecision intentDecision = conversationIntentService.route(rewrittenQuestion, mcpEnabled);
        if (intentDecision.action() == ConversationIntentAction.CLARIFY) {
            chatStreamPublisher.publishAssistantCompleted(
                command.conversationId(),
                intentDecision.reply(),
                resolveLocalConversationTitle(command)
            );
            return;
        }
        if (intentDecision.action() == ConversationIntentAction.DIRECT && StrUtil.isNotBlank(intentDecision.reply())) {
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
            command.skillCodes(),
            command.expertCode(),
            List.of()
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
                List.of()
            );
        } catch (RuntimeException exception) {
            if (chatRuntimeGuardService.isCancelled(command.conversationId(), runId)) {
                return;
            }
            throw exception;
        }
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
        List<ChatAttachment> currentMessageAttachments
    ) {
        List<ChatToolSpec> toolSpecs = resolveModelVisibleToolSpecs(initialAiHistory, currentMessageAttachments);
        List<ChatMessage> currentHistory = new ArrayList<>(initialAiHistory);
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
                null
            ));
            return;
        }
        // 轮次上限由系统配置控制，避免模型在工具-回灌链路里无限循环。
        int maxToolRounds = Math.max(1, runtimeSettingService.chatToolMaxRounds());
        for (int round = 0; round < maxToolRounds; round++) {
            List<AiToolCall> toolCalls = new ArrayList<>();
            aiChatClient.streamChatWithTools(currentHistory, command.deepThinking(), toolSpecs, buildStreamHandler(
                command,
                builder,
                thinkingBuilder,
                thinkingStartedAt,
                streamError,
                selectedProvider,
                selectedModel,
                activeRunId,
                toolCalls
            ));
        if (streamError[0] != null || toolCalls.isEmpty()) {
                return;
            }
            // 工具重入后模型会继续追加同一条助手消息；已流出的正文和真实 thinking 不能清空。
            // 失败收口、finish 事件和消息落库都依赖这两个缓冲保留工具调用前已经到达的内容。
            for (AiToolCall toolCall : toolCalls) {
                ChatToolExecutionResult toolResult;
                try {
                    toolResult = executeModelToolCall(command, runId, toolCall);
                } catch (RuntimeException exception) {
                    streamError[0] = exception;
                    return;
                }
                currentHistory.add(ChatMessage.create(
                    cn.hutool.core.util.IdUtil.getSnowflakeNextId(),
                    command.conversationId(),
                    ChatMessageRole.SYSTEM,
                    buildLocalToolEvidenceContext(toolResult),
                    ChatMessageStatus.COMPLETED,
                    null,
                    null,
                    null
                ).attachRun(runId));
            }
        }
        // 连续工具调用仍未结束时，用明确异常提示用户收敛工具调用策略。
        streamError[0] = new IllegalStateException("本地工具调用轮次超过上限，请收敛工具调用后重试");
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
        List<AiToolCall> toolCalls
    ) {
        return new AiChatClient.ToolAwareStreamHandler() {
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
     * 在当前本地 workspace 中执行模型请求的工具。
     * @param command 当前消息命令。
     * @param runId 运行标识。
     * @param toolCall 模型工具调用。
     * @return 工具执行结果。
     */
    private ChatToolExecutionResult executeModelToolCall(SendChatMessageCommand command, Long runId, AiToolCall toolCall) {
        Path workspacePath = resolveToolWorkingDirectory(command);
        if (workspacePath != null) {
            ChatToolExecutionContext.bindToolWorkingDirectory(workspacePath);
        }
        LocalDateTime startedAt = LocalDateTime.now();
        publishLocalToolCallEvent(command.conversationId(), toolCall, "start", startedAt, null, null);
        try {
            ChatToolExecutionResult toolResult = chatToolExecutionService.execute(toolCall.toolCode(), toolCall.arguments());
            ChatExecutionStep toolStep = ChatExecutionStep.builder()
                .id(cn.hutool.core.util.IdUtil.getSnowflakeNextId())
                .runId(runId)
                .stepType("tool")
                .stepTitle("执行本地工具 " + toolCall.toolCode())
                .stepStatus("COMPLETED")
                .sequenceNo(1L)
                .content(toolResult.content())
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();
            if (!command.localOnly()) {
                chatExecutionStepRepository.save(toolStep);
                chatStreamPublisher.publishStep(command.conversationId(), Map.of(
                    "id", toolStep.getId(),
                    "runId", toolStep.getRunId(),
                    "stepType", toolStep.getStepType(),
                    "stepTitle", toolStep.getStepTitle(),
                    "stepStatus", toolStep.getStepStatus(),
                    "sequenceNo", toolStep.getSequenceNo(),
                    "content", toolStep.getContent()
                ));
            }
            publishLocalToolCallEvent(command.conversationId(), toolCall, "complete", startedAt, LocalDateTime.now(), toolResult);
            return toolResult;
        } catch (RuntimeException exception) {
            publishLocalToolCallError(command.conversationId(), toolCall, startedAt, exception);
            throw exception;
        } finally {
            ChatToolExecutionContext.clear();
        }
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
        payload.put("displayName", StrUtil.blankToDefault(toolCall.toolCode(), "本地工具"));
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
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("# 本地工具执行结果\n");
        contextBuilder.append("工具标识：").append(StrUtil.blankToDefault(toolResult.toolCode(), "unknown")).append('\n');
        contextBuilder.append("工具输出：").append(normalizeEvidenceText(toolResult.content(), 4000)).append('\n');
        if (toolResult.metadata() != null && !toolResult.metadata().isEmpty()) {
            contextBuilder.append("工具元数据：")
                .append(normalizeEvidenceText(cn.hutool.json.JSONUtil.toJsonStr(toolResult.metadata()), 2000));
        }
        return contextBuilder.toString().trim();
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
        List<SearchReferenceCandidate> searchReferences
    ) {
        String systemPrompt = resolveSystemPromptFromIntent(intentDecision);
        String expertContext = chatExpertContextService.buildExpertContext(selectedExpertCode);
        String skillContext = chatSkillContextService.buildSkillContext(selectedSkillCodes);
        String searchEvidenceContext = buildSearchEvidenceContext(intentDecision, searchReferences);
        if (
            StrUtil.isBlank(systemPrompt)
            && StrUtil.isBlank(expertContext)
            && StrUtil.isBlank(skillContext)
            && StrUtil.isBlank(searchEvidenceContext)
        ) {
            return history;
        }
        List<String> promptSegments = new ArrayList<>();
        if (StrUtil.isNotBlank(systemPrompt)) {
            promptSegments.add(systemPrompt);
        }
        if (StrUtil.isNotBlank(searchEvidenceContext)) {
            promptSegments.add(searchEvidenceContext);
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
        aiHistory.addAll(history);
        return aiHistory;
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
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("# 联网检索证据\n");
        contextBuilder.append("当前日期：").append(DateUtil.today()).append('\n');
        contextBuilder.append("回答约束：\n");
        contextBuilder.append("1. 你只能依据下方检索证据回答，不得引用训练记忆中的旧时间、旧版本或旧结论\n");
        contextBuilder.append("2. 当联网证据与模型记忆冲突时，必须以联网证据为准\n");
        contextBuilder.append("3. 优先采用来源可靠且信息更新的条目；若证据冲突，说明冲突并给出更可信来源\n");
        contextBuilder.append("4. 若证据不足以得出结论，必须明确回答“当前检索证据不足，无法确认”\n");
        contextBuilder.append("5. 最终回答每个关键结论都必须带引用编号，如 [R1]、[R2]\n");
        contextBuilder.append("检索结果：\n");
        int rank = 1;
        for (SearchReferenceCandidate reference : references) {
            contextBuilder.append(rank++).append(". ");
            contextBuilder.append("标题：").append(normalizeEvidenceText(reference.title(), 120));
            if (StrUtil.isNotBlank(reference.siteName())) {
                contextBuilder.append("；站点：").append(normalizeEvidenceText(reference.siteName(), 80));
            }
            if (StrUtil.isNotBlank(reference.url())) {
                contextBuilder.append("；链接：").append(normalizeEvidenceText(reference.url(), 300));
            }
            if (StrUtil.isNotBlank(reference.snippet())) {
                contextBuilder.append("；摘要：").append(normalizeEvidenceText(reference.snippet(), 220));
            }
            contextBuilder.append('\n');
        }
        return contextBuilder.toString().trim();
    }

    /**
     * 将工具原始结果转换为模型后续回答可消费的系统证据，保证“工具完成后再整理回答”。
     * @param toolResult 工具执行结果。
     * @return 可注入模型上下文的证据文本。
     */
    private String buildToolEvidenceContext(ChatMcpToolResult toolResult) {
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("# 工具执行证据\n");
        contextBuilder.append("工具标识：").append(StrUtil.blankToDefault(toolResult.toolId(), "unknown")).append('\n');
        contextBuilder.append("回答约束：\n");
        contextBuilder.append("1. 你必须基于本次工具结果整理最终回答，而不是直接复述工具原文\n");
        contextBuilder.append("2. 如果工具结果不足以回答问题，要明确说明不足\n");
        contextBuilder.append("3. 如果工具结果包含结构化数据，应先提炼结论再回答\n");
        contextBuilder.append("工具结果：\n");
        contextBuilder.append(normalizeEvidenceText(toolResult.content(), 4000)).append('\n');
        if (toolResult.metadata() != null && !toolResult.metadata().isEmpty()) {
            contextBuilder.append("工具元数据：\n");
            contextBuilder.append(normalizeEvidenceText(cn.hutool.json.JSONUtil.toJsonStr(toolResult.metadata()), 2000)).append('\n');
        }
        return contextBuilder.toString().trim();
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
        return new ArrayList<>(merged.values());
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


