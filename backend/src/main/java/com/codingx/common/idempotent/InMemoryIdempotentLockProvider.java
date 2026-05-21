package com.codingx.common.idempotent;

import com.codingx.common.error.ErrorMessageCatalog;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 进程内幂等锁实现，作为无 Redisson 环境下的降级方案。
 */
public class InMemoryIdempotentLockProvider implements IdempotentLockProvider {

    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    @Override
    public IdempotentLock lock(String lockKey) {
        ReentrantLock lock = lockMap.computeIfAbsent(lockKey, key -> new ReentrantLock());
        return new InMemoryIdempotentLock(lock);
    }

    /**
     * 进程内锁适配器。
     */
    private static final class InMemoryIdempotentLock implements IdempotentLock {

        private final ReentrantLock delegate;

        private InMemoryIdempotentLock(ReentrantLock delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean tryLock(long waitTimeMs, long leaseTimeMs) {
            if (waitTimeMs <= 0) {
                return delegate.tryLock();
            }
            try {
                return delegate.tryLock(waitTimeMs, TimeUnit.MILLISECONDS);
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
