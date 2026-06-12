package com.codingx.chat.infrastructure.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.common.error.ErrorMessageCatalog;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

/**
 * 验证聊天队列门控的最小并发、通知唤醒与租约续期行为。
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
        assertEquals(ErrorMessageCatalog.CHAT_QUEUE_BUSY, rejected.reason());
        assertEquals(null, rejected.queuePosition());
    }

    /**
     * 同一会话已有运行时必须拒绝再次进入，避免重复请求保存两条相同用户消息并启动两条执行流。
     */
    @Test
    void inMemoryAcquireRejectsSameConversationWhenAlreadyActive() {
        ConversationQueueGate gate = new ConversationQueueGate(2);

        assertTrue(gate.tryAcquire(1001L).allowed());
        QueueAcquireResult duplicate = gate.tryAcquire(1001L);

        assertFalse(duplicate.allowed());
        assertEquals(ErrorMessageCatalog.CHAT_QUEUE_BUSY, duplicate.reason());
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
        RedissonClient redissonClient = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RScoredSortedSet<String> queue = mock(RScoredSortedSet.class);
        RPermitExpirableSemaphore semaphore = mock(RPermitExpirableSemaphore.class);
        RTopic topic = mock(RTopic.class);
        AtomicBoolean firstConversationActive = new AtomicBoolean(false);
        AtomicBoolean secondQueued = new AtomicBoolean(false);

        when(redissonClient.<String>getScoredSortedSet(anyString())).thenReturn(queue);
        when(redissonClient.getPermitExpirableSemaphore(anyString())).thenReturn(semaphore);
        when(redissonClient.getTopic(anyString())).thenReturn(topic);
        when(semaphore.trySetPermits(anyInt())).thenReturn(false);

        when(queue.rank("1001")).thenReturn(0);
        when(queue.rank("1002")).thenAnswer(invocation -> secondQueued.get() ? 0 : 1);
        when(queue.add(anyDouble(), anyString())).thenAnswer(invocation -> {
            if ("1002".equals(invocation.getArgument(1, String.class))) {
                secondQueued.set(true);
            }
            return true;
        });
        when(semaphore.tryAcquire(anyLong(), anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
            if (!firstConversationActive.get()) {
                firstConversationActive.set(true);
                return "permit-1001";
            }
            return firstConversationActive.get() ? null : "permit-1002";
        });

        doAnswer(invocation -> {
            firstConversationActive.set(false);
            return null;
        }).when(semaphore).release("permit-1001");
        when(topic.publish(anyString())).thenReturn(1L);
        when(topic.countSubscribers()).thenReturn(0L);

        ConversationQueueGate gate = new ConversationQueueGate(true, 1, 500L, 50L, 300L, redissonClient);
        assertTrue(gate.tryAcquire(1001L).allowed());

        CompletableFuture<QueueAcquireResult> waitingAcquire = CompletableFuture.supplyAsync(() -> gate.tryAcquire(1002L));
        waitForPollingWindow();
        CompletableFuture<Void> releaseFuture = CompletableFuture.runAsync(() -> gate.release(1001L));

        releaseFuture.get(200, TimeUnit.MILLISECONDS);
        QueueAcquireResult result = waitingAcquire.get(800, TimeUnit.MILLISECONDS);

        assertTrue(result.allowed());
    }

    /**
     * Redis 许可释放后应广播唤醒通知，减少排队线程额外轮询等待。
     */
    @Test
    void redisReleasePublishesQueueNotify() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RScoredSortedSet<String> queue = mock(RScoredSortedSet.class);
        RPermitExpirableSemaphore semaphore = mock(RPermitExpirableSemaphore.class);
        RTopic topic = mock(RTopic.class);

        when(redissonClient.<String>getScoredSortedSet(anyString())).thenReturn(queue);
        when(redissonClient.getPermitExpirableSemaphore(anyString())).thenReturn(semaphore);
        when(redissonClient.getTopic(anyString())).thenReturn(topic);
        when(semaphore.trySetPermits(anyInt())).thenReturn(false);
        when(queue.rank("1001")).thenReturn(0);
        when(semaphore.tryAcquire(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn("permit-1001");
        when(topic.publish(anyString())).thenReturn(1L);

        ConversationQueueGate gate = new ConversationQueueGate(true, 1, 500L, 50L, 300L, redissonClient);
        assertTrue(gate.tryAcquire(1001L).allowed());
        gate.release(1001L);

        verify(topic, atLeastOnce()).publish("permit_released");
    }

    /**
     * Redis 活跃会话在租约窗口内应允许续租，防止长会话被误释放。
     */
    @Test
    void redisActiveConversationCanRenewLease() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RScoredSortedSet<String> queue = mock(RScoredSortedSet.class);
        RPermitExpirableSemaphore semaphore = mock(RPermitExpirableSemaphore.class);
        RTopic topic = mock(RTopic.class);

        when(redissonClient.<String>getScoredSortedSet(anyString())).thenReturn(queue);
        when(redissonClient.getPermitExpirableSemaphore(anyString())).thenReturn(semaphore);
        when(redissonClient.getTopic(anyString())).thenReturn(topic);
        when(semaphore.trySetPermits(anyInt())).thenReturn(false);
        when(queue.rank("1001")).thenReturn(0);
        when(semaphore.tryAcquire(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn("permit-1001");
        when(semaphore.updateLeaseTime(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(topic.publish(anyString())).thenReturn(1L);

        ConversationQueueGate gate = new ConversationQueueGate(true, 1, 500L, 50L, 300L, redissonClient);
        assertTrue(gate.tryAcquire(1001L).allowed());
        assertTrue(gate.renew(1001L));
    }

    /**
     * Redis 门控在当前节点已经持有会话许可时也必须拒绝重复进入，避免同一会话并发执行。
     */
    @Test
    void redisAcquireRejectsSameConversationWhenPermitAlreadyActive() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RScoredSortedSet<String> queue = mock(RScoredSortedSet.class);
        RPermitExpirableSemaphore semaphore = mock(RPermitExpirableSemaphore.class);
        RTopic topic = mock(RTopic.class);

        when(redissonClient.<String>getScoredSortedSet(anyString())).thenReturn(queue);
        when(redissonClient.getPermitExpirableSemaphore(anyString())).thenReturn(semaphore);
        when(redissonClient.getTopic(anyString())).thenReturn(topic);
        when(semaphore.trySetPermits(anyInt())).thenReturn(false);
        when(queue.rank("1001")).thenReturn(0);
        when(semaphore.tryAcquire(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn("permit-1001");
        when(topic.publish(anyString())).thenReturn(1L);

        ConversationQueueGate gate = new ConversationQueueGate(true, 2, 500L, 50L, 300L, redissonClient);

        assertTrue(gate.tryAcquire(1001L).allowed());
        QueueAcquireResult duplicate = gate.tryAcquire(1001L);

        assertFalse(duplicate.allowed());
        assertEquals(ErrorMessageCatalog.CHAT_QUEUE_BUSY, duplicate.reason());
    }

    /**
     * Redis 模式下若请求首轮即可获取许可，不应回调排队位置，避免前端出现瞬时排队闪烁。
     */
    @Test
    void redisImmediateAcquireShouldNotReportQueuePosition() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RScoredSortedSet<String> queue = mock(RScoredSortedSet.class);
        RPermitExpirableSemaphore semaphore = mock(RPermitExpirableSemaphore.class);
        RTopic topic = mock(RTopic.class);
        AtomicInteger queuePositionCallbackCount = new AtomicInteger(0);

        when(redissonClient.<String>getScoredSortedSet(anyString())).thenReturn(queue);
        when(redissonClient.getPermitExpirableSemaphore(anyString())).thenReturn(semaphore);
        when(redissonClient.getTopic(anyString())).thenReturn(topic);
        when(semaphore.trySetPermits(anyInt())).thenReturn(false);
        when(queue.add(anyDouble(), anyString())).thenReturn(true);
        when(queue.rank("1001")).thenReturn(0);
        when(semaphore.tryAcquire(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn("permit-1001");
        when(topic.publish(anyString())).thenReturn(1L);

        ConversationQueueGate gate = new ConversationQueueGate(true, 1, 500L, 50L, 300L, redissonClient);
        QueueAcquireResult result = gate.tryAcquire(1001L, position -> queuePositionCallbackCount.incrementAndGet());

        assertTrue(result.allowed());
        assertEquals(0, queuePositionCallbackCount.get());
    }

    /**
     * 进程内门控并发已满时应回传排队占位，便于前端展示明确提示。
     */
    @Test
    void inMemoryAcquireReportsQueuePositionWhenBusy() {
        ConversationQueueGate gate = new ConversationQueueGate(1);
        assertTrue(gate.tryAcquire(1001L).allowed());
        int[] positionHolder = new int[] {0};

        QueueAcquireResult result = gate.tryAcquire(1002L, position -> positionHolder[0] = position);

        assertFalse(result.allowed());
        assertEquals(ErrorMessageCatalog.CHAT_QUEUE_BUSY, result.reason());
        assertEquals(1, positionHolder[0]);
    }

    /**
     * 线程被中断后释放 Redis 信号量时，如果不清除中断标志，Redisson 的后续清理操作会失败。
     * 本测试验证当前实现是否正确处理中断以避免信号量泄漏。
     */
    @Test
    void redisReleaseShouldNotLeakPermitWhenThreadInterrupted() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RScoredSortedSet<String> queue = mock(RScoredSortedSet.class);
        RPermitExpirableSemaphore semaphore = mock(RPermitExpirableSemaphore.class);
        RTopic topic = mock(RTopic.class);

        when(redissonClient.<String>getScoredSortedSet(anyString())).thenReturn(queue);
        when(redissonClient.getPermitExpirableSemaphore(anyString())).thenReturn(semaphore);
        when(redissonClient.getTopic(anyString())).thenReturn(topic);
        when(semaphore.trySetPermits(anyInt())).thenReturn(false);
        when(queue.rank("1001")).thenReturn(0);
        when(semaphore.tryAcquire(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn("permit-1001");

        // 模拟 Redisson 在线程中断时的真实行为：只要线程中断标志被设置，所有阻塞操作都会失败
        doAnswer(invocation -> {
            if (Thread.currentThread().isInterrupted()) {
                throw new org.redisson.client.RedisException(new InterruptedException("Thread interrupted during release"));
            }
            return null;
        }).when(semaphore).release("permit-1001");

        when(queue.remove(anyString())).thenAnswer(invocation -> {
            if (Thread.currentThread().isInterrupted()) {
                throw new org.redisson.client.RedisException(new InterruptedException("Thread interrupted during queue cleanup"));
            }
            return true;
        });

        when(topic.publish(anyString())).thenAnswer(invocation -> {
            if (Thread.currentThread().isInterrupted()) {
                throw new org.redisson.client.RedisException(new InterruptedException("Thread interrupted during publish"));
            }
            return 1L;
        });

        ConversationQueueGate gate = new ConversationQueueGate(true, 1, 500L, 50L, 300L, redissonClient);

        // 步骤 1：获取许可
        assertTrue(gate.tryAcquire(1001L).allowed());

        // 步骤 2：模拟线程中断（用户点击停止）
        Thread.currentThread().interrupt();

        // 步骤 3：释放许可（当前实现会因中断而导致清理失败，修复后应该成功）
        gate.release(1001L);

        // 步骤 4：验证所有清理操作都完成了（如果中断标志没有被正确处理，这些调用不会成功）
        verify(semaphore).release("permit-1001");
        verify(queue, atLeastOnce()).remove("1001");  // acquire 时也会调用 remove
        verify(topic, atLeastOnce()).publish("permit_released");  // acquire 和 release 时都会调用

        // 步骤 5：验证中断标志最终被恢复（保证调用方能感知到中断）
        assertTrue(Thread.interrupted(), "中断标志应该被恢复");
    }

    /**
     * 给等待线程一个最小轮询窗口，确保测试进入真实排队状态。
     */
    private void waitForPollingWindow() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(Duration.ofMillis(120).toMillis());
    }
}
