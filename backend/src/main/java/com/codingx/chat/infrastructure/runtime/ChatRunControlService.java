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
        chatRuntimeStateStore.markActive(conversationId);
        sessions.put(conversationId, new ChatRunSession(cancelAction));
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
     * 正常完成后主动释放取消句柄。
     * @param conversationId 会话标识。
     */
    public void complete(Long conversationId) {
        sessions.remove(conversationId);
        chatRuntimeStateStore.clear(conversationId);
    }

    /**
     * 维护单个会话运行态与取消状态的内部会话对象。
     */
    private static final class ChatRunSession {

        private final Runnable cancelAction;
        private volatile boolean cancelled;

        private ChatRunSession(Runnable cancelAction) {
            this.cancelAction = cancelAction;
        }

        private void cancel() {
            this.cancelled = true;
            cancelAction.run();
        }

        private boolean cancelled() {
            return cancelled;
        }
    }
}
