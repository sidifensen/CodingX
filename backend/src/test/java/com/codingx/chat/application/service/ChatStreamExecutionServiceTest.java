package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import com.codingx.chat.application.command.SendChatMessageCommand;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证聊天流派发服务会异步触发实际消息处理。
 */
@ExtendWith(MockitoExtension.class)
class ChatStreamExecutionServiceTest {

    /**
     * 聊天应用服务依赖。
     */
    @Mock
    private ChatApplicationService chatApplicationService;

    /**
     * 测试专用执行器。
     */
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    /**
     * 运行保护服务依赖。
     */
    @Mock
    private ChatRuntimeGuardService chatRuntimeGuardService;

    /**
     * Trace 记录服务依赖。
     */
    @Mock
    private ConversationTraceRecordService conversationTraceRecordService;

    /**
     * 释放测试线程池，避免用例之间残留后台线程。
     */
    @AfterEach
    void shutdownExecutor() {
        executorService.shutdownNow();
    }

    /**
     * dispatch 应快速返回，并在后台线程中执行 sendMessage。
     * @throws Exception 等待后台执行时出现异常。
     */
    @Test
    void dispatchReturnsImmediatelyAndRunsSendMessageInBackground() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ChatStreamExecutionService service = new ChatStreamExecutionService(
            chatApplicationService,
            chatRuntimeGuardService,
            conversationTraceRecordService,
            executorService
        );
        org.mockito.Mockito.doAnswer(invocation -> {
            started.countDown();
            release.await(3, TimeUnit.SECONDS);
            return null;
        }).when(chatApplicationService).sendMessage(new SendChatMessageCommand(1001L, "你好"), 2001L);

        long startAt = System.nanoTime();
        service.dispatch(new SendChatMessageCommand(1001L, "你好"), 2001L);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startAt);

        assertTrue(elapsedMs < 100, "dispatch should return immediately");
        assertTrue(started.await(1, TimeUnit.SECONDS), "background task should start");
        verify(conversationTraceRecordService).startTrace("chat-entry", 1001L, 2001L);

        release.countDown();
        verify(chatApplicationService, org.mockito.Mockito.timeout(1000)).sendMessage(new SendChatMessageCommand(1001L, "你好"), 2001L);
    }
}
