package com.codingx.common.idempotent;

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 幂等锁提供器配置，优先使用 Redisson 分布式锁，无 Redisson 时自动回退进程内锁。
 */
@Configuration
public class IdempotentLockProviderConfig {

    /**
     * 统一创建幂等锁提供器，避免组件条件装配顺序导致实现丢失。
     * @param redissonClientProvider Redisson 客户端提供器，未启用时返回空。
     * @return 幂等锁提供器实现。
     */
    @Bean
    public IdempotentLockProvider idempotentLockProvider(ObjectProvider<RedissonClient> redissonClientProvider) {
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redissonClient != null) {
            return new RedissonIdempotentLockProvider(redissonClient);
        }
        return new InMemoryIdempotentLockProvider();
    }
}
