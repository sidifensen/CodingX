package com.codingx.chat.application.service;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatExecutionStep;
import com.codingx.chat.domain.model.ChatExecutionRun;
import com.codingx.chat.domain.model.ChatAttachment;
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
import com.codingx.common.exception.ForbiddenException;
import com.codingx.expert.application.service.ChatExpertContextService;
import com.codingx.mcp.application.service.ChatMcpExecutionService;
import com.codingx.mcp.application.service.ChatMcpToolResult;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.skill.application.service.ChatSkillContextService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
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
            throw new IllegalArgumentException("Message content is required");
        }
        Long runId = currentRunId(command.conversationId());
        ChatConversation conversation = chatConversationRepository.requireById(command.conversationId());
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException("You cannot access this conversation");
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
        String rewrittenQuestion = rewriteResult.rewrite();
        boolean mcpEnabled = command.mcpCodes() != null && !command.mcpCodes().isEmpty();
        ConversationIntentDecision intentDecision = conversationIntentService.route(rewrittenQuestion, mcpEnabled);
        if (intentDecision.action() == ConversationIntentAction.CLARIFY) {
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
        if (intentDecision.action() == ConversationIntentAction.DIRECT && StrUtil.isNotBlank(intentDecision.reply())) {
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
        if (intentDecision.action() == ConversationIntentAction.MCP_DISABLED) {
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
        if (intentDecision.action() == ConversationIntentAction.MCP) {
            com.codingx.chat.domain.model.ChatIntentNode intentNode = chatIntentNodeRepository.findByIntentCode(intentDecision.intentCode());
            if (intentNode == null || StrUtil.isBlank(intentNode.getMcpToolId())) {
                throw new IllegalStateException("MCP tool config is missing for intent: " + intentDecision.intentCode());
            }
            if (!isMcpEnabledForCurrentMessage(intentNode.getMcpToolId(), command.mcpCodes())) {
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
            ChatMcpToolResult toolResult = chatMcpExecutionService.execute(intentNode.getMcpToolId(), rewrittenQuestion);
            ChatExecutionStep mcpStep = ChatExecutionStep.builder()
                .id(cn.hutool.core.util.IdUtil.getSnowflakeNextId())
                .runId(runId)
                .stepType("mcp")
                .stepTitle("执行 MCP 工具")
                .stepStatus("COMPLETED")
                .sequenceNo(1L)
                .content(toolResult.content())
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
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
            chatStreamPublisher.publishMcpCall(command.conversationId(), Map.of(
                "toolId", toolResult.toolId(),
                "displayName", resolveMcpDisplayName(toolResult.toolId()),
                "input", rewrittenQuestion,
                "content", toolResult.content(),
                "metadata", toolResult.metadata() == null ? Map.of() : toolResult.metadata()
            ));
            ChatMessage assistantMessage = ChatMessage.assistantMessage(
                command.conversationId(),
                toolResult.content(),
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
        if (intentDecision.action() == ConversationIntentAction.SEARCH) {
            List<SearchReferenceCandidate> references = executeSearchQuestions(
                rewriteResult.shouldSplit() ? rewriteResult.subQuestions() : List.of(rewrittenQuestion),
                runId,
                command.conversationId()
            );
            searchReferenceCollector.collect(runId, userMessage.getId(), command.conversationId(), references);
            documentArtifactService.createDocxArtifact(runId, userMessage.getId(), command.conversationId(), "搜索结果整理中");
        }
        StringBuilder builder = new StringBuilder();
        StringBuilder thinkingBuilder = new StringBuilder();
        final Throwable[] streamError = new Throwable[1];
        final String[] selectedProvider = new String[1];
        final String[] selectedModel = new String[1];
        List<ChatMessage> aiHistory = buildAiHistory(
            conversationSummaryService.buildModelHistory(command.conversationId(), history),
            intentDecision,
            command.conversationId(),
            command.skillCodes(),
            command.expertCode()
        );
        tokenCounterService.estimateConversationTokens(aiHistory);
        final Long activeRunId = runId;
        try {
            aiChatClient.streamChat(aiHistory, command.deepThinking(), new AiChatClient.StreamHandler() {
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
                    thinkingBuilder.append(delta);
                    chatStreamPublisher.publishAssistantThinkingDelta(command.conversationId(), delta);
                }
                @Override
                public void onComplete() {
                }
                @Override
                public void onError(Throwable throwable) {
                    streamError[0] = throwable;
                }
            });
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
                StrUtil.blankToDefault(llmResponseCleaner.clean(builder.toString()), "AI response failed"),
                ChatMessageStatus.FAILED,
                selectedProvider[0],
                selectedModel[0],
                streamError[0].getMessage()

            ).attachRun(runId);
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
        assistantMessage.restoreRuntimeState(
            assistantMessage.getRunId(),
            llmResponseCleaner.clean(thinkingBuilder.toString()),
            StrUtil.isBlank(thinkingBuilder.toString()) ? null : 0,
            null,
            assistantMessage.getCreatedAt(),
            assistantMessage.getUpdatedAt()
        );
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

    private Long currentRunId(Long conversationId) {
        return ChatExecutionContext.currentRunId().orElse(conversationId);
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
        String selectedExpertCode
    ) {
        String systemPrompt = resolveSystemPromptFromIntent(intentDecision);
        String expertContext = chatExpertContextService.buildExpertContext(selectedExpertCode);
        String skillContext = chatSkillContextService.buildSkillContext(selectedSkillCodes);
        if (StrUtil.isBlank(systemPrompt) && StrUtil.isBlank(expertContext) && StrUtil.isBlank(skillContext)) {
            return history;
        }
        List<String> promptSegments = new ArrayList<>();
        if (StrUtil.isNotBlank(systemPrompt)) {
            promptSegments.add(systemPrompt);
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
     * 按子问题并行执行搜索，并为每个子问题推送独立步骤事件。
     * @param searchQuestions 搜索问题集合。
     * @param runId 运行标识。
     * @param conversationId 会话标识。
     * @return 合并去重后的来源候选。
     */
    private List<SearchReferenceCandidate> executeSearchQuestions(List<String> searchQuestions, Long runId, Long conversationId) {
        List<CompletableFuture<List<SearchReferenceCandidate>>> futures = new ArrayList<>();
        int maxParallelQuestions = Math.max(1, runtimeSettingService.searchMaxParallelQuestions());
        List<String> effectiveQuestions = searchQuestions;
        if (searchQuestions.size() > maxParallelQuestions) {
            effectiveQuestions = searchQuestions.subList(0, maxParallelQuestions);
        }
        int sequenceNo = 1;
        for (String searchQuestion : effectiveQuestions) {
            ChatExecutionStep searchStep = ChatExecutionStep.builder()
                .id(cn.hutool.core.util.IdUtil.getSnowflakeNextId())
                .runId(runId)
                .stepType("search")
                .stepTitle(effectiveQuestions.size() > 1 ? "搜索子问题 " + sequenceNo : "搜索资料")
                .stepStatus("COMPLETED")
                .sequenceNo((long) sequenceNo++)
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


