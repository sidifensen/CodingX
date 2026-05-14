package com.codingx.chat.infrastructure.runtime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis 的聊天运行态存储实现，为跨节点取消和限流提供状态基础。
 */
@Component
@Qualifier("redisChatRuntimeStateStore")
@ConditionalOnProperty(prefix = "app.runtime", name = "use-redis-state-store", havingValue = "true")
public class RedisChatRuntimeStateStore implements ChatRuntimeStateStore {

    private static final String ACTIVE_PREFIX = "chat:active:";
    private static final String CANCELLED_PREFIX = "chat:cancelled:";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 注入 Redis 模板以维护聊天运行态。
     * @param stringRedisTemplate Redis 模板。
     */
    public RedisChatRuntimeStateStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void markActive(Long conversationId) {
        stringRedisTemplate.opsForValue().set(activeKey(conversationId), "1");
        stringRedisTemplate.delete(cancelledKey(conversationId));
    }

    @Override
    public void markCancelled(Long conversationId) {
        stringRedisTemplate.delete(activeKey(conversationId));
        stringRedisTemplate.opsForValue().set(cancelledKey(conversationId), "1");
    }

    @Override
    public boolean isActive(Long conversationId) {
        return "1".equals(stringRedisTemplate.opsForValue().get(activeKey(conversationId)));
    }

    @Override
    public boolean isCancelled(Long conversationId) {
        return "1".equals(stringRedisTemplate.opsForValue().get(cancelledKey(conversationId)));
    }

    @Override
    public void clear(Long conversationId) {
        stringRedisTemplate.delete(activeKey(conversationId));
        stringRedisTemplate.delete(cancelledKey(conversationId));
    }

    private String activeKey(Long conversationId) {
        return ACTIVE_PREFIX + conversationId;
    }

    private String cancelledKey(Long conversationId) {
        return CANCELLED_PREFIX + conversationId;
    }
}
