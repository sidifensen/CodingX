package com.codingx.chat.infrastructure.stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 验证聊天 SSE 注册中心在浏览器断开后只清理连接，不把写失败升级为业务异常。
 */
class ChatSseRegistryTest {

    /**
     * 浏览器断开期间产生的运行中事件应保存在会话缓冲区，新页面重新订阅后立即回放。
     */
    @Test
    void registerReplaysEventsPublishedWithoutActiveEmitter() throws Exception {
        ChatSseRegistry registry = new ChatSseRegistry();

        registry.publish(1L, "message", Map.of("delta", "后台继续输出"));

        SseEmitter emitter = registry.register(1L);

        assertFalse(readEarlySendAttempts(emitter).isEmpty());
    }

    /**
     * 任务完成后运行期缓冲必须清理，避免下一轮打开会话时看到上一轮旧事件。
     */
    @Test
    void completeClearsBufferedEventsForConversation() throws Exception {
        ChatSseRegistry registry = new ChatSseRegistry();

        registry.publish(1L, "message", Map.of("delta", "上一轮输出"));
        registry.complete(1L);
        SseEmitter emitter = registry.register(1L);

        assertTrue(readEarlySendAttempts(emitter).isEmpty());
    }

    /**
     * 重连订阅建立时只应回放已缓冲事件；后续 live publish 再按顺序发送，避免重连瞬间重复或乱序。
     */
    @Test
    void registerDoesNotReceiveLiveEventThatWasPublishedAfterReplay() {
        ChatSseRegistry registry = new ChatSseRegistry();

        registry.publish(1L, "message", Map.of("delta", "断线期间输出"));
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        registry.register(1L, emitter);
        registry.publish(1L, "message", Map.of("delta", "重连后输出"));

        assertTrue(emitter.payloads().get(0).contains("断线期间输出"));
        assertTrue(emitter.payloads().get(1).contains("重连后输出"));
    }

    /**
     * Spring 可能把底层客户端断开包装为运行时异常，发布端应识别并移除失效 emitter。
     */
    @Test
    void publishRemovesEmitterWhenClientAbortIsWrappedAsRuntimeException() throws Exception {
        ChatSseRegistry registry = new ChatSseRegistry();
        ThrowingSseEmitter emitter = new ThrowingSseEmitter(
            new IllegalStateException(
                "Response not usable after response errors",
                new AsyncRequestNotUsableException(
                    "Response not usable after response errors",
                    new IOException("你的主机中的软件中止了一个已建立的连接。")
                )
            )
        );
        registry.register(1L, emitter);

        assertDoesNotThrow(() -> registry.publish(1L, "message", Map.of("delta", "test")));
        assertFalse(readRegisteredEmitters(registry, 1L).contains(emitter));
    }

    /**
     * 通过反射读取真实注册表中的连接列表，避免为测试暴露额外生产 API。
     */
    @SuppressWarnings("unchecked")
    private List<SseEmitter> readRegisteredEmitters(ChatSseRegistry registry, Long conversationId) throws Exception {
        Field field = ChatSseRegistry.class.getDeclaredField("streamStates");
        field.setAccessible(true);
        Map<Long, Object> streamStates = (Map<Long, Object>) field.get(registry);
        Object state = streamStates.get(conversationId);
        if (state == null) {
            return List.of();
        }
        Method method = state.getClass().getDeclaredMethod("emitters");
        method.setAccessible(true);
        return (CopyOnWriteArrayList<SseEmitter>) method.invoke(state);
    }

    /**
     * 读取 Spring 在响应真正绑定前暂存的发送事件，用于断言 register 是否回放了缓冲事件。
     */
    @SuppressWarnings("unchecked")
    private Set<Object> readEarlySendAttempts(SseEmitter emitter) throws Exception {
        Field field = ResponseBodyEmitter.class.getDeclaredField("earlySendAttempts");
        field.setAccessible(true);
        return (Set<Object>) field.get(emitter);
    }

    /**
     * 记录发送内容的 emitter，用于验证重连回放和后续 live 事件的顺序。
     */
    private static class RecordingSseEmitter extends SseEmitter {

        private final List<String> payloads = new ArrayList<>();

        private RecordingSseEmitter() {
            super(0L);
        }

        @Override
        public void send(SseEventBuilder builder) {
            payloads.add(builder.build().stream()
                .map(DataWithMediaType::getData)
                .map(String::valueOf)
                .toList()
                .toString());
        }

        private List<String> payloads() {
            return payloads;
        }
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
