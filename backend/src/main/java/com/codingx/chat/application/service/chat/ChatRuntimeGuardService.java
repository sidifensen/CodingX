package com.codingx.chat.application.service;

import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.ConflictException;
import com.codingx.chat.domain.port.ChatStreamPublisher;
import com.codingx.chat.infrastructure.runtime.ChatRunControlService;
import com.codingx.chat.infrastructure.runtime.ConversationQueueGate;
import com.codingx.chat.infrastructure.runtime.ConversationQueueSnapshot;
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
     * 会话队列门控，负责同一会话运行资格获取、排队位置计算和完成后释放。
     */
    private final ConversationQueueGate conversationQueueGate;

    /**
     * 聊天运行取消控制服务，负责登记取消句柄并判断当前 run 是否已被用户取消。
     */
    private final ChatRunControlService chatRunControlService;

    /**
     * 流式事件发布端口，用于把排队、拒绝、取消等运行态事件推送给前端。
     */
    private final ChatStreamPublisher chatStreamPublisher;

    /**
     * 确保当前会话已获取执行资格，否则立刻发出 reject 并中断。
     * @param conversationId 会话标识。
     */
    public void ensureAccepted(Long conversationId) {
        // 步骤 1：尝试获取当前会话执行资格；排队期间持续把队列位置推送给前端。
        QueueAcquireResult result = conversationQueueGate.tryAcquire(conversationId, position ->
            chatStreamPublisher.publishQueued(conversationId, position)
        );
        if (!result.allowed() && !"queued".equalsIgnoreCase(result.reason())) {
            // 步骤 2：非排队型拒绝直接通知前端并抛出冲突异常，中断后续模型调用。
            chatStreamPublisher.publishRejected(conversationId, result.reason());
            throw new ConflictException(ErrorMessageCatalog.CHAT_QUEUE_BUSY);
        }
        // 步骤 3：获取执行资格后通知前端关闭排队提示。
        chatStreamPublisher.publishQueueAccepted(conversationId);
    }

    /**
     * 注册当前会话的取消句柄。
     * @param conversationId 会话标识。
     * @param cancelAction 取消动作。
     */
    public void registerCancellation(Long conversationId, Runnable cancelAction) {
        // 步骤 1：按会话登记取消动作，供用户点击停止时快速中断当前运行。
        chatRunControlService.register(conversationId, cancelAction);
    }

    /**
     * 注册指定运行实例的取消句柄。
     * @param conversationId 会话标识。
     * @param runId 运行标识。
     * @param cancelAction 取消动作。
     */
    public void registerCancellation(Long conversationId, Long runId, Runnable cancelAction) {
        // 步骤 1：按会话和 run 双维度登记取消动作，避免旧 run 误取消新 run。
        chatRunControlService.register(conversationId, runId, cancelAction);
    }

    /**
     * 判断指定会话是否已收到取消信号。
     * @param conversationId 会话标识。
     * @return 是否已取消。
     */
    public boolean isCancelled(Long conversationId) {
        // 步骤 1：查询会话级取消状态，供长流程在关键节点主动停止。
        return chatRunControlService.isCancelled(conversationId);
    }

    /**
     * 判断指定运行实例是否已取消或过期。
     * @param conversationId 会话标识。
     * @param runId 运行标识。
     * @return 是否已取消或过期。
     */
    public boolean isCancelled(Long conversationId, Long runId) {
        // 步骤 1：查询 run 级取消或过期状态，避免异步旧任务继续写入新会话流。
        return chatRunControlService.isCancelled(conversationId, runId);
    }

    /**
     * 触发指定会话的取消收口。
     * @param conversationId 会话标识。
     */
    public void cancelConversation(Long conversationId) {
        if (chatRunControlService.cancel(conversationId)) {
            // 步骤 1：取消成功后释放队列资格，避免该会话长期占用运行 permit。
            conversationQueueGate.release(conversationId);
            // 步骤 2：发布取消终态并关闭 SSE 通道。
            chatStreamPublisher.publishCancelled(conversationId);
        }
    }

    /**
     * 正常完成后释放门控与取消句柄。
     * @param conversationId 会话标识。
     */
    public void completeConversation(Long conversationId) {
        // 步骤 1：正常完成后释放队列资格。
        conversationQueueGate.release(conversationId);
        // 步骤 2：清理会话级取消句柄，避免下次运行误读旧状态。
        chatRunControlService.complete(conversationId);
    }

    /**
     * 指定运行实例完成后释放门控与取消句柄，避免旧 run 误清理新 run。
     * @param conversationId 会话标识。
     * @param runId 运行标识。
     */
    public void completeConversation(Long conversationId, Long runId) {
        // 步骤 1：释放队列资格，允许同会话后续请求继续执行。
        conversationQueueGate.release(conversationId);
        // 步骤 2：按 run 清理取消句柄，防止旧 run 完成时覆盖新 run 状态。
        chatRunControlService.complete(conversationId, runId);
    }

    /**
     * 返回当前队列快照，供管理端展示运行时并发状态。
     * @return 队列快照。
     */
    public ConversationQueueSnapshot snapshot() {
        // 步骤 1：返回队列门控当前快照，供管理端只读展示运行态。
        return conversationQueueGate.snapshot();
    }
}

