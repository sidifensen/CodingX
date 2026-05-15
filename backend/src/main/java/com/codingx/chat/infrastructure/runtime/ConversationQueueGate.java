package com.codingx.chat.infrastructure.runtime;

import com.codingx.config.RuntimeProperties;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 提供聊天链路并发门控，支持进程内和 Redis 两种实现。
 */
@Component
public class ConversationQueueGate {

    private static final String QUEUE_KEY = "chat:queue:waiting";
    private static final String ACTIVE_COUNT_KEY = "chat:queue:active-count";

    private static final DefaultRedisScript<String> CLAIM_SCRIPT = new DefaultRedisScript<>( """
        local member = ARGV[1]
        local score = tonumber(ARGV[2])
        local maxConcurrent = tonumber(ARGV[3])
        local leaseSeconds = tonumber(ARGV[4])
        if redis.call('EXISTS', KEYS[3]) == 1 then
          return 'granted'
        end
        redis.call('ZADD', KEYS[1], 'NX', score, member)
        local rank = redis.call('ZRANK', KEYS[1], member)
        local active = tonumber(redis.call('GET', KEYS[2]) or '0')
        if rank ~= false and rank < maxConcurrent and active < maxConcurrent then
          redis.call('ZREM', KEYS[1], member)
          redis.call('SET', KEYS[3], '1', 'EX', leaseSeconds)
          redis.call('INCR', KEYS[2])
          redis.call('EXPIRE', KEYS[2], leaseSeconds)
          return 'granted'
        end
        return 'wait'
        """, String.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>( """
        if redis.call('DEL', KEYS[1]) == 1 then
          local active = tonumber(redis.call('GET', KEYS[2]) or '0')
          if active > 0 then
            redis.call('DECR', KEYS[2])
          end
          return 1
        end
        return 0
        """, Long.class);

    private final boolean useRedisQueueGate;
    private final int maxConcurrent;
    private final long queueAcquireTimeoutMs;
    private final long queuePollIntervalMs;
    private final long queueLeaseSeconds;
    private final StringRedisTemplate stringRedisTemplate;
    private final Map<Long, Boolean> activeConversations = new ConcurrentHashMap<>();
    private final Object inMemoryMonitor = new Object();

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
            redisTemplateProvider.getIfAvailable()
        );
    }

    /**
     * 测试构造器，默认使用进程内门控。
     * @param maxConcurrent 最大并发数。
     */
    public ConversationQueueGate(int maxConcurrent) {
        this(false, maxConcurrent, 3000L, 200L, 300L, null);
    }

    /**
     * 显式构造器，便于测试和运行时复用。
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
        this.useRedisQueueGate = useRedisQueueGate;
        this.maxConcurrent = Math.max(1, maxConcurrent);
        this.queueAcquireTimeoutMs = Math.max(200L, queueAcquireTimeoutMs);
        this.queuePollIntervalMs = Math.max(50L, queuePollIntervalMs);
        this.queueLeaseSeconds = Math.max(30L, queueLeaseSeconds);
        this.stringRedisTemplate = stringRedisTemplate;
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
        stringRedisTemplate.execute(
            RELEASE_SCRIPT,
            java.util.List.of(activeConversationKey(conversationId), ACTIVE_COUNT_KEY)
        );
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
            String result = stringRedisTemplate.execute(
                CLAIM_SCRIPT,
                java.util.List.of(QUEUE_KEY, ACTIVE_COUNT_KEY, activeConversationKey(conversationId)),
                member,
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(maxConcurrent),
                String.valueOf(queueLeaseSeconds)
            );
            if ("granted".equals(result)) {
                return QueueAcquireResult.granted();
            }
            sleepQuietly(queuePollIntervalMs);
        }
        stringRedisTemplate.opsForZSet().remove(QUEUE_KEY, member);
        return QueueAcquireResult.rejected("busy");
    }

    /**
     * 构造单会话激活标识。
     * @param conversationId 会话标识。
     * @return Redis key。
     */
    private String activeConversationKey(Long conversationId) {
        return "chat:queue:active:" + conversationId;
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
}
