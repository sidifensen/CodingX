package com.codingx.chat.application.service;

import com.codingx.chat.domain.service.ChatStreamPublisher;
import com.codingx.chat.infrastructure.runtime.ChatRunControlService;
import com.codingx.chat.infrastructure.runtime.ConversationQueueGate;
import com.codingx.chat.infrastructure.runtime.QueueAcquireResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 统一封装聊天运行中的队列准入、取消收口与释放逻辑。
 */
@Service
@RequiredArgsConstructor
public class ChatRuntimeGuardService {

    /**
     * 队列门控依赖。
     */
    private final ConversationQueueGate conversationQueueGate;

    /**
     * 取消控制依赖。
     */
    private final ChatRunControlService chatRunControlService;

    /**
     * 流式事件发布依赖。
     */
    private final ChatStreamPublisher chatStreamPublisher;

    /**
     * 确保当前会话已获取执行资格，否则立刻发出 reject 并中断。
     * @param conversationId 会话标识。
     */
    public void ensureAccepted(Long conversationId) {
        QueueAcquireResult result = conversationQueueGate.tryAcquire(conversationId);
        if (!result.allowed()) {
            chatStreamPublisher.publishRejected(conversationId, result.reason());
            throw new IllegalStateException("Conversation rejected: " + result.reason());
        }
    }

    /**
     * 注册当前会话的取消句柄。
     * @param conversationId 会话标识。
     * @param cancelAction 取消动作。
     */
    public void registerCancellation(Long conversationId, Runnable cancelAction) {
        chatRunControlService.register(conversationId, cancelAction);
    }

    /**
     * 判断指定会话是否已收到取消信号。
     * @param conversationId 会话标识。
     * @return 是否已取消。
     */
    public boolean isCancelled(Long conversationId) {
        return chatRunControlService.isCancelled(conversationId);
    }

    /**
     * 触发指定会话的取消收口。
     * @param conversationId 会话标识。
     */
    public void cancelConversation(Long conversationId) {
        if (chatRunControlService.cancel(conversationId)) {
            conversationQueueGate.release(conversationId);
            chatStreamPublisher.publishCancelled(conversationId);
        }
    }

    /**
     * 正常完成后释放门控与取消句柄。
     * @param conversationId 会话标识。
     */
    public void completeConversation(Long conversationId) {
        conversationQueueGate.release(conversationId);
        chatRunControlService.complete(conversationId);
    }
}
