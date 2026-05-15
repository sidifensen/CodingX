package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.domain.model.ChatExecutionRun;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
     * 执行记录仓储依赖。
     */
    @Mock
    private ChatExecutionRunRepository chatExecutionRunRepository;

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
            chatExecutionRunRepository,
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

    /**
     * 派发服务需要把当前登录上下文透传到后台线程。
     * @throws Exception 等待后台线程执行时抛出。
     */
    @Test
    void dispatchPropagatesLoginContextIntoBackgroundThread() throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        ChatStreamExecutionService service = new ChatStreamExecutionService(
            chatApplicationService,
            chatRuntimeGuardService,
            conversationTraceRecordService,
            chatExecutionRunRepository,
            executorService
        );
        try (org.mockito.MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = org.mockito.Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(2001L);
            org.mockito.Mockito.doAnswer(invocation -> {
                Long forwardedUserId = invocation.getArgument(1);
                if (forwardedUserId.equals(2001L)) {
                    captured.countDown();
                }
                return null;
            }).when(chatApplicationService).sendMessage(new SendChatMessageCommand(1001L, "你好"), 2001L);

            service.dispatch(new SendChatMessageCommand(1001L, "你好"), 2001L);
        }

        assertTrue(captured.await(1, TimeUnit.SECONDS), "background task should receive forwarded login id");
    }

    /**
     * runId 必须透传到后台线程，否则后续步骤、来源与运行记录会挂到错误链路上。
     * @throws Exception 等待后台线程执行时抛出。
     */
    @Test
    void dispatchPropagatesRunIdIntoBackgroundThread() throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        AtomicReference<Long> savedRunId = new AtomicReference<>();
        AtomicReference<Long> observedRunId = new AtomicReference<>();
        ChatStreamExecutionService service = new ChatStreamExecutionService(
            chatApplicationService,
            chatRuntimeGuardService,
            conversationTraceRecordService,
            chatExecutionRunRepository,
            executorService
        );
        org.mockito.Mockito.doAnswer(invocation -> {
            ChatExecutionRun run = invocation.getArgument(0);
            savedRunId.set(run.getId());
            return null;
        }).when(chatExecutionRunRepository).save(any(ChatExecutionRun.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            observedRunId.set(ChatExecutionContext.currentRunId().orElse(null));
            captured.countDown();
            return null;
        }).when(chatApplicationService).sendMessage(new SendChatMessageCommand(1001L, "你好"), 2001L);

        service.dispatch(new SendChatMessageCommand(1001L, "你好"), 2001L);

        assertTrue(captured.await(1, TimeUnit.SECONDS), "background task should capture run id");
        assertEquals(savedRunId.get(), observedRunId.get(), "background task should reuse dispatch run id");
    }

    /**
     * trace 上下文也必须透传到后台线程，否则后续收口拿不到 traceId。
     * @throws Exception 等待后台线程执行时抛出。
     */
    @Test
    void dispatchPropagatesTraceContextIntoBackgroundThread() throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        ChatStreamExecutionService service = new ChatStreamExecutionService(
            chatApplicationService,
            chatRuntimeGuardService,
            conversationTraceRecordService,
            chatExecutionRunRepository,
            executorService
        );
        org.mockito.Mockito.when(conversationTraceRecordService.startTrace("chat-entry", 1001L, 2001L))
            .thenReturn(ChatTraceRun.builder().traceId("trace-1").traceName("chat-entry").build());
        AtomicReference<String> observedTraceId = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            observedTraceId.set(ConversationTraceContext.current() != null ? ConversationTraceContext.current().getTraceId() : null);
            captured.countDown();
            return null;
        }).when(chatApplicationService).sendMessage(new SendChatMessageCommand(1001L, "你好"), 2001L);

        service.dispatch(new SendChatMessageCommand(1001L, "你好"), 2001L);

        assertTrue(captured.await(1, TimeUnit.SECONDS), "background task should capture trace context");
        assertEquals("trace-1", observedTraceId.get());
    }
}
