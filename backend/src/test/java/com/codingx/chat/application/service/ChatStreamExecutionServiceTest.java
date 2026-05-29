package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
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
import com.codingx.expert.domain.repository.ChatExpertRepository;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.task.domain.model.Task;
import com.codingx.task.domain.model.TaskStatus;
import com.codingx.task.domain.repository.TaskRepository;
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

    @Mock
    private ChatSkillRepository chatSkillRepository;

    @Mock
    private ChatMcpRepository chatMcpRepository;

    @Mock
    private ChatExpertRepository chatExpertRepository;

    @Mock
    private ChatWorkspaceBindingService chatWorkspaceBindingService;

    @Mock
    private ChatConversationRepository chatConversationRepository;

    @Mock
    private TaskRepository taskRepository;

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
            chatMcpRepository,
            chatSkillRepository,
            chatExpertRepository,
            chatConversationRepository,
            chatWorkspaceBindingService,
            taskRepository,
            executorService
        );
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
        verify(chatMcpRepository).bindTaskMcps(any(Long.class), eq(java.util.List.of()));
        verify(chatSkillRepository).bindTaskSkills(any(Long.class), eq(java.util.List.of()));
        verify(chatExpertRepository).bindTaskExpert(any(Long.class), eq(null));
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
        ChatStreamExecutionService service = new ChatStreamExecutionService(
            chatApplicationService,
            chatRuntimeGuardService,
            conversationTraceRecordService,
            chatExecutionRunRepository,
            chatMcpRepository,
            chatSkillRepository,
            chatExpertRepository,
            chatConversationRepository,
            chatWorkspaceBindingService,
            taskRepository,
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
            }).when(chatApplicationService).sendMessage(new SendChatMessageCommand(1001L, "你好", false, java.util.List.of(), java.util.List.of(), null, null, java.util.List.of()), 2001L);

            service.dispatch(new SendChatMessageCommand(1001L, "你好", false), 2001L);
        }

        assertTrue(captured.await(1, TimeUnit.SECONDS), "background task should receive forwarded login id");
        verify(chatMcpRepository).bindTaskMcps(any(Long.class), eq(java.util.List.of()));
        verify(chatSkillRepository).bindTaskSkills(any(Long.class), eq(java.util.List.of()));
        verify(chatExpertRepository).bindTaskExpert(any(Long.class), eq(null));
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
            chatMcpRepository,
            chatSkillRepository,
            chatExpertRepository,
            chatConversationRepository,
            chatWorkspaceBindingService,
            taskRepository,
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
        }).when(chatApplicationService).sendMessage(new SendChatMessageCommand(1001L, "你好", false, java.util.List.of(), java.util.List.of(), null, null, java.util.List.of()), 2001L);

        service.dispatch(new SendChatMessageCommand(1001L, "你好", false), 2001L);

        assertTrue(captured.await(1, TimeUnit.SECONDS), "background task should capture run id");
        assertEquals(savedRunId.get(), observedRunId.get(), "background task should reuse dispatch run id");
        verify(chatMcpRepository).bindTaskMcps(savedRunId.get(), java.util.List.of());
        verify(chatSkillRepository).bindTaskSkills(savedRunId.get(), java.util.List.of());
        verify(chatExpertRepository).bindTaskExpert(savedRunId.get(), null);
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
            chatMcpRepository,
            chatSkillRepository,
            chatExpertRepository,
            chatConversationRepository,
            chatWorkspaceBindingService,
            taskRepository,
            executorService
        );
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
        verify(chatMcpRepository).bindTaskMcps(any(Long.class), eq(java.util.List.of()));
        verify(chatSkillRepository).bindTaskSkills(any(Long.class), eq(java.util.List.of()));
        verify(chatExpertRepository).bindTaskExpert(any(Long.class), eq(null));
    }

    /**
     * 当后台执行直接抛错时，执行记录与 Trace 都应收口为 ERROR，避免永久停留 RUNNING。
     * @throws Exception 等待后台线程执行时抛出。
     */
    @Test
    void dispatchMarksRunFailedWhenApplicationServiceThrows() throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        AtomicReference<Long> runIdRef = new AtomicReference<>();
        ChatStreamExecutionService service = new ChatStreamExecutionService(
            chatApplicationService,
            chatRuntimeGuardService,
            conversationTraceRecordService,
            chatExecutionRunRepository,
            chatMcpRepository,
            chatSkillRepository,
            chatExpertRepository,
            chatConversationRepository,
            chatWorkspaceBindingService,
            taskRepository,
            executorService
        );
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
        verify(chatMcpRepository).bindTaskMcps(any(Long.class), eq(java.util.List.of()));
        verify(chatSkillRepository).bindTaskSkills(any(Long.class), eq(java.util.List.of()));
        verify(chatExpertRepository).bindTaskExpert(any(Long.class), eq(null));
    }

    /**
     * 当会话被门控拒绝时应记录 REJECTED，避免误记为系统异常。
     * @throws Exception 等待后台线程执行时抛出。
     */
    @Test
    void dispatchMarksRunRejectedWhenRuntimeGuardRejectsConversation() throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        AtomicReference<Long> runIdRef = new AtomicReference<>();
        ChatStreamExecutionService service = new ChatStreamExecutionService(
            chatApplicationService,
            chatRuntimeGuardService,
            conversationTraceRecordService,
            chatExecutionRunRepository,
            chatMcpRepository,
            chatSkillRepository,
            chatExpertRepository,
            chatConversationRepository,
            chatWorkspaceBindingService,
            taskRepository,
            executorService
        );
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
     * 派发入口应把消息级 MCP 与技能绑定同时写入，确保工作台可回放“当前能力上下文”。
     * @throws Exception 等待后台线程执行时抛出。
     */
    @Test
    void dispatchBindsBothMcpAndSkillSelections() throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        ChatStreamExecutionService service = new ChatStreamExecutionService(
            chatApplicationService,
            chatRuntimeGuardService,
            conversationTraceRecordService,
            chatExecutionRunRepository,
            chatMcpRepository,
            chatSkillRepository,
            chatExpertRepository,
            chatConversationRepository,
            chatWorkspaceBindingService,
            taskRepository,
            executorService
        );
        org.mockito.Mockito.doAnswer(invocation -> {
            captured.countDown();
            return null;
        }).when(chatApplicationService).sendMessage(new SendChatMessageCommand(
            1001L,
            "你好",
            false,
            java.util.List.of("weather_query"),
            java.util.List.of("agent-browser")
        ), 2001L);

        service.dispatch(new SendChatMessageCommand(
            1001L,
            "你好",
            false,
            java.util.List.of("weather_query"),
            java.util.List.of("agent-browser")
        ), 2001L);

        assertTrue(captured.await(1, TimeUnit.SECONDS), "background task should start with explicit selections");
        verify(chatMcpRepository).bindTaskMcps(any(Long.class), eq(java.util.List.of("weather_query")));
        verify(chatSkillRepository).bindTaskSkills(any(Long.class), eq(java.util.List.of("agent-browser")));
        verify(chatExpertRepository).bindTaskExpert(any(Long.class), eq(null));
    }

    /**
     * 派发入口应把消息级专家绑定同时写入，确保工作台可回放“当前专家”。
     * @throws Exception 等待后台线程执行时抛出。
     */
    @Test
    void dispatchBindsExpertSelection() throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        ChatStreamExecutionService service = new ChatStreamExecutionService(
            chatApplicationService,
            chatRuntimeGuardService,
            conversationTraceRecordService,
            chatExecutionRunRepository,
            chatMcpRepository,
            chatSkillRepository,
            chatExpertRepository,
            chatConversationRepository,
            chatWorkspaceBindingService,
            taskRepository,
            executorService
        );
        org.mockito.Mockito.doAnswer(invocation -> {
            captured.countDown();
            return null;
        }).when(chatApplicationService).sendMessage(new SendChatMessageCommand(
            1001L,
            "你好",
            false,
            java.util.List.of(),
            java.util.List.of(),
            "solution-architect",
            null,
            java.util.List.of()
        ), 2001L);

        service.dispatch(new SendChatMessageCommand(
            1001L,
            "你好",
            false,
            java.util.List.of(),
            java.util.List.of(),
            "solution-architect",
            null,
            java.util.List.of()
        ), 2001L);

        assertTrue(captured.await(1, TimeUnit.SECONDS), "background task should start with expert selection");
        verify(chatExpertRepository).bindTaskExpert(any(Long.class), eq("solution-architect"));
    }

    /**
     * 本地运行态默认不把任务、运行记录与能力绑定写入数据库，避免本地历史经任务表进入云端侧。
     * 关键约束：仍需注册取消句柄并异步执行 sendMessage，保证 SSE 生命周期不变。
     * @throws Exception 等待后台执行时出现异常。
     */
    @Test
    void dispatchLocalOnlySkipsPersistentTaskAndRunRecords() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        ChatStreamExecutionService service = new ChatStreamExecutionService(
            chatApplicationService,
            chatRuntimeGuardService,
            conversationTraceRecordService,
            chatExecutionRunRepository,
            chatMcpRepository,
            chatSkillRepository,
            chatExpertRepository,
            chatConversationRepository,
            chatWorkspaceBindingService,
            taskRepository,
            executorService
        );
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
        verify(taskRepository, never()).save(any(Task.class));
        verify(chatExecutionRunRepository, never()).save(any(ChatExecutionRun.class));
        verify(conversationTraceRecordService, never()).startTrace(any(), any(), any());
        verify(chatMcpRepository, never()).bindTaskMcps(any(Long.class), any());
        verify(chatSkillRepository, never()).bindTaskSkills(any(Long.class), any());
        verify(chatExpertRepository, never()).bindTaskExpert(any(Long.class), any());
        verify(chatRuntimeGuardService).registerCancellation(eq(9901L), eq(8801L), any(Runnable.class));
        verify(chatRuntimeGuardService, timeout(1000)).completeConversation(9901L, 8801L);
    }

    /**
     * 派发入口必须复用控制器下发的任务标识，确保 stream meta、task 与 run 可一一对应。
     * @throws Exception 等待后台线程执行时抛出。
     */
    @Test
    void dispatchPersistsDurableTaskWithProvidedTaskId() throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        Long taskId = 91001L;
        ChatStreamExecutionService service = new ChatStreamExecutionService(
            chatApplicationService,
            chatRuntimeGuardService,
            conversationTraceRecordService,
            chatExecutionRunRepository,
            chatMcpRepository,
            chatSkillRepository,
            chatExpertRepository,
            chatConversationRepository,
            chatWorkspaceBindingService,
            taskRepository,
            executorService
        );
        org.mockito.Mockito.doAnswer(invocation -> {
            captured.countDown();
            return null;
        }).when(chatApplicationService).sendMessage(new SendChatMessageCommand(1001L, "你好", false, java.util.List.of(), java.util.List.of(), null, null, java.util.List.of()), 2001L);
        when(chatExecutionRunRepository.findByConversationId(1001L)).thenReturn(java.util.List.of(
            ChatExecutionRun.builder()
                .id(taskId)
                .conversationId(1001L)
                .taskId(taskId)
                .status("COMPLETED")
                .finishedAt(java.time.LocalDateTime.now())
                .build()
        ));

        service.dispatch(
            taskId,
            new SendChatMessageCommand(1001L, "你好", false),
            2001L
        );

        assertTrue(captured.await(1, TimeUnit.SECONDS), "background task should finish");
        ArgumentCaptor<ChatExecutionRun> runCaptor = ArgumentCaptor.forClass(ChatExecutionRun.class);
        verify(chatExecutionRunRepository, timeout(1000).atLeastOnce()).save(runCaptor.capture());
        assertTrue(
            runCaptor.getAllValues().stream().anyMatch(run -> taskId.equals(run.getId()) && taskId.equals(run.getTaskId())),
            "run id and task id should reuse provided task id"
        );
        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository, timeout(1000).atLeast(2)).save(taskCaptor.capture());
        assertEquals(TaskStatus.RUNNING, taskCaptor.getAllValues().get(0).getStatus());
        assertTrue(
            taskCaptor.getAllValues().stream().anyMatch(task -> taskId.equals(task.getId()) && task.getStatus() == TaskStatus.SUCCEEDED),
            "task should be marked succeeded after normal completion"
        );
    }

    /**
     * 后台任务进入终态时应把会话任务完成提醒重置为未读，让前端列表刷新后能显示提醒圆点。
     * @throws Exception 等待后台线程执行时抛出。
     */
    @Test
    void dispatchMarksConversationTaskCompletionUnreadAfterTaskFinished() throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        Long taskId = 91002L;
        ChatConversation conversation = ChatConversation.create(1001L, "后台完成会话", 2001L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.findById(1001L)).thenReturn(java.util.Optional.of(conversation));
        ChatStreamExecutionService service = new ChatStreamExecutionService(
            chatApplicationService,
            chatRuntimeGuardService,
            conversationTraceRecordService,
            chatExecutionRunRepository,
            chatMcpRepository,
            chatSkillRepository,
            chatExpertRepository,
            chatConversationRepository,
            chatWorkspaceBindingService,
            taskRepository,
            executorService
        );
        org.mockito.Mockito.doAnswer(invocation -> {
            captured.countDown();
            return null;
        }).when(chatApplicationService).sendMessage(new SendChatMessageCommand(1001L, "你好", false, java.util.List.of(), java.util.List.of(), null, null, java.util.List.of()), 2001L);
        when(chatExecutionRunRepository.findByConversationId(1001L)).thenReturn(java.util.List.of(
            ChatExecutionRun.builder()
                .id(taskId)
                .conversationId(1001L)
                .taskId(taskId)
                .status("COMPLETED")
                .finishedAt(java.time.LocalDateTime.now())
                .build()
        ));

        service.dispatch(
            taskId,
            new SendChatMessageCommand(1001L, "你好", false),
            2001L
        );

        assertTrue(captured.await(1, TimeUnit.SECONDS), "background task should finish");
        ArgumentCaptor<ChatConversation> conversationCaptor = ArgumentCaptor.forClass(ChatConversation.class);
        verify(chatConversationRepository, timeout(1000)).save(conversationCaptor.capture());
        assertEquals(Boolean.FALSE, conversationCaptor.getValue().getTaskCompletionRead());
    }

    /**
     * 提醒状态写入失败不能反向污染任务终态；会话被删除时后台任务仍应按执行结果收口。
     * @throws Exception 等待后台线程执行时抛出。
     */
    @Test
    void dispatchKeepsTaskSucceededWhenTaskCompletionReminderWriteFails() throws Exception {
        CountDownLatch captured = new CountDownLatch(1);
        Long taskId = 91003L;
        ChatConversation conversation = ChatConversation.create(1001L, "后台完成会话", 2001L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.findById(1001L)).thenReturn(java.util.Optional.of(conversation));
        org.mockito.Mockito.doThrow(new NotFoundException(ErrorMessageCatalog.CHAT_CONVERSATION_NOT_FOUND))
            .when(chatConversationRepository)
            .save(any(ChatConversation.class));
        ChatStreamExecutionService service = new ChatStreamExecutionService(
            chatApplicationService,
            chatRuntimeGuardService,
            conversationTraceRecordService,
            chatExecutionRunRepository,
            chatMcpRepository,
            chatSkillRepository,
            chatExpertRepository,
            chatConversationRepository,
            chatWorkspaceBindingService,
            taskRepository,
            executorService
        );
        org.mockito.Mockito.doAnswer(invocation -> {
            captured.countDown();
            return null;
        }).when(chatApplicationService).sendMessage(new SendChatMessageCommand(1001L, "你好", false, java.util.List.of(), java.util.List.of(), null, null, java.util.List.of()), 2001L);
        when(chatExecutionRunRepository.findByConversationId(1001L)).thenReturn(java.util.List.of(
            ChatExecutionRun.builder()
                .id(taskId)
                .conversationId(1001L)
                .taskId(taskId)
                .status("COMPLETED")
                .finishedAt(java.time.LocalDateTime.now())
                .build()
        ));

        service.dispatch(
            taskId,
            new SendChatMessageCommand(1001L, "你好", false),
            2001L
        );

        assertTrue(captured.await(1, TimeUnit.SECONDS), "background task should finish");
        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository, timeout(1000).atLeast(2)).save(taskCaptor.capture());
        assertTrue(
            taskCaptor.getAllValues().stream().anyMatch(task -> taskId.equals(task.getId()) && task.getStatus() == TaskStatus.SUCCEEDED),
            "task should stay succeeded even when reminder write fails"
        );
        verify(chatExecutionRunRepository, org.mockito.Mockito.never()).save(
            org.mockito.ArgumentMatchers.argThat(run -> "ERROR".equals(run.getStatus()))
        );
    }
}
