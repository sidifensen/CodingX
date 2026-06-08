package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.application.service.chat.ChatStreamExecutionService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatExecutionRun;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.ConflictException;
import com.codingx.common.exception.NotFoundException;
import com.codingx.skill.application.service.SkillLocalCacheService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.io.TempDir;

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
     * 技能本地缓存依赖，用于验证云端技能包下载目录的清理边界。
     */
    @Mock
    private SkillLocalCacheService skillLocalCacheService;

    /**
     * 工作空间绑定依赖，用于后台线程恢复工具执行目录。
     */
    @Mock
    private ChatWorkspaceBindingService chatWorkspaceBindingService;

    /**
     * 会话仓储依赖，用于验证完成提醒未读状态的写入兜底。
     */
    @Mock
    private ChatConversationRepository chatConversationRepository;

    /**
     * 释放测试线程池，避免用例之间残留后台线程。
     * @throws Exception 等待后台任务收口时被中断。
     */
    @AfterEach
    void shutdownExecutor() throws Exception {
        // 步骤 1：先给后台任务正常执行 finally 的机会，避免 JUnit 删除 @TempDir 时和 skill 临时目录清理竞争。
        executorService.shutdown();
        if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
            // 步骤 2：超时才强制中断，保证异常用例不会把后台线程遗留到下一个测试。
            executorService.shutdownNow();
            assertTrue(executorService.awaitTermination(2, TimeUnit.SECONDS), "background executor should stop after each test");
        }
    }

    /**
     * 创建默认派发服务；构造参数刻意不包含旧任务仓储，约束测试不再回到 task/task_expert 链路。
     */
    private ChatStreamExecutionService createService() {
        return new ChatStreamExecutionService(
            chatApplicationService,
            chatRuntimeGuardService,
            conversationTraceRecordService,
            chatExecutionRunRepository,
            chatConversationRepository,
            chatWorkspaceBindingService,
            executorService
        );
    }

    /**
     * 创建带流发布器和技能缓存的派发服务，用于覆盖云端技能临时目录清理场景。
     */
    private ChatStreamExecutionService createServiceWithSkillCache() {
        return new ChatStreamExecutionService(
            chatApplicationService,
            chatRuntimeGuardService,
            conversationTraceRecordService,
            chatExecutionRunRepository,
            chatConversationRepository,
            chatWorkspaceBindingService,
            new com.codingx.chat.infrastructure.stream.NoopChatStreamPublisher(),
            skillLocalCacheService,
            executorService
        );
    }

    /**
     * dispatch 应快速返回，并在后台线程中执行 sendMessage。
     * @throws Exception 等待后台执行时出现异常。
     */
    @Test
    void dispatchReturnsImmediatelyAndRunsSendMessageInBackground() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ChatStreamExecutionService service = createService();
        org.mockito.Mockito.doAnswer(invocation -> {
            started.countDown();
            release.await(3, TimeUnit.SECONDS);
            return null;
        }).when(chatApplicationService).sendMessage(new SendChatMessageCommand(1001L, "你好", false, java.util.List.of(), java.util.List.of(), null, null, java.util.List.of()), 2001L);

        long startAt = System.nanoTime();
        service.dispatch(new SendChatMessageCommand(1001L, "你好", false), 2001L);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startAt);

        assertTrue(elapsedMs < 100, "dispatch should return immediately");
        assertTrue(started.await(1, TimeUnit.SECONDS), "background task should start");
        verify(conversationTraceRecordService).startTrace("chat-entry", 1001L, 2001L);
        verify(chatRuntimeGuardService).registerCancellation(eq(1001L), any(Long.class), any(Runnable.class));

        release.countDown();
        verify(chatApplicationService, org.mockito.Mockito.timeout(1000)).sendMessage(new SendChatMessageCommand(1001L, "你好", false, java.util.List.of(), java.util.List.of(), null, null, java.util.List.of()), 2001L);
        verify(chatRuntimeGuardService, org.mockito.Mockito.timeout(1000))
            .completeConversation(eq(1001L), any(Long.class));
    }

    /**
     * 派发服务需要把当前登录上下文透传到后台线程。
     * @throws Exception 等待后台线程执行时抛出。
     */
    @Test
    void dispatchPropagatesLoginContextIntoBackgroundThread() throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        ChatStreamExecutionService service = createService();
        try (org.mockito.MockedStatic<cn.dev33.satoken.stp.StpUtil> mocked = org.mockito.Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            mocked.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(2001L);
            org.mockito.Mockito.doAnswer(invocation -> {
                Long forwardedUserId = invocation.getArgument(1);
                if (forwardedUserId.equals(2001L)) {
                    captured.countDown();
                }
                return null;
            }).when(chatApplicationService).sendMessage(new SendChatMessageCommand(1001L, "你好", false, java.util.List.of(), java.util.List.of(), null, null, java.util.List.of()), 2001L);

            service.dispatch(new SendChatMessageCommand(1001L, "你好", false), 2001L);
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
        ChatStreamExecutionService service = createService();
        org.mockito.Mockito.doAnswer(invocation -> {
            ChatExecutionRun run = invocation.getArgument(0);
            savedRunId.set(run.getId());
            return null;
        }).when(chatExecutionRunRepository).save(any(ChatExecutionRun.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            observedRunId.set(ChatExecutionContext.currentRunId().orElse(null));
            captured.countDown();
            return null;
        }).when(chatApplicationService).sendMessage(new SendChatMessageCommand(1001L, "你好", false, java.util.List.of(), java.util.List.of(), null, null, java.util.List.of()), 2001L);

        service.dispatch(new SendChatMessageCommand(1001L, "你好", false), 2001L);

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
        ChatStreamExecutionService service = createService();
        org.mockito.Mockito.when(conversationTraceRecordService.startTrace("chat-entry", 1001L, 2001L))
            .thenReturn(ChatTraceRun.builder().traceId("trace-1").traceName("chat-entry").build());
        AtomicReference<String> observedTraceId = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            observedTraceId.set(ConversationTraceContext.current() != null ? ConversationTraceContext.current().getTraceId() : null);
            captured.countDown();
            return null;
        }).when(chatApplicationService).sendMessage(new SendChatMessageCommand(1001L, "你好", false, java.util.List.of(), java.util.List.of(), null, null, java.util.List.of()), 2001L);

        service.dispatch(new SendChatMessageCommand(1001L, "你好", false), 2001L);

        assertTrue(captured.await(1, TimeUnit.SECONDS), "background task should capture trace context");
        assertEquals("trace-1", observedTraceId.get());
    }

    /**
     * 当后台执行直接抛错时，执行记录与 Trace 都应收口为 ERROR，避免永久停留 RUNNING。
     * @throws Exception 等待后台线程执行时抛出。
     */
    @Test
    void dispatchMarksRunFailedWhenApplicationServiceThrows() throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        AtomicReference<Long> runIdRef = new AtomicReference<>();
        ChatStreamExecutionService service = createService();
        ChatTraceRun traceRun = ChatTraceRun.builder().traceId("trace-error").traceName("chat-entry").build();
        when(conversationTraceRecordService.startTrace("chat-entry", 1001L, 2001L)).thenReturn(traceRun);
        org.mockito.Mockito.doAnswer(invocation -> {
            ChatExecutionRun run = invocation.getArgument(0);
            if ("RUNNING".equals(run.getStatus())) {
                runIdRef.set(run.getId());
            }
            if ("ERROR".equals(run.getStatus())) {
                captured.countDown();
            }
            return null;
        }).when(chatExecutionRunRepository).save(any(ChatExecutionRun.class));
        org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
            .when(chatApplicationService).sendMessage(new SendChatMessageCommand(1001L, "你好", false, java.util.List.of(), java.util.List.of(), null, null, java.util.List.of()), 2001L);

        service.dispatch(new SendChatMessageCommand(1001L, "你好", false), 2001L);

        assertTrue(captured.await(1, TimeUnit.SECONDS), "failed run should be updated to ERROR");
        verify(chatExecutionRunRepository, org.mockito.Mockito.atLeast(2)).save(any(ChatExecutionRun.class));
        verify(conversationTraceRecordService).finishTrace("trace-error", runIdRef.get(), "ERROR", "boom");
    }

    /**
     * 当会话被门控拒绝时应记录 REJECTED，避免误记为系统异常。
     * @throws Exception 等待后台线程执行时抛出。
     */
    @Test
    void dispatchMarksRunRejectedWhenRuntimeGuardRejectsConversation() throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        AtomicReference<Long> runIdRef = new AtomicReference<>();
        ChatStreamExecutionService service = createService();
        ChatTraceRun traceRun = ChatTraceRun.builder().traceId("trace-rejected").traceName("chat-entry").build();
        when(conversationTraceRecordService.startTrace("chat-entry", 1001L, 2001L)).thenReturn(traceRun);
        org.mockito.Mockito.doAnswer(invocation -> {
            ChatExecutionRun run = invocation.getArgument(0);
            if ("RUNNING".equals(run.getStatus())) {
                runIdRef.set(run.getId());
            }
            if ("REJECTED".equals(run.getStatus())) {
                captured.countDown();
            }
            return null;
        }).when(chatExecutionRunRepository).save(any(ChatExecutionRun.class));
        org.mockito.Mockito.doThrow(new ConflictException(ErrorMessageCatalog.CHAT_QUEUE_BUSY))
            .when(chatApplicationService).sendMessage(new SendChatMessageCommand(1001L, "你好", false, java.util.List.of(), java.util.List.of(), null, null, java.util.List.of()), 2001L);

        service.dispatch(new SendChatMessageCommand(1001L, "你好", false), 2001L);

        assertTrue(captured.await(1, TimeUnit.SECONDS), "rejected run should be updated to REJECTED");
        verify(chatExecutionRunRepository, timeout(1000).atLeast(2)).save(any(ChatExecutionRun.class));
        verify(conversationTraceRecordService).finishTrace("trace-rejected", runIdRef.get(), "REJECTED", ErrorMessageCatalog.CHAT_QUEUE_BUSY);
    }

    /**
     * 派发入口只透传显式能力选择，MCP/技能上下文由应用服务写入 chat_execution_step。
     * @param tempParent 测试隔离的临时父目录，用于模拟云端技能下载结果。
     * @throws Exception 准备技能目录或等待后台线程失败。
     */
    @Test
    void dispatchPassesExplicitCapabilitySelectionToApplicationService(@TempDir Path tempParent) throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        Path packageRoot = tempParent.resolve("codingx-skills-capability");
        Path skillDir = packageRoot.resolve("agent-browser");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "browser skill");
        when(skillLocalCacheService.downloadSkillToTemp("agent-browser")).thenReturn(skillDir);
        ChatStreamExecutionService service = createServiceWithSkillCache();
        SendChatMessageCommand command = new SendChatMessageCommand(
            1001L,
            "你好",
            false,
            java.util.List.of("weather_query"),
            java.util.List.of("agent-browser")
        );
        org.mockito.Mockito.doAnswer(invocation -> {
            captured.countDown();
            return null;
        }).when(chatApplicationService).sendMessage(command, 2001L);

        service.dispatch(command, 2001L);

        assertTrue(captured.await(1, TimeUnit.SECONDS), "background task should start with explicit selections");
        verify(chatApplicationService, timeout(1000)).sendMessage(command, 2001L);
    }

    /**
     * 云端 skill 的临时清理只能删除本次下载根目录，不能误删系统 Temp 父目录里的其他进程文件。
     * @param tempParent 测试隔离的临时父目录，用于模拟 java.io.tmpdir。
     * @throws Exception 准备临时目录或等待后台线程失败。
     */
    @Test
    void dispatchCleansOnlyCloudSkillTempPackageRoot(@TempDir Path tempParent) throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        Long runId = 92001L;
        Path packageRoot = tempParent.resolve("codingx-skills-test");
        Path skillDir = packageRoot.resolve("web-access");
        Path siblingOwnedByOtherProcess = tempParent.resolve("owned-by-other.tmp");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "web access skill");
        Files.writeString(siblingOwnedByOtherProcess, "keep");
        when(skillLocalCacheService.downloadSkillToTemp("web-access")).thenReturn(skillDir);
        ChatStreamExecutionService service = createServiceWithSkillCache();
        SendChatMessageCommand command = new SendChatMessageCommand(
            1001L,
            "使用联网技能",
            false,
            java.util.List.of(),
            java.util.List.of("web-access")
        );
        org.mockito.Mockito.doAnswer(invocation -> {
            captured.countDown();
            return null;
        }).when(chatApplicationService).sendMessage(command, 2001L);

        service.dispatch(runId, command, 2001L);

        assertTrue(captured.await(1, TimeUnit.SECONDS), "background task should use downloaded cloud skill");
        verify(chatRuntimeGuardService, timeout(1000)).completeConversation(1001L, runId);
        assertTrue(Files.exists(siblingOwnedByOtherProcess), "cleanup must not delete files beside codingx skill package root");
        assertTrue(Files.notExists(packageRoot), "cleanup should delete the downloaded codingx skill package root");
    }

    /**
     * 派发入口只透传消息级专家选择，当前专家上下文由应用服务写入 chat_execution_step。
     * @throws Exception 等待后台线程执行时抛出。
     */
    @Test
    void dispatchPassesExpertSelectionToApplicationService() throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        ChatStreamExecutionService service = createService();
        SendChatMessageCommand command = new SendChatMessageCommand(
            1001L,
            "你好",
            false,
            java.util.List.of(),
            java.util.List.of(),
            "solution-architect",
            null,
            java.util.List.of()
        );
        org.mockito.Mockito.doAnswer(invocation -> {
            captured.countDown();
            return null;
        }).when(chatApplicationService).sendMessage(command, 2001L);

        service.dispatch(command, 2001L);

        assertTrue(captured.await(1, TimeUnit.SECONDS), "background task should start with expert selection");
        verify(chatApplicationService, timeout(1000)).sendMessage(command, 2001L);
    }

    /**
     * 本地运行态默认不把运行记录写入数据库，避免本地历史进入云端持久化链路。
     * 关键约束：仍需注册取消句柄并异步执行 sendMessage，保证 SSE 生命周期不变。
     * @throws Exception 等待后台执行时出现异常。
     */
    @Test
    void dispatchLocalOnlySkipsPersistentRunRecords() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        ChatStreamExecutionService service = createService();
        SendChatMessageCommand command = SendChatMessageCommand.localOnly(
            9901L,
            "分析本地仓库",
            false,
            java.util.List.of(),
            java.util.List.of(),
            java.util.Map.of(),
            null,
            "D:/code/test",
            java.util.List.of(),
            false
        );
        org.mockito.Mockito.doAnswer(invocation -> {
            started.countDown();
            return null;
        }).when(chatApplicationService).sendMessage(command, 2001L);

        service.dispatch(8801L, command, 2001L);

        assertTrue(started.await(1, TimeUnit.SECONDS), "local-only background task should start");
        verify(chatExecutionRunRepository, never()).save(any(ChatExecutionRun.class));
        verify(conversationTraceRecordService, never()).startTrace(any(), any(), any());
        verify(chatRuntimeGuardService).registerCancellation(eq(9901L), eq(8801L), any(Runnable.class));
        verify(chatRuntimeGuardService, timeout(1000)).completeConversation(9901L, 8801L);
    }

    /**
     * 派发入口必须复用控制器下发的运行标识，并以 runId 写入兼容列 taskId。
     * @throws Exception 等待后台线程执行时抛出。
     */
    @Test
    void dispatchPersistsDurableRunWithProvidedRunId() throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        Long runId = 91001L;
        ChatStreamExecutionService service = createService();
        org.mockito.Mockito.doAnswer(invocation -> {
            captured.countDown();
            return null;
        }).when(chatApplicationService).sendMessage(new SendChatMessageCommand(1001L, "你好", false, java.util.List.of(), java.util.List.of(), null, null, java.util.List.of()), 2001L);

        service.dispatch(
            runId,
            new SendChatMessageCommand(1001L, "你好", false),
            2001L
        );

        assertTrue(captured.await(1, TimeUnit.SECONDS), "background task should finish");
        ArgumentCaptor<ChatExecutionRun> runCaptor = ArgumentCaptor.forClass(ChatExecutionRun.class);
        verify(chatExecutionRunRepository).save(runCaptor.capture());
        ChatExecutionRun savedRun = runCaptor.getValue();
        assertEquals(runId, savedRun.getId());
        assertEquals(runId, savedRun.getTaskId());
        assertEquals(1001L, savedRun.getConversationId());
        assertEquals("RUNNING", savedRun.getStatus());
        assertEquals("WAITING", savedRun.getQueueStatus());
        assertTrue(
            savedRun.getStartedAt() != null && savedRun.getCreatedAt() != null && savedRun.getUpdatedAt() != null,
            "initial run should persist lifecycle timestamps"
        );
    }

    /**
     * 后台 run 进入终态时应把会话任务完成提醒重置为未读，让前端列表刷新后能显示提醒圆点。
     * @throws Exception 等待后台线程执行时抛出。
     */
    @Test
    void dispatchMarksConversationTaskCompletionUnreadAfterRunFinished() throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        Long runId = 91002L;
        ChatConversation conversation = ChatConversation.create(1001L, "后台完成会话", 2001L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.findById(1001L)).thenReturn(java.util.Optional.of(conversation));
        ChatStreamExecutionService service = createService();
        org.mockito.Mockito.doAnswer(invocation -> {
            captured.countDown();
            return null;
        }).when(chatApplicationService).sendMessage(new SendChatMessageCommand(1001L, "你好", false, java.util.List.of(), java.util.List.of(), null, null, java.util.List.of()), 2001L);

        service.dispatch(
            runId,
            new SendChatMessageCommand(1001L, "你好", false),
            2001L
        );

        assertTrue(captured.await(1, TimeUnit.SECONDS), "background task should finish");
        ArgumentCaptor<ChatConversation> conversationCaptor = ArgumentCaptor.forClass(ChatConversation.class);
        verify(chatConversationRepository, timeout(1000)).save(conversationCaptor.capture());
        assertEquals(Boolean.FALSE, conversationCaptor.getValue().getTaskCompletionRead());
    }

    /**
     * 提醒状态写入失败不能反向污染 run 终态；会话被删除时后台 run 仍应按执行结果收口。
     * @throws Exception 等待后台线程执行时抛出。
     */
    @Test
    void dispatchDoesNotMarkRunFailedWhenTaskCompletionReminderWriteFails() throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        Long runId = 91003L;
        ChatConversation conversation = ChatConversation.create(1001L, "后台完成会话", 2001L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.findById(1001L)).thenReturn(java.util.Optional.of(conversation));
        org.mockito.Mockito.doThrow(new NotFoundException(ErrorMessageCatalog.CHAT_CONVERSATION_NOT_FOUND))
            .when(chatConversationRepository)
            .save(any(ChatConversation.class));
        ChatStreamExecutionService service = createService();
        org.mockito.Mockito.doAnswer(invocation -> {
            captured.countDown();
            return null;
        }).when(chatApplicationService).sendMessage(new SendChatMessageCommand(1001L, "你好", false, java.util.List.of(), java.util.List.of(), null, null, java.util.List.of()), 2001L);

        service.dispatch(
            runId,
            new SendChatMessageCommand(1001L, "你好", false),
            2001L
        );

        assertTrue(captured.await(1, TimeUnit.SECONDS), "background task should finish");
        verify(chatExecutionRunRepository, org.mockito.Mockito.never()).save(
            org.mockito.ArgumentMatchers.argThat(run -> "ERROR".equals(run.getStatus()))
        );
    }
}
