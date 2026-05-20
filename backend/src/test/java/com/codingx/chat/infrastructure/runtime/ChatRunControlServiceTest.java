package com.codingx.chat.infrastructure.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * 验证聊天运行取消控制服务的最小行为。
 */
class ChatRunControlServiceTest {

    /**
     * 注册中的会话应支持取消，并在取消后执行注册的收口动作。
     */
    @Test
    void cancelTriggersRegisteredActionAndClearsRunningState() {
        ChatRunControlService service = new ChatRunControlService(new InMemoryChatRuntimeStateStore());
        AtomicBoolean cancelled = new AtomicBoolean(false);

        service.register(1001L, () -> cancelled.set(true));
        assertTrue(service.isRunning(1001L));

        assertTrue(service.cancel(1001L));
        assertTrue(cancelled.get());
        assertFalse(service.isRunning(1001L));
    }

    /**
     * 未注册的会话取消请求应安全返回 false。
     */
    @Test
    void cancelReturnsFalseForUnknownConversation() {
        ChatRunControlService service = new ChatRunControlService(new InMemoryChatRuntimeStateStore());

        assertFalse(service.cancel(9999L));
    }

    /**
     * 同一会话开启新 run 后，旧 run 应视为过期，避免旧流继续写回新请求页面。
     */
    @Test
    void oldRunShouldBeTreatedAsCancelledAfterNewRunRegistered() {
        ChatRunControlService service = new ChatRunControlService(new InMemoryChatRuntimeStateStore());
        service.register(1001L, 9001L, () -> {
        });
        service.register(1001L, 9002L, () -> {
        });

        assertTrue(service.isCancelled(1001L, 9001L));
        assertFalse(service.isCancelled(1001L, 9002L));
    }

    /**
     * 旧 run 完成时不得清理当前新 run 的状态，否则会让新请求失去取消/门控保护。
     */
    @Test
    void completeOldRunMustNotClearCurrentRunState() {
        ChatRunControlService service = new ChatRunControlService(new InMemoryChatRuntimeStateStore());
        service.register(1001L, 9001L, () -> {
        });
        service.register(1001L, 9002L, () -> {
        });

        assertFalse(service.complete(1001L, 9001L));
        assertTrue(service.isRunning(1001L));
        assertFalse(service.isCancelled(1001L, 9002L));
    }
}
