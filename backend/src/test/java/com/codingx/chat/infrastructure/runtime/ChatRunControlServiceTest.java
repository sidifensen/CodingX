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
}
