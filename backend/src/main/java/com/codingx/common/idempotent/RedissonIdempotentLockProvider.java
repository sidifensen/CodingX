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

    private final RedissonClient redissonClient;

    @Override
    public IdempotentLock lock(String lockKey) {
        return new RedissonIdempotentLock(redissonClient.getLock(lockKey));
    }

    /**
     * Redisson 锁适配器。
     */
    private static final class RedissonIdempotentLock implements IdempotentLock {

        private final RLock delegate;

        private RedissonIdempotentLock(RLock delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean tryLock(long waitTimeMs, long leaseTimeMs) {
            try {
                return delegate.tryLock(waitTimeMs, leaseTimeMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ErrorMessageCatalog.IDEMPOTENT_LOCK_INTERRUPTED, exception);
            }
        }

        @Override
        public void unlock() {
            if (delegate.isHeldByCurrentThread()) {
                delegate.unlock();
            }
        }
    }
}
