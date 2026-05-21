package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;

import com.codingx.chat.domain.port.ChatStreamPublisher;
import com.codingx.chat.infrastructure.runtime.ChatRunControlService;
import com.codingx.chat.infrastructure.runtime.ConversationQueueGate;
import com.codingx.chat.infrastructure.runtime.QueueAcquireResult;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.ConflictException;
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
        when(conversationQueueGate.tryAcquire(eq(1001L), any())).thenReturn(QueueAcquireResult.rejected(ErrorMessageCatalog.CHAT_QUEUE_BUSY));

        ConflictException exception = assertThrows(ConflictException.class, () -> chatRuntimeGuardService.ensureAccepted(1001L));

        assertEquals(ErrorMessageCatalog.CHAT_QUEUE_BUSY, exception.getMessage());
        verify(chatStreamPublisher).publishRejected(1001L, ErrorMessageCatalog.CHAT_QUEUE_BUSY);
    }

    /**
     * 获取执行资格成功后应推送 queue-accepted，通知前端关闭排队提示。
     */
    @Test
    void ensureAcceptedPublishesQueueAcceptedWhenGranted() {
        when(conversationQueueGate.tryAcquire(eq(1001L), any())).thenReturn(QueueAcquireResult.granted());

        chatRuntimeGuardService.ensureAccepted(1001L);

        verify(chatStreamPublisher).publishQueueAccepted(1001L);
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

    /**
     * run 级完成时应调用 run 级清理，避免旧 run 抢先清理新 run 句柄。
     */
    @Test
    void completeConversationWithRunIdUsesRunScopedCleanup() {
        chatRuntimeGuardService.completeConversation(1001L, 9001L);

        verify(conversationQueueGate).release(1001L);
        verify(chatRunControlService).complete(1001L, 9001L);
    }

    /**
     * run 级取消判断应透传到运行控制服务。
     */
    @Test
    void isCancelledWithRunIdDelegatesToRunControlService() {
        when(chatRunControlService.isCancelled(1001L, 9001L)).thenReturn(true);

        org.junit.jupiter.api.Assertions.assertTrue(chatRuntimeGuardService.isCancelled(1001L, 9001L));
        verify(chatRunControlService).isCancelled(1001L, 9001L);
    }
}

