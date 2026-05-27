package com.codingx.config;

import cn.hutool.core.util.StrUtil;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 提供聊天门控所需的 RedissonClient，按 Spring Redis 配置自动拼装连接参数。
 */
@Configuration
@ConditionalOnProperty(prefix = "app.runtime", name = "use-redis-queue-gate", havingValue = "true")
public class RedissonConfig {

    /**
     * 基于 spring.data.redis 配置创建 Redisson 客户端。
     * @param redisProperties Redis 基础配置。
     * @return 可用于分布式信号量与队列控制的 RedissonClient。
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        return Redisson.create(createConfig(redisProperties));
    }

    /**
     * 创建 Redisson 原始配置，供 Bean 初始化和配置级单元测试复用。
     * @param redisProperties Redis 基础配置。
     * @return 已设置连接地址、数据库与字符串序列化的 Redisson 配置。
     */
    Config createConfig(RedisProperties redisProperties) {
        Config config = new Config();
        // 聊天队列门控只存储字符串 permit/member，使用文本 codec 便于在 Redis 工具中排查运行态。
        config.setCodec(StringCodec.INSTANCE);
        String address = "redis://" + redisProperties.getHost() + ":" + redisProperties.getPort();
        SingleServerConfig singleServerConfig = config.useSingleServer()
            .setAddress(address)
            .setDatabase(redisProperties.getDatabase())
            .setTimeout((int) redisProperties.getTimeout().toMillis());
        if (StrUtil.isNotBlank(redisProperties.getPassword())) {
            singleServerConfig.setPassword(redisProperties.getPassword());
        }
        return config;
    }
}
