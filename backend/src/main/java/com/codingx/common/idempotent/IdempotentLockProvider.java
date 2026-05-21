package com.codingx.common.idempotent;

/**
 * 幂等锁提供器，统一抽象分布式锁与进程内锁实现。
 */
public interface IdempotentLockProvider {

    /**
     * 根据锁键获取可操作的幂等锁句柄。
     * @param lockKey 幂等锁键。
     * @return 幂等锁句柄。
     */
    IdempotentLock lock(String lockKey);

    /**
     * 幂等锁句柄。
     */
    interface IdempotentLock {

        /**
         * 尝试获取锁。
         * @param waitTimeMs 最大等待毫秒数。
         * @param leaseTimeMs 锁租约毫秒数。
         * @return 是否成功获取。
         */
        boolean tryLock(long waitTimeMs, long leaseTimeMs);

        /**
         * 释放锁。
         */
        void unlock();
    }
}
