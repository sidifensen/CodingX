package com.codingx.chat.application.service;

import com.codingx.chat.application.command.SendChatMessageCommand;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

/**
 * 负责将聊天消息处理异步派发到后台线程，避免 SSE 入口阻塞整个 HTTP 请求。
 */
@Service
public class ChatStreamExecutionService {

    private final ChatApplicationService chatApplicationService;
    private final ChatRuntimeGuardService chatRuntimeGuardService;
    private final ExecutorService executor;

    /**
     * 使用默认后台线程池构造聊天流派发服务。
     * @param chatApplicationService 聊天应用服务。
     * @param chatRuntimeGuardService 运行保护服务。
     */
    public ChatStreamExecutionService(
        ChatApplicationService chatApplicationService,
        ChatRuntimeGuardService chatRuntimeGuardService
    ) {
        this(chatApplicationService, chatRuntimeGuardService, Executors.newCachedThreadPool());
    }

    /**
     * 注入可替换执行器，便于测试与后续线程池治理。
     * @param chatApplicationService 聊天应用服务。
     * @param chatRuntimeGuardService 运行保护服务。
     * @param executor 后台执行器。
     */
    public ChatStreamExecutionService(
        ChatApplicationService chatApplicationService,
        ChatRuntimeGuardService chatRuntimeGuardService,
        ExecutorService executor
    ) {
        this.chatApplicationService = chatApplicationService;
        this.chatRuntimeGuardService = chatRuntimeGuardService;
        this.executor = executor;
    }

    /**
     * 异步派发聊天消息处理，同时在入口线程完成门控和取消注册。
     * @param command 聊天消息命令。
     * @param userId 当前用户标识。
     */
    public void dispatch(SendChatMessageCommand command, Long userId) {
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        chatRuntimeGuardService.registerCancellation(command.conversationId(), () -> {
            Future<?> future = futureRef.get();
            if (future != null) {
                future.cancel(true);
            }
        });
        Future<?> future = executor.submit(() -> {
            try {
                chatApplicationService.sendMessage(command, userId);
            } finally {
                chatRuntimeGuardService.completeConversation(command.conversationId());
            }
        });
        futureRef.set(future);
    }
}
