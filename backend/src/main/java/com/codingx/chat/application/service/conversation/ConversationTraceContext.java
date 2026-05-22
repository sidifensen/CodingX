package com.codingx.chat.application.service;

import com.codingx.chat.domain.model.ChatTraceRun;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * 维护当前线程上的聊天 Trace 上下文。
 */
public final class ConversationTraceContext {

    private static final ThreadLocal<ChatTraceRun> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Deque<String>> NODE_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private ConversationTraceContext() {
    }

    /**
     * 创建并绑定当前链路的根 Trace 上下文。
     * @param traceName 链路名称。
     * @param conversationId 会话标识。
     * @param userId 用户标识。
     * @return 根 Trace 记录。
     */
    public static ChatTraceRun start(String traceName, Long conversationId, Long userId) {
        ChatTraceRun traceRun = ChatTraceRun.builder()
            .traceId(UUID.randomUUID().toString())
            .traceName(traceName)
            .conversationId(conversationId)
            .userId(userId)
            .status("RUNNING")
            .build();
        CURRENT.set(traceRun);
        return traceRun;
    }

    /**
     * 显式绑定既有根 Trace，供异步线程继续沿用同一条链路上下文。
     * @param traceRun 根 Trace。
     */
    public static void bind(ChatTraceRun traceRun) {
        CURRENT.set(traceRun);
    }

    /**
     * 显式绑定既有根 Trace 与节点栈快照，用于异步线程恢复父子节点层级。
     * @param traceRun 根 Trace。
     * @param nodeStackSnapshot 节点栈快照，栈顶应为当前节点。
     */
    public static void bind(ChatTraceRun traceRun, Deque<String> nodeStackSnapshot) {
        CURRENT.set(traceRun);
        Deque<String> nextStack = new ArrayDeque<>();
        if (nodeStackSnapshot != null && !nodeStackSnapshot.isEmpty()) {
            nextStack.addAll(nodeStackSnapshot);
        }
        NODE_STACK.set(nextStack);
    }

    /**
     * 返回当前线程绑定的根 Trace。
     * @return 当前 Trace。
     */
    public static ChatTraceRun current() {
        return CURRENT.get();
    }

    /**
     * 清理当前线程上的 Trace 上下文。
     */
    public static void clear() {
        CURRENT.remove();
        NODE_STACK.remove();
    }

    /**
     * 压入当前执行节点标识。
     * @param nodeId 节点标识。
     */
    public static void pushNode(String nodeId) {
        NODE_STACK.get().push(nodeId);
    }

    /**
     * 弹出当前执行节点标识。
     */
    public static void popNode() {
        if (!NODE_STACK.get().isEmpty()) {
            NODE_STACK.get().pop();
        }
    }

    /**
     * 返回当前父节点标识。
     * @return 父节点标识。
     */
    public static String currentNodeId() {
        return NODE_STACK.get().peek();
    }

    /**
     * 返回当前节点深度。
     * @return 节点深度。
     */
    public static int currentDepth() {
        return NODE_STACK.get().size();
    }

    /**
     * 快照当前节点栈，供线程池任务提交时深拷贝父子层级上下文。
     * @return 节点栈副本。
     */
    public static Deque<String> snapshotNodeStack() {
        return new ArrayDeque<>(NODE_STACK.get());
    }
}
