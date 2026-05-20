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
import com.codingx.chat.domain.service.AiChatClient;
import com.codingx.chat.domain.service.ChatStreamPublisher;
import com.codingx.common.exception.ForbiddenException;
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
import org.springframework.stereotype.Service;

/**
 * 负责协调 ChatApplicationService 的应用流程，串联领域对象与基础设施服务。
 */
@Service
@RequiredArgsConstructor
public class ChatApplicationService {

    /**
     * ChatConversationRepository 依赖。
     */
    private final ChatConversationRepository chatConversationRepository;

    /**
     * ChatMessageRepository 依赖。
     */
    private final ChatMessageRepository chatMessageRepository;

    /**
     * ChatExecutionStepRepository 依赖。
     */
    private final ChatExecutionStepRepository chatExecutionStepRepository;

    /**
     * ChatExecutionRunRepository 依赖。
     */
    private final ChatExecutionRunRepository chatExecutionRunRepository;

    /**
     * AiChatClient 依赖。
     */
    private final AiChatClient aiChatClient;

    /**
     * ChatStreamPublisher 依赖。
     */
    private final ChatStreamPublisher chatStreamPublisher;

    /**
     * ChatRuntimeGuardService 依赖。
     */
    private final ChatRuntimeGuardService chatRuntimeGuardService;

    /**
     * ConversationTitleService 依赖。
     */
    private final ConversationTitleService conversationTitleService;

    /**
     * ConversationSummaryService 依赖。
     */
    private final ConversationSummaryService conversationSummaryService;

    /**
     * ConversationRewriteService 依赖。
     */
    private final ConversationRewriteService conversationRewriteService;

    /**
     * ConversationIntentService 依赖。
     */
    private final ConversationIntentService conversationIntentService;

    /**
     * Prompt 资产加载器依赖。
     */
    private final PromptTemplateLoader promptTemplateLoader;

    /**
     * MCP 执行服务依赖。
     */
    private final ChatMcpExecutionService chatMcpExecutionService;
    private final ChatMcpRepository chatMcpRepository;
    private final ChatAttachmentService chatAttachmentService;

    /**
     * 意图节点仓储依赖。
     */
    private final com.codingx.chat.domain.repository.ChatIntentNodeRepository chatIntentNodeRepository;

    /**
     * WebSearchExecutionService 依赖。
     */
    private final WebSearchExecutionService webSearchExecutionService;

    /**
     * SearchReferenceCollector 依赖。
     */
    private final SearchReferenceCollector searchReferenceCollector;

    /**
     * DocumentArtifactService 依赖。
     */
    private final DocumentArtifactService documentArtifactService;

    /**
     * Trace 收口服务依赖。
     */
    private final ConversationTraceRecordService conversationTraceRecordService;
    private final com.codingx.common.support.ai.TokenCounterService tokenCounterService;
    private final com.codingx.common.support.ai.LlmResponseCleaner llmResponseCleaner;
    private final ExecutorService searchExecutor;
    private final RuntimeSettingService runtimeSettingService;
    private final ChatSkillContextService chatSkillContextService;

    /**
     * 发送 sendMessage 处理的消息或请求。
     * @param command 输入参数。
     * @param userId 输入参数。
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
            command.skillCodes()
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
        List<String> selectedSkillCodes
    ) {
        String systemPrompt = resolveSystemPromptFromIntent(intentDecision);
        String skillContext = chatSkillContextService.buildSkillContext(selectedSkillCodes);
        if (StrUtil.isBlank(systemPrompt) && StrUtil.isBlank(skillContext)) {
            return history;
        }
        String composedSystemPrompt = StrUtil.isBlank(skillContext)
            ? systemPrompt
            : (StrUtil.isBlank(systemPrompt)
                ? skillContext
                : systemPrompt + "\n\n" + skillContext);
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

