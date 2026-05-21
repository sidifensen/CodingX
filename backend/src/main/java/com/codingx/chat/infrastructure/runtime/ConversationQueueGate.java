package com.codingx.chat.infrastructure.runtime;

import com.codingx.config.RuntimeProperties;
import com.codingx.chat.application.service.RuntimeSettingService;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 提供聊天链路并发门控，支持进程内和 Redisson 分布式信号量两种实现。
 */
@Component
@Slf4j
public class ConversationQueueGate {

    private static final String SEMAPHORE_NAME = "chat:queue:semaphore";
    private static final String QUEUE_KEY = "chat:queue:waiting";
    private static final String NOTIFY_TOPIC = "chat:queue:notify";

    private final boolean useRedisQueueGate;
    private final int maxConcurrent;
    private final long queueAcquireTimeoutMs;
    private final long queuePollIntervalMs;
    private final long queueLeaseSeconds;
    private final long queueLeaseRenewIntervalMs;
    private final RedissonClient redissonClient;
    private final RuntimeSettingService runtimeSettingService;

    private final Map<Long, Boolean> activeConversations = new ConcurrentHashMap<>();
    private final Map<Long, String> permitByConversation = new ConcurrentHashMap<>();
    private final Object inMemoryMonitor = new Object();
    private final ScheduledExecutorService renewScheduler;

    /**
     * Spring 运行时构造器，按配置决定是否启用 Redisson 队列门控。
     * @param runtimeProperties 运行时配置。
     * @param redissonClientProvider Redisson 客户端提供者。
     */
    @Autowired
    public ConversationQueueGate(
        RuntimeProperties runtimeProperties,
        RuntimeSettingService runtimeSettingService,
        ObjectProvider<RedissonClient> redissonClientProvider
    ) {
        this(
            runtimeProperties.isUseRedisQueueGate(),
            runtimeProperties.getQueueMaxConcurrent(),
            runtimeProperties.getQueueAcquireTimeoutMs(),
            runtimeProperties.getQueuePollIntervalMs(),
            runtimeProperties.getQueueLeaseSeconds(),
            runtimeProperties.getQueueLeaseRenewIntervalMs(),
            redissonClientProvider.getIfAvailable(),
            runtimeSettingService
        );
    }

    /**
     * 测试构造器，默认使用进程内门控。
     * @param maxConcurrent 最大并发数。
     */
    public ConversationQueueGate(int maxConcurrent) {
        this(false, maxConcurrent, 3000L, 200L, 300L, 10000L, null, null);
    }

    /**
     * 显式构造器，便于测试和运行时复用。
     * @param useRedisQueueGate 是否启用 Redisson 门控。
     * @param maxConcurrent 最大并发数。
     * @param queueAcquireTimeoutMs 获取超时时间。
     * @param queuePollIntervalMs 轮询间隔。
     * @param queueLeaseSeconds 租约秒数。
     * @param queueLeaseRenewIntervalMs 租约续期间隔毫秒数。
     * @param redissonClient Redisson 客户端。
     */
    public ConversationQueueGate(
        boolean useRedisQueueGate,
        int maxConcurrent,
        long queueAcquireTimeoutMs,
        long queuePollIntervalMs,
        long queueLeaseSeconds,
        long queueLeaseRenewIntervalMs,
        RedissonClient redissonClient,
        RuntimeSettingService runtimeSettingService
    ) {
        this.useRedisQueueGate = useRedisQueueGate;
        this.runtimeSettingService = runtimeSettingService;
        this.maxConcurrent = Math.max(
            1,
            runtimeSettingService == null ? maxConcurrent : runtimeSettingService.queueMaxConcurrent()
        );
        this.queueAcquireTimeoutMs = Math.max(
            200L,
            runtimeSettingService == null ? queueAcquireTimeoutMs : runtimeSettingService.queueAcquireTimeoutMs()
        );
        this.queuePollIntervalMs = Math.max(
            50L,
            runtimeSettingService == null ? queuePollIntervalMs : runtimeSettingService.queuePollIntervalMs()
        );
        this.queueLeaseSeconds = Math.max(
            30L,
            runtimeSettingService == null ? queueLeaseSeconds : runtimeSettingService.queueLeaseSeconds()
        );
        long configuredRenewInterval = runtimeSettingService == null
            ? queueLeaseRenewIntervalMs
            : runtimeSettingService.queueLeaseRenewIntervalMs();
        this.queueLeaseRenewIntervalMs = normalizeRenewInterval(this.queueLeaseSeconds, configuredRenewInterval);
        this.redissonClient = redissonClient;
        this.renewScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "chat-queue-lease-renew");
            thread.setDaemon(true);
            return thread;
        });
        scheduleLeaseRenewalTask();
    }

    /**
     * 兼容旧构造签名，未显式传续租间隔时按租约时长自动推导。
     * @param useRedisQueueGate 是否启用 Redis 门控。
     * @param maxConcurrent 最大并发数。
     * @param queueAcquireTimeoutMs 获取超时时间。
     * @param queuePollIntervalMs 轮询间隔。
     * @param queueLeaseSeconds 租约秒数。
     * @param redissonClient Redisson 客户端。
     */
    public ConversationQueueGate(
        boolean useRedisQueueGate,
        int maxConcurrent,
        long queueAcquireTimeoutMs,
        long queuePollIntervalMs,
        long queueLeaseSeconds,
        RedissonClient redissonClient
    ) {
        this(
            useRedisQueueGate,
            maxConcurrent,
            queueAcquireTimeoutMs,
            queuePollIntervalMs,
            queueLeaseSeconds,
            defaultRenewInterval(queueLeaseSeconds),
            redissonClient,
            null
        );
    }

    /**
     * 尝试为指定会话获取执行资格。
     * @param conversationId 会话标识。
     * @return 获取结果。
     */
    public QueueAcquireResult tryAcquire(Long conversationId) {
        return tryAcquire(conversationId, null);
    }

    /**
     * 尝试为指定会话获取执行资格，并在排队期间回调当前位置。
     * @param conversationId 会话标识。
     * @param queuePositionConsumer 排队位置回调，可为空。
     * @return 获取结果。
     */
    public QueueAcquireResult tryAcquire(Long conversationId, IntConsumer queuePositionConsumer) {
        if (!useRedisQueueGate || redissonClient == null) {
            synchronized (inMemoryMonitor) {
                return tryAcquireInMemory(conversationId, queuePositionConsumer);
            }
        }
        return tryAcquireWithRedisson(conversationId, queuePositionConsumer);
    }

    /**
     * 释放指定会话占用的执行资格。
     * @param conversationId 会话标识。
     */
    public void release(Long conversationId) {
        if (!useRedisQueueGate || redissonClient == null) {
            synchronized (inMemoryMonitor) {
                activeConversations.remove(conversationId);
            }
            return;
        }
        String permitId = permitByConversation.remove(conversationId);
        if (permitId == null) {
            redissonClient.getScoredSortedSet(QUEUE_KEY).remove(member(conversationId));
            return;
        }
        try {
            redissonClient.getPermitExpirableSemaphore(SEMAPHORE_NAME).release(permitId);
        } catch (RuntimeException exception) {
            log.warn("释放会话许可失败，conversationId={}", conversationId, exception);
        } finally {
            redissonClient.getScoredSortedSet(QUEUE_KEY).remove(member(conversationId));
            publishQueueNotify();
        }
    }

    /**
     * 续租 Redis 中的活动会话，防止长会话因租约超时被误释放。
     * @param conversationId 会话标识。
     * @return 续租是否成功。
     */
    public boolean renew(Long conversationId) {
        if (!useRedisQueueGate || redissonClient == null) {
            return true;
        }
        String permitId = permitByConversation.get(conversationId);
        if (permitId == null) {
            return false;
        }
        try {
            return redissonClient.getPermitExpirableSemaphore(SEMAPHORE_NAME)
                .updateLeaseTime(permitId, queueLeaseSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException exception) {
            permitByConversation.remove(conversationId);
            log.warn("续租会话许可失败，conversationId={}", conversationId, exception);
            return false;
        }
    }

    /**
     * 返回当前队列运行时快照，供管理端展示并发与排队指标。
     * @return 队列快照。
     */
    public ConversationQueueSnapshot snapshot() {
        if (!useRedisQueueGate || redissonClient == null) {
            synchronized (inMemoryMonitor) {
                int activeCount = activeConversations.size();
                int waitingCount = 0;
                return new ConversationQueueSnapshot(
                    "memory",
                    maxConcurrent,
                    activeCount,
                    waitingCount,
                    Math.max(0, maxConcurrent - activeCount)
                );
            }
        }
        RPermitExpirableSemaphore semaphore = redissonClient.getPermitExpirableSemaphore(SEMAPHORE_NAME);
        semaphore.trySetPermits(maxConcurrent);
        int availablePermits = Math.max(0, semaphore.availablePermits());
        int activeCount = Math.max(0, maxConcurrent - availablePermits);
        int waitingCount = Math.max(0, redissonClient.getScoredSortedSet(QUEUE_KEY).size());
        return new ConversationQueueSnapshot(
            "redis",
            maxConcurrent,
            activeCount,
            waitingCount,
            availablePermits
        );
    }

    /**
     * 生命周期结束时关闭续租调度器，避免后台线程泄漏。
     */
    @PreDestroy
    public void destroy() {
        renewScheduler.shutdownNow();
    }

    /**
     * 进程内最小门控实现。
     * @param conversationId 会话标识。
     * @return 获取结果。
     */
    private QueueAcquireResult tryAcquireInMemory(Long conversationId, IntConsumer queuePositionConsumer) {
        if (activeConversations.containsKey(conversationId)) {
            return QueueAcquireResult.granted();
        }
        if (activeConversations.size() >= maxConcurrent) {
            if (queuePositionConsumer != null) {
                queuePositionConsumer.accept(1);
            }
            return QueueAcquireResult.rejected("busy");
        }
        activeConversations.put(conversationId, Boolean.TRUE);
        return QueueAcquireResult.granted();
    }

    /**
     * 基于 Redisson 可过期信号量 + 排队集合的门控实现。
     * @param conversationId 会话标识。
     * @return 获取结果。
     */
    private QueueAcquireResult tryAcquireWithRedisson(Long conversationId, IntConsumer queuePositionConsumer) {
        String requestMember = member(conversationId);
        RScoredSortedSet<String> queue = redissonClient.getScoredSortedSet(QUEUE_KEY);
        RPermitExpirableSemaphore semaphore = redissonClient.getPermitExpirableSemaphore(SEMAPHORE_NAME);
        semaphore.trySetPermits(maxConcurrent);

        if (permitByConversation.containsKey(conversationId)) {
            return QueueAcquireResult.granted();
        }
        queue.add(System.currentTimeMillis(), requestMember);
        long deadline = System.currentTimeMillis() + queueAcquireTimeoutMs;

        while (System.currentTimeMillis() < deadline) {
            Integer rank = queue.rank(requestMember);
            if (rank != null && queuePositionConsumer != null) {
                queuePositionConsumer.accept(rank + 1);
            }
            if (rank != null && rank < maxConcurrent) {
                String permitId = acquirePermit(semaphore);
                if (permitId != null) {
                    permitByConversation.put(conversationId, permitId);
                    queue.remove(requestMember);
                    publishQueueNotify();
                    return QueueAcquireResult.granted();
                }
            }
            waitForSignalOrTimeout(queuePollIntervalMs);
        }
        queue.remove(requestMember);
        return QueueAcquireResult.rejected("busy");
    }

    /**
     * 从 Redisson 信号量尝试获取可过期 permit。
     * @param semaphore 信号量实例。
     * @return permitId；获取失败返回 null。
     */
    private String acquirePermit(RPermitExpirableSemaphore semaphore) {
        try {
            return semaphore.tryAcquire(0, queueLeaseSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while acquiring queue permit", exception);
        } catch (RuntimeException exception) {
            log.warn("获取队列许可失败，将在下一轮重试", exception);
            return null;
        }
    }

    /**
     * 等待队列通知或超时轮询，通知失败时自动退化为超时重试。
     * @param millis 等待毫秒数。
     */
    private void waitForSignalOrTimeout(long millis) {
        try {
            // 通过轻量查询触发一次网络往返，确保连接可用；失败时退化为纯轮询。
            redissonClient.getTopic(NOTIFY_TOPIC).countSubscribers();
        } catch (RuntimeException ignored) {
            // 通知通道不可用时继续轮询退化，不阻断主流程。
        }
        sleepQuietly(millis);
    }

    /**
     * 主动发布队列通知，唤醒其他等待中的线程或节点。
     */
    private void publishQueueNotify() {
        try {
            redissonClient.getTopic(NOTIFY_TOPIC).publish("permit_released");
        } catch (RuntimeException exception) {
            log.warn("发布队列唤醒通知失败，等待线程将继续按轮询间隔重试", exception);
        }
    }

    /**
     * 在轮询等待窗口中短暂睡眠，避免忙等。
     * @param millis 睡眠毫秒数。
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for queue permit", exception);
        }
    }

    /**
     * 周期性刷新当前节点活跃会话租约，降低长会话租约过期概率。
     */
    private void scheduleLeaseRenewalTask() {
        renewScheduler.scheduleAtFixedRate(
            this::renewAllActiveLeases,
            queueLeaseRenewIntervalMs,
            queueLeaseRenewIntervalMs,
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * 扫描当前节点活跃会话并续租，续租失败将剔除本地 permit 记录。
     */
    private void renewAllActiveLeases() {
        if (!useRedisQueueGate || redissonClient == null || permitByConversation.isEmpty()) {
            return;
        }
        for (Long conversationId : permitByConversation.keySet()) {
            renew(conversationId);
        }
    }

    /**
     * 规范化续租间隔，确保小于租约并有最小安全间隔。
     * @param queueLeaseSeconds 租约秒数。
     * @param configuredIntervalMs 配置的续租间隔。
     * @return 归一化后的续租间隔毫秒数。
     */
    private static long normalizeRenewInterval(long queueLeaseSeconds, long configuredIntervalMs) {
        long leaseMillis = TimeUnit.SECONDS.toMillis(Math.max(1L, queueLeaseSeconds));
        long upperBound = Math.max(1000L, leaseMillis - 1000L);
        return Math.max(1000L, Math.min(configuredIntervalMs, upperBound));
    }

    /**
     * 按租约自动计算默认续租间隔，默认取租约三分之一并保证最小值。
     * @param queueLeaseSeconds 租约秒数。
     * @return 默认续租间隔毫秒数。
     */
    private static long defaultRenewInterval(long queueLeaseSeconds) {
        long leaseMillis = TimeUnit.SECONDS.toMillis(Math.max(1L, queueLeaseSeconds));
        return Math.max(1000L, leaseMillis / 3);
    }

    /**
     * 统一构造排队 member，避免直接暴露业务主键格式。
     * @param conversationId 会话标识。
     * @return 排队集合 member。
     */
    private String member(Long conversationId) {
        return Objects.toString(conversationId);
    }
}
