package com.codingx.common.idempotent;

import com.codingx.common.error.ErrorMessageCatalog;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * 基于 Redisson 的幂等锁实现，适用于多实例部署场景。
 */
@RequiredArgsConstructor
public class RedissonIdempotentLockProvider implements IdempotentLockProvider {

    /**
     * Redisson 客户端，用于创建跨 JVM 实例共享的分布式幂等锁。
     */
    private final RedissonClient redissonClient;

    @Override
    public IdempotentLock lock(String lockKey) {
        // 步骤 1：按幂等锁键获取 Redisson 锁，同一 key 在多实例之间共享互斥状态。
        return new RedissonIdempotentLock(redissonClient.getLock(lockKey));
    }

    /**
     * Redisson 锁适配器。
     */
    private static final class RedissonIdempotentLock implements IdempotentLock {

        /**
         * Redisson 分布式锁实例，负责实际的等待、租约与跨节点释放判断。
         */
        private final RLock delegate;

        /**
         * 创建 Redisson 锁适配器。
         * @param delegate 已按幂等 key 获取的 Redisson 锁，不允许为空。
         */
        private RedissonIdempotentLock(RLock delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean tryLock(long waitTimeMs, long leaseTimeMs) {
            try {
                // 步骤 1：按注解配置的等待时间和租约时间尝试加锁，租约用于兜底释放异常退出的请求。
                return delegate.tryLock(waitTimeMs, leaseTimeMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                // 步骤 2：恢复中断标记并抛出统一异常，避免调用方误判为普通重复提交。
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ErrorMessageCatalog.IDEMPOTENT_LOCK_INTERRUPTED, exception);
            }
        }

        @Override
        public void unlock() {
            if (delegate.isHeldByCurrentThread()) {
                // 只释放当前线程持有的锁，避免租约重入或超时后误解锁其他请求。
                delegate.unlock();
            }
        }
    }
}
