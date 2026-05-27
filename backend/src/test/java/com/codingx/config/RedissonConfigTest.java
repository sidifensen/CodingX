package com.codingx.config;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

/**
 * 验证 Redisson 客户端配置，确保聊天队列门控写入 Redis 的字符串成员保持可读。
 */
class RedissonConfigTest {

    /**
     * 聊天队列门控只写入字符串 permit 与 member，必须使用文本 codec 避免运维工具显示二进制乱码。
     */
    @Test
    void redissonClientShouldUseStringCodecForReadableQueueGateKeys() {
        RedisProperties redisProperties = new RedisProperties();
        redisProperties.setHost("localhost");
        redisProperties.setPort(6379);
        redisProperties.setDatabase(0);
        redisProperties.setTimeout(Duration.ofSeconds(10));
        Config config = new RedissonConfig().createConfig(redisProperties);

        assertSame(StringCodec.INSTANCE, config.getCodec(), "Redisson 应使用 StringCodec 写入可读字符串");
    }
}
