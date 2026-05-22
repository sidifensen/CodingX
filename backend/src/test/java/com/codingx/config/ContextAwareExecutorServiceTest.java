package com.codingx.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.codingx.chat.application.service.ConversationTraceContext;
import com.codingx.chat.domain.model.ChatTraceRun;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 验证上下文透传执行器会把 Trace 节点栈完整传递到异步线程，避免父子节点层级丢失。
 */
class ContextAwareExecutorServiceTest {

    @AfterEach
    void cleanUp() {
        ConversationTraceContext.clear();
    }

    /**
     * 当主线程已压入根节点后，子线程应读取到同样的 parentNodeId 与 depth。
     */
    @Test
    void shouldPropagateTraceNodeStackToAsyncTask() throws Exception {
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        ContextAwareExecutorService executorService = new ContextAwareExecutorService(delegate);
        try {
            ConversationTraceContext.bind(ChatTraceRun.builder().traceId("trace-ctx-1").traceName("chat-entry").build());
            ConversationTraceContext.pushNode("root-node-1");

            Future<NodeContextView> future = executorService.submit(() -> new NodeContextView(
                ConversationTraceContext.current() == null ? null : ConversationTraceContext.current().getTraceId(),
                ConversationTraceContext.currentNodeId(),
                ConversationTraceContext.currentDepth()
            ));

            NodeContextView view = future.get();
            assertNotNull(view);
            assertEquals("trace-ctx-1", view.traceId());
            assertEquals("root-node-1", view.parentNodeId());
            assertEquals(1, view.depth());
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * 任务执行结束后应清空工作线程上下文，避免线程复用串线到后续任务。
     */
    @Test
    void shouldClearTraceContextAfterTaskFinished() throws Exception {
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        ContextAwareExecutorService executorService = new ContextAwareExecutorService(delegate);
        try {
            ConversationTraceContext.bind(ChatTraceRun.builder().traceId("trace-ctx-2").traceName("chat-entry").build());
            ConversationTraceContext.pushNode("root-node-2");

            Future<Void> first = executorService.submit(() -> null);
            first.get();
            // 主线程清理后再次提交空上下文任务，用于校验工作线程是否残留上个任务上下文。
            ConversationTraceContext.clear();

            Future<NodeContextView> second = executorService.submit(() -> new NodeContextView(
                ConversationTraceContext.current() == null ? null : ConversationTraceContext.current().getTraceId(),
                ConversationTraceContext.currentNodeId(),
                ConversationTraceContext.currentDepth()
            ));
            NodeContextView view = second.get();
            assertNotNull(view);
            assertNull(view.traceId());
            assertNull(view.parentNodeId());
            assertEquals(0, view.depth());
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * 异步断言返回结构，简化测试可读性。
     * @param traceId 链路标识。
     * @param parentNodeId 父节点标识。
     * @param depth 栈深度。
     */
    private record NodeContextView(String traceId, String parentNodeId, int depth) {
    }
}
