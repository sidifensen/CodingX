package com.codingx.chat.infrastructure.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 维护聊天运行中的取消句柄，供后续取消接口与跨线程收口逻辑复用。
 */
@Component
public class ChatRunControlService {

    private final ChatRuntimeStateStore chatRuntimeStateStore;
    private final Map<Long, ChatRunSession> sessions = new ConcurrentHashMap<>();

    /**
     * 注入运行态存储实现，统一维护活动态和取消态。
     * @param chatRuntimeStateStore 运行态存储。
     */
    public ChatRunControlService(ChatRuntimeStateStore chatRuntimeStateStore) {
        this.chatRuntimeStateStore = chatRuntimeStateStore;
    }

    /**
     * 注册某个会话的取消动作。
     * @param conversationId 会话标识。
     * @param cancelAction 取消动作。
     */
    public void register(Long conversationId, Runnable cancelAction) {
        register(conversationId, conversationId, cancelAction);
    }

    /**
     * 注册某个会话运行实例的取消动作。
     * 关键约束：同一会话开启新 run 后，旧 run 必须视为“过期”并停止回写。
     * @param conversationId 会话标识。
     * @param runId 本次运行标识。
     * @param cancelAction 取消动作。
     */
    public void register(Long conversationId, Long runId, Runnable cancelAction) {
        chatRuntimeStateStore.markActive(conversationId);
        sessions.put(conversationId, new ChatRunSession(runId, cancelAction));
    }

    /**
     * 取消指定会话的运行任务。
     * @param conversationId 会话标识。
     * @return 是否成功找到并执行取消动作。
     */
    public boolean cancel(Long conversationId) {
        ChatRunSession session = sessions.get(conversationId);
        if (session == null || session.cancelled()) {
            return false;
        }
        session.cancel();
        chatRuntimeStateStore.markCancelled(conversationId);
        return true;
    }

    /**
     * 判断当前会话是否仍处于运行状态。
     * @param conversationId 会话标识。
     * @return 是否运行中。
     */
    public boolean isRunning(Long conversationId) {
        return chatRuntimeStateStore.isActive(conversationId);
    }

    /**
     * 判断当前会话是否已收到取消信号。
     * @param conversationId 会话标识。
     * @return 是否已取消。
     */
    public boolean isCancelled(Long conversationId) {
        return chatRuntimeStateStore.isCancelled(conversationId);
    }

    /**
     * 判断指定 run 是否已被取消或已过期。
     * @param conversationId 会话标识。
     * @param runId 运行标识。
     * @return 当前 run 是否不可继续写回。
     */
    public boolean isCancelled(Long conversationId, Long runId) {
        ChatRunSession session = sessions.get(conversationId);
        if (session == null) {
            return chatRuntimeStateStore.isCancelled(conversationId);
        }
        if (!session.matchesRun(runId)) {
            return true;
        }
        return session.cancelled();
    }

    /**
     * 正常完成后主动释放取消句柄。
     * @param conversationId 会话标识。
     */
    public void complete(Long conversationId) {
        complete(conversationId, conversationId);
    }

    /**
     * 指定 run 正常完成后释放取消句柄。
     * @param conversationId 会话标识。
     * @param runId 运行标识。
     * @return 是否完成了当前激活 run 的清理。
     */
    public boolean complete(Long conversationId, Long runId) {
        ChatRunSession session = sessions.get(conversationId);
        if (session == null || !session.matchesRun(runId)) {
            return false;
        }
        sessions.remove(conversationId);
        chatRuntimeStateStore.clear(conversationId);
        return true;
    }

    /**
     * 维护单个会话运行态与取消状态的内部会话对象。
     */
    private static final class ChatRunSession {

        private final Long runId;
        private final Runnable cancelAction;
        private volatile boolean cancelled;

        private ChatRunSession(Long runId, Runnable cancelAction) {
            this.runId = runId;
            this.cancelAction = cancelAction;
        }

        private void cancel() {
            this.cancelled = true;
            cancelAction.run();
        }

        private boolean cancelled() {
            return cancelled;
        }

        private boolean matchesRun(Long targetRunId) {
            return runId != null && runId.equals(targetRunId);
        }
    }
}
