package com.codingx.chat.infrastructure.runtime;

import com.codingx.config.RuntimeProperties;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 提供聊天链路并发门控，支持进程内和 Redis 两种实现。
 */
@Component
@Slf4j
public class ConversationQueueGate {

    private static final String QUEUE_KEY = "chat:queue:waiting";
    private static final String ACTIVE_ZSET_KEY = "chat:queue:active";
    private static final String NOTIFY_CHANNEL = "chat:queue:notify";

    private static final DefaultRedisScript<String> CLAIM_SCRIPT = new DefaultRedisScript<>( """
        local member = ARGV[1]
        local nowMillis = tonumber(ARGV[2])
        local maxConcurrent = tonumber(ARGV[3])
        local leaseSeconds = tonumber(ARGV[4])
        local expireAt = nowMillis + leaseSeconds * 1000
        redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', nowMillis)
        if redis.call('ZSCORE', KEYS[2], member) ~= false then
          redis.call('ZADD', KEYS[2], expireAt, member)
          redis.call('ZREM', KEYS[1], member)
          return 'granted'
        end
        redis.call('ZADD', KEYS[1], 'NX', nowMillis, member)
        local rank = redis.call('ZRANK', KEYS[1], member)
        local activeCount = redis.call('ZCARD', KEYS[2])
        if rank ~= false and rank < maxConcurrent and activeCount < maxConcurrent then
          redis.call('ZREM', KEYS[1], member)
          redis.call('ZADD', KEYS[2], expireAt, member)
          return 'granted'
        end
        return 'wait'
        """, String.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>( """
        local member = ARGV[1]
        local removed = redis.call('ZREM', KEYS[1], member)
        redis.call('ZREM', KEYS[2], member)
        return removed
        """, Long.class);

    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>( """
        local member = ARGV[1]
        local expireAt = tonumber(ARGV[2])
        if redis.call('ZSCORE', KEYS[1], member) == false then
          return 0
        end
        redis.call('ZADD', KEYS[1], expireAt, member)
        return 1
        """, Long.class);

    private final boolean useRedisQueueGate;
    private final int maxConcurrent;
    private final long queueAcquireTimeoutMs;
    private final long queuePollIntervalMs;
    private final long queueLeaseSeconds;
    private final long queueLeaseRenewIntervalMs;
    private final StringRedisTemplate stringRedisTemplate;
    private final Map<Long, Boolean> activeConversations = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> redisActiveConversations = new ConcurrentHashMap<>();
    private final Object inMemoryMonitor = new Object();
    private final Object redisWaitMonitor = new Object();
    private final AtomicLong notifyVersion = new AtomicLong(0L);
    private final ScheduledExecutorService renewScheduler;

    /**
     * Spring 运行时构造器，按配置决定是否启用 Redis 队列门控。
     * @param runtimeProperties 运行时配置。
     * @param redisTemplateProvider Redis 模板提供者。
     */
    @Autowired
    public ConversationQueueGate(RuntimeProperties runtimeProperties, ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this(
            runtimeProperties.isUseRedisQueueGate(),
            runtimeProperties.getQueueMaxConcurrent(),
            runtimeProperties.getQueueAcquireTimeoutMs(),
            runtimeProperties.getQueuePollIntervalMs(),
            runtimeProperties.getQueueLeaseSeconds(),
            runtimeProperties.getQueueLeaseRenewIntervalMs(),
            redisTemplateProvider.getIfAvailable()
        );
    }

    /**
     * 测试构造器，默认使用进程内门控。
     * @param maxConcurrent 最大并发数。
     */
    public ConversationQueueGate(int maxConcurrent) {
        this(false, maxConcurrent, 3000L, 200L, 300L, 10000L, null);
    }

    /**
     * 显式构造器，便于测试和运行时复用。
     * @param useRedisQueueGate 是否启用 Redis 门控。
     * @param maxConcurrent 最大并发数。
     * @param queueAcquireTimeoutMs 获取超时时间。
     * @param queuePollIntervalMs 轮询间隔。
     * @param queueLeaseSeconds 租约秒数。
     * @param queueLeaseRenewIntervalMs 租约续期间隔毫秒数。
     * @param stringRedisTemplate Redis 模板。
     */
    public ConversationQueueGate(
        boolean useRedisQueueGate,
        int maxConcurrent,
        long queueAcquireTimeoutMs,
        long queuePollIntervalMs,
        long queueLeaseSeconds,
        long queueLeaseRenewIntervalMs,
        StringRedisTemplate stringRedisTemplate
    ) {
        this.useRedisQueueGate = useRedisQueueGate;
        this.maxConcurrent = Math.max(1, maxConcurrent);
        this.queueAcquireTimeoutMs = Math.max(200L, queueAcquireTimeoutMs);
        this.queuePollIntervalMs = Math.max(50L, queuePollIntervalMs);
        this.queueLeaseSeconds = Math.max(30L, queueLeaseSeconds);
        this.queueLeaseRenewIntervalMs = normalizeRenewInterval(queueLeaseSeconds, queueLeaseRenewIntervalMs);
        this.stringRedisTemplate = stringRedisTemplate;
        this.renewScheduler = createRenewScheduler();
        scheduleLeaseRenewalTask();
    }

    /**
     * 兼容旧构造签名，未显式传续租间隔时按租约时长自动推导。
     * @param useRedisQueueGate 是否启用 Redis 门控。
     * @param maxConcurrent 最大并发数。
     * @param queueAcquireTimeoutMs 获取超时时间。
     * @param queuePollIntervalMs 轮询间隔。
     * @param queueLeaseSeconds 租约秒数。
     * @param stringRedisTemplate Redis 模板。
     */
    public ConversationQueueGate(
        boolean useRedisQueueGate,
        int maxConcurrent,
        long queueAcquireTimeoutMs,
        long queuePollIntervalMs,
        long queueLeaseSeconds,
        StringRedisTemplate stringRedisTemplate
    ) {
        this(
            useRedisQueueGate,
            maxConcurrent,
            queueAcquireTimeoutMs,
            queuePollIntervalMs,
            queueLeaseSeconds,
            defaultRenewInterval(queueLeaseSeconds),
            stringRedisTemplate
        );
    }

    /**
     * 尝试为指定会话获取执行资格。
     * @param conversationId 会话标识。
     * @return 获取结果。
     */
    public QueueAcquireResult tryAcquire(Long conversationId) {
        if (!useRedisQueueGate || stringRedisTemplate == null) {
            synchronized (inMemoryMonitor) {
                return tryAcquireInMemory(conversationId);
            }
        }
        return tryAcquireWithRedis(conversationId);
    }

    /**
     * 释放指定会话占用的执行资格。
     * @param conversationId 会话标识。
     */
    public void release(Long conversationId) {
        if (!useRedisQueueGate || stringRedisTemplate == null) {
            synchronized (inMemoryMonitor) {
                activeConversations.remove(conversationId);
            }
            return;
        }
        redisActiveConversations.remove(conversationId);
        stringRedisTemplate.execute(
            RELEASE_SCRIPT,
            java.util.List.of(ACTIVE_ZSET_KEY, QUEUE_KEY),
            String.valueOf(conversationId)
        );
        publishQueueNotify();
    }

    /**
     * 续租 Redis 中的活动会话，防止长会话因租约超时被误释放。
     * @param conversationId 会话标识。
     * @return 续租是否成功。
     */
    public boolean renew(Long conversationId) {
        if (!useRedisQueueGate || stringRedisTemplate == null) {
            return true;
        }
        Long renewed = stringRedisTemplate.execute(
            RENEW_SCRIPT,
            java.util.List.of(ACTIVE_ZSET_KEY),
            String.valueOf(conversationId),
            String.valueOf(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(queueLeaseSeconds))
        );
        boolean success = renewed != null && renewed > 0;
        if (!success) {
            redisActiveConversations.remove(conversationId);
        }
        return success;
    }

    /**
     * 进程内最小门控实现。
     * @param conversationId 会话标识。
     * @return 获取结果。
     */
    private QueueAcquireResult tryAcquireInMemory(Long conversationId) {
        if (activeConversations.containsKey(conversationId)) {
            return QueueAcquireResult.granted();
        }
        if (activeConversations.size() >= maxConcurrent) {
            return QueueAcquireResult.rejected("busy");
        }
        activeConversations.put(conversationId, Boolean.TRUE);
        return QueueAcquireResult.granted();
    }

    /**
     * 基于 Redis ZSet + 计数器的门控实现。
     * Redis 轮询阶段不能持有 JVM 级锁，否则等待中的请求会反向阻塞 release。
     * @param conversationId 会话标识。
     * @return 获取结果。
     */
    private QueueAcquireResult tryAcquireWithRedis(Long conversationId) {
        String member = String.valueOf(conversationId);
        long deadline = System.currentTimeMillis() + queueAcquireTimeoutMs;
        while (System.currentTimeMillis() < deadline) {
            long nowMillis = System.currentTimeMillis();
            String result = stringRedisTemplate.execute(
                CLAIM_SCRIPT,
                java.util.List.of(QUEUE_KEY, ACTIVE_ZSET_KEY),
                member,
                String.valueOf(nowMillis),
                String.valueOf(maxConcurrent),
                String.valueOf(queueLeaseSeconds)
            );
            if ("granted".equals(result)) {
                redisActiveConversations.put(conversationId, Boolean.TRUE);
                return QueueAcquireResult.granted();
            }
            waitForQueueNotify(queuePollIntervalMs);
        }
        stringRedisTemplate.opsForZSet().remove(QUEUE_KEY, member);
        return QueueAcquireResult.rejected("busy");
    }

    /**
     * 在轮询等待窗口中短暂睡眠，避免忙等。
     * @param millis 睡眠毫秒数。
     */
    private void waitForQueueNotify(long millis) {
        long beforeVersion = notifyVersion.get();
        synchronized (redisWaitMonitor) {
            if (notifyVersion.get() != beforeVersion) {
                return;
            }
            sleepQuietly(millis);
        }
    }

    /**
     * 使用对象监视器实现可中断等待，兼容超时轮询与显式唤醒。
     * @param millis 等待毫秒数。
     */
    private void sleepQuietly(long millis) {
        try {
            redisWaitMonitor.wait(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for queue permit", exception);
        }
    }

    /**
     * 发布排队唤醒信号，优先唤醒同节点等待线程并广播到 Redis 频道。
     */
    private void publishQueueNotify() {
        notifyVersion.incrementAndGet();
        synchronized (redisWaitMonitor) {
            redisWaitMonitor.notifyAll();
        }
        try {
            stringRedisTemplate.convertAndSend(NOTIFY_CHANNEL, "permit_released");
        } catch (RuntimeException exception) {
            log.warn("发布队列唤醒通知失败，等待线程将继续按轮询间隔重试", exception);
        }
    }

    /**
     * 初始化续租调度器，线程使用 daemon 模式避免影响进程退出。
     * @return 续租调度器。
     */
    private ScheduledExecutorService createRenewScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "chat-queue-lease-renew");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 启动续租任务，周期性刷新 Redis 活跃会话租约。
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
     * 扫描当前节点仍活跃的会话并续租，续租失败则移除本地跟踪状态。
     */
    private void renewAllActiveLeases() {
        if (!useRedisQueueGate || stringRedisTemplate == null || redisActiveConversations.isEmpty()) {
            return;
        }
        for (Long conversationId : redisActiveConversations.keySet()) {
            try {
                renew(conversationId);
            } catch (RuntimeException exception) {
                log.warn("续租会话执行资格失败，conversationId={}", conversationId, exception);
            }
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
     * 生命周期结束时关闭续租调度器，避免后台线程泄漏。
     */
    @PreDestroy
    public void destroy() {
        renewScheduler.shutdownNow();
    }
}
