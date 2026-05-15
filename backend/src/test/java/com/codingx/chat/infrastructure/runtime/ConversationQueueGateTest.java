package com.codingx.chat.infrastructure.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 验证聊天队列门控的最小并发与拒绝行为。
 */
class ConversationQueueGateTest {

    /**
     * 超过并发上限时应立即拒绝新的会话执行。
     */
    @Test
    void acquireRejectsWhenConcurrencyLimitReached() {
        ConversationQueueGate gate = new ConversationQueueGate(1);

        assertTrue(gate.tryAcquire(1001L).allowed());
        QueueAcquireResult rejected = gate.tryAcquire(1002L);

        assertFalse(rejected.allowed());
        assertEquals("busy", rejected.reason());
    }

    /**
     * 释放 permit 后后续请求应可继续获取执行资格。
     */
    @Test
    void releaseAllowsNextConversationToProceed() {
        ConversationQueueGate gate = new ConversationQueueGate(1);
        gate.tryAcquire(1001L);

        gate.release(1001L);
        QueueAcquireResult result = gate.tryAcquire(1002L);

        assertTrue(result.allowed());
    }

    /**
     * Redis 排队模式下，等待中的请求不应阻塞前一个会话释放许可。
     */
    @Test
    void redisWaiterCanProceedAfterPermitReleased() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        AtomicBoolean firstConversationActive = new AtomicBoolean(false);

        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
            .thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                List<String> keys = invocation.getArgument(1, List.class);
                String member = invocation.getArgument(2, String.class);
                if (keys.size() != 3) {
                    return "wait";
                }
                if ("1001".equals(member)) {
                    firstConversationActive.set(true);
                    return "granted";
                }
                return firstConversationActive.get() ? "wait" : "granted";
            });
        when(redisTemplate.execute(any(RedisScript.class), anyList()))
            .thenAnswer(invocation -> {
                firstConversationActive.set(false);
                return 1L;
            });

        ConversationQueueGate gate = new ConversationQueueGate(true, 1, 500L, 50L, 300L, redisTemplate);
        assertTrue(gate.tryAcquire(1001L).allowed());

        CompletableFuture<QueueAcquireResult> waitingAcquire = CompletableFuture.supplyAsync(() -> gate.tryAcquire(1002L));
        waitForPollingWindow();
        CompletableFuture<Void> releaseFuture = CompletableFuture.runAsync(() -> gate.release(1001L));

        releaseFuture.get(200, TimeUnit.MILLISECONDS);
        QueueAcquireResult result = waitingAcquire.get(800, TimeUnit.MILLISECONDS);

        assertTrue(result.allowed());
    }

    /**
     * 给等待线程一个最小轮询窗口，确保测试进入真实排队状态。
     */
    private void waitForPollingWindow() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(Duration.ofMillis(120).toMillis());
    }
}
