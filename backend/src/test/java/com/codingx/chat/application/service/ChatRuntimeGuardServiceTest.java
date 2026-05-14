package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.service.ChatStreamPublisher;
import com.codingx.chat.infrastructure.runtime.ChatRunControlService;
import com.codingx.chat.infrastructure.runtime.ConversationQueueGate;
import com.codingx.chat.infrastructure.runtime.QueueAcquireResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证聊天运行保护服务对拒绝和取消场景的收口行为。
 */
@ExtendWith(MockitoExtension.class)
class ChatRuntimeGuardServiceTest {

    /**
     * 队列门控依赖。
     */
    @Mock
    private ConversationQueueGate conversationQueueGate;

    /**
     * 取消控制依赖。
     */
    @Mock
    private ChatRunControlService chatRunControlService;

    /**
     * 流式发布依赖。
     */
    @Mock
    private ChatStreamPublisher chatStreamPublisher;

    /**
     * 被测服务。
     */
    @InjectMocks
    private ChatRuntimeGuardService chatRuntimeGuardService;

    /**
     * 无法获取执行资格时应推送 reject 事件并中止后续流程。
     */
    @Test
    void ensureAcceptedPublishesRejectWhenQueueGateDenies() {
        when(conversationQueueGate.tryAcquire(1001L)).thenReturn(QueueAcquireResult.rejected("busy"));

        assertThrows(IllegalStateException.class, () -> chatRuntimeGuardService.ensureAccepted(1001L));

        verify(chatStreamPublisher).publishRejected(1001L, "busy");
    }

    /**
     * 取消已注册会话时应发出 cancel 事件。
     */
    @Test
    void cancelConversationPublishesCancelEvent() {
        when(chatRunControlService.cancel(1001L)).thenReturn(true);

        chatRuntimeGuardService.cancelConversation(1001L);

        verify(chatStreamPublisher).publishCancelled(1001L);
    }
}
