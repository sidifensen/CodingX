package com.codingx.chat.infrastructure.stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 验证聊天 SSE 注册中心在浏览器断开后只清理连接，不把写失败升级为业务异常。
 */
class ChatSseRegistryTest {

    /**
     * Spring 可能把底层客户端断开包装为运行时异常，发布端应识别并移除失效 emitter。
     */
    @Test
    void publishRemovesEmitterWhenClientAbortIsWrappedAsRuntimeException() throws Exception {
        ChatSseRegistry registry = new ChatSseRegistry();
        Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = readEmitters(registry);
        emitters.put(1L, new CopyOnWriteArrayList<>(List.of(new ThrowingSseEmitter(
            new IllegalStateException(
                "Response not usable after response errors",
                new AsyncRequestNotUsableException(
                    "Response not usable after response errors",
                    new IOException("你的主机中的软件中止了一个已建立的连接。")
                )
            )
        ))));

        assertDoesNotThrow(() -> registry.publish(1L, "message", Map.of("delta", "test")));
        assertFalse(emitters.containsKey(1L));
    }

    /**
     * 通过反射读取真实注册表，避免为测试暴露额外生产 API。
     */
    @SuppressWarnings("unchecked")
    private Map<Long, CopyOnWriteArrayList<SseEmitter>> readEmitters(ChatSseRegistry registry) throws Exception {
        Field field = ChatSseRegistry.class.getDeclaredField("emitters");
        field.setAccessible(true);
        return (Map<Long, CopyOnWriteArrayList<SseEmitter>>) field.get(registry);
    }

    /**
     * 模拟 ResponseBodyEmitter 把客户端断开包装成运行时异常的发送器。
     */
    private static class ThrowingSseEmitter extends SseEmitter {

        private final RuntimeException exception;

        private ThrowingSseEmitter(RuntimeException exception) {
            super(0L);
            this.exception = exception;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            throw exception;
        }
    }
}
