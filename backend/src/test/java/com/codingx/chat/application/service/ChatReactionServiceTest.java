package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatMessageFeedback;
import com.codingx.chat.domain.repository.ChatMessageFeedbackRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证消息反馈服务的最小 upsert 行为。
 */
@ExtendWith(MockitoExtension.class)
class ChatReactionServiceTest {

    /**
     * 反馈仓储依赖。
     */
    @Mock
    private ChatMessageFeedbackRepository chatMessageFeedbackRepository;

    /**
     * 被测服务。
     */
    @InjectMocks
    private ChatReactionService chatReactionService;

    /**
     * 已存在反馈时应按同一消息同一用户更新，而不是重复插入。
     */
    @Test
    void submitReactionUpdatesExistingFeedback() {
        ChatMessageFeedback existing = ChatMessageFeedback.builder()
            .id(3001L)
            .messageId(101L)
            .conversationId(201L)
            .userId(1001L)
            .vote(1)
            .reason("good")
            .comment("old")
            .build();
        when(chatMessageFeedbackRepository.findByMessageIdAndUserId(101L, 1001L)).thenReturn(Optional.of(existing));

        chatReactionService.submitReaction(101L, 201L, 1001L, -1, "bad", "updated");

        ArgumentCaptor<ChatMessageFeedback> captor = ArgumentCaptor.forClass(ChatMessageFeedback.class);
        verify(chatMessageFeedbackRepository).save(captor.capture());
        assertEquals(3001L, captor.getValue().getId());
        assertEquals(-1, captor.getValue().getVote());
        assertEquals("updated", captor.getValue().getComment());
    }
}
