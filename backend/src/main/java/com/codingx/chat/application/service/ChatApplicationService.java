package com.codingx.chat.application.service;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatExecutionStep;
import com.codingx.chat.domain.model.ChatExecutionRun;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.chat.domain.repository.ChatExecutionStepRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.domain.service.AiChatClient;
import com.codingx.chat.domain.service.ChatStreamPublisher;
import com.codingx.common.exception.ForbiddenException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        ChatMessage userMessage = ChatMessage.userMessage(command.conversationId(), command.content()).attachRun(runId);
        chatMessageRepository.save(userMessage);
        chatStreamPublisher.publishUserMessage(command.conversationId(), userMessage.getContent());
        history.add(userMessage);
        String rewrittenQuestion = conversationRewriteService.rewrite(
            history.stream().filter(message -> message.getRole() == com.codingx.chat.domain.model.ChatMessageRole.USER)
                .map(ChatMessage::getContent)
                .toList(),
            command.content()
        );
        ConversationIntentDecision intentDecision = conversationIntentService.route(rewrittenQuestion);
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
            chatStreamPublisher.publishAssistantCompleted(command.conversationId(), assistantMessage.getContent(), conversation.getTitle());
            return;
        }
        if (intentDecision.action() == ConversationIntentAction.SEARCH) {
            List<SearchReferenceCandidate> references = webSearchExecutionService.search(rewrittenQuestion);
            ChatExecutionStep searchStep = ChatExecutionStep.builder()
                .id(cn.hutool.core.util.IdUtil.getSnowflakeNextId())
                .runId(runId)
                .stepType("search")
                .stepTitle("搜索资料")
                .stepStatus("COMPLETED")
                .sequenceNo(1L)
                .content(rewrittenQuestion)
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();
            chatExecutionStepRepository.save(searchStep);
            chatStreamPublisher.publishStep(command.conversationId(), Map.of(
                "id", searchStep.getId(),
                "runId", searchStep.getRunId(),
                "stepType", searchStep.getStepType(),
                "stepTitle", searchStep.getStepTitle(),
                "stepStatus", searchStep.getStepStatus(),
                "sequenceNo", searchStep.getSequenceNo(),
                "content", searchStep.getContent()
            ));
            searchReferenceCollector.collect(runId, userMessage.getId(), command.conversationId(), references);
            documentArtifactService.createDocxArtifact(runId, userMessage.getId(), command.conversationId(), "搜索结果整理中");
        }
        StringBuilder builder = new StringBuilder();
        final Throwable[] streamError = new Throwable[1];
        aiChatClient.streamChat(history, new AiChatClient.StreamHandler() {
            @Override
            public void onDelta(String delta) {
                if (chatRuntimeGuardService.isCancelled(command.conversationId())) {
                    return;
                }
                builder.append(delta);
                chatStreamPublisher.publishAssistantDelta(command.conversationId(), delta);
            }
            @Override
            public void onComplete() {
            }
            @Override
            public void onError(Throwable throwable) {
                streamError[0] = throwable;
            }
        });
        if (chatRuntimeGuardService.isCancelled(command.conversationId())) {
            ChatMessage cancelledMessage = ChatMessage.assistantMessage(
                command.conversationId(),
                StrUtil.blankToDefault(builder.toString(), ""),
                ChatMessageStatus.CANCELLED,
                null,
                null,
                null
            ).attachRun(runId);
            chatMessageRepository.save(cancelledMessage);
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            recordExecutionOutcome(conversation, userMessage.getId(), cancelledMessage.getId(), intentDecision.intentCode(), intentDecision.action() == ConversationIntentAction.SEARCH, true, ChatMessageStatus.CANCELLED, null);
            return;
        }
        if (streamError[0] != null) {

            ChatMessage failedMessage = ChatMessage.assistantMessage(
                command.conversationId(),
                StrUtil.blankToDefault(builder.toString(), "AI response failed"),
                ChatMessageStatus.FAILED,
                null,
                null,
                streamError[0].getMessage()

            ).attachRun(runId);
            chatMessageRepository.save(failedMessage);
            conversation.touch();
            conversation.recordLastRunId(runId);
            chatConversationRepository.save(conversation);
            chatStreamPublisher.publishError(command.conversationId(), streamError[0].getMessage());
            recordExecutionOutcome(conversation, userMessage.getId(), failedMessage.getId(), intentDecision.intentCode(), intentDecision.action() == ConversationIntentAction.SEARCH, false, ChatMessageStatus.FAILED, streamError[0].getMessage());
            return;
        }
        ChatMessage assistantMessage = ChatMessage.assistantMessage(
            command.conversationId(),
            StrUtil.blankToDefault(builder.toString(), ""),
            ChatMessageStatus.COMPLETED,
            null,
            null,
            null

        ).attachRun(runId);
        chatMessageRepository.save(assistantMessage);
        history.add(assistantMessage);
        conversation.rename(conversationTitleService.generateTitle(conversation, history));
        conversationSummaryService.refreshSummaryIfNeeded(conversation, history);
        conversation.touch();
        conversation.recordLastRunId(runId);
        chatConversationRepository.save(conversation);
        recordExecutionOutcome(conversation, userMessage.getId(), assistantMessage.getId(), intentDecision.intentCode(), intentDecision.action() == ConversationIntentAction.SEARCH, true, ChatMessageStatus.COMPLETED, null);
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
}
