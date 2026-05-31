package com.codingx.common.idempotent;

import com.codingx.common.error.ErrorMessageCatalog;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 进程内幂等锁实现，作为无 Redisson 环境下的降级方案。
 */
public class InMemoryIdempotentLockProvider implements IdempotentLockProvider {

    /**
     * 进程内锁缓存，key 为幂等锁键，value 为对应的 JVM 本地可重入锁。
     */
    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    @Override
    public IdempotentLock lock(String lockKey) {
        // 步骤 1：同一个幂等锁键复用同一把本地锁，保证单进程内重复提交互斥。
        ReentrantLock lock = lockMap.computeIfAbsent(lockKey, key -> new ReentrantLock());
        // 步骤 2：返回统一接口适配器，切面无需感知底层是否使用本地锁。
        return new InMemoryIdempotentLock(lock);
    }

    /**
     * 进程内锁适配器。
     */
    private static final class InMemoryIdempotentLock implements IdempotentLock {

        /**
         * JVM 本地可重入锁实例，用于承载无 Redis 环境下的互斥能力。
         */
        private final ReentrantLock delegate;

        /**
         * 创建本地锁适配器。
         * @param delegate 已按幂等 key 缓存的可重入锁，不允许为空。
         */
        private InMemoryIdempotentLock(ReentrantLock delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean tryLock(long waitTimeMs, long leaseTimeMs) {
            if (waitTimeMs <= 0) {
                // 步骤 1：不等待时立即尝试获取锁，适合重复提交直接失败的接口。
                return delegate.tryLock();
            }
            try {
                // 步骤 2：允许等待时按毫秒超时阻塞，leaseTimeMs 在本地锁实现中不生效。
                return delegate.tryLock(waitTimeMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                // 步骤 3：恢复中断标记并抛出统一异常，避免吞掉线程池调度信号。
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ErrorMessageCatalog.IDEMPOTENT_LOCK_INTERRUPTED, exception);
            }
        }

        @Override
        public void unlock() {
            if (delegate.isHeldByCurrentThread()) {
                // 只允许锁持有线程释放，避免误释放其他请求正在持有的锁。
                delegate.unlock();
            }
        }
    }
}
