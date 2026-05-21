package com.codingx.chat.application.service;

import com.codingx.chat.domain.model.ChatQueryTermMapping;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 为查询词映射规则提供轻量级内存缓存，避免每次改写都直接查询数据库。
 */
@Component
public class ConversationQueryTermMappingCacheManager {

    private static final long CACHE_TTL_MILLIS = 60_000L;

    private final AtomicReference<List<ChatQueryTermMapping>> cachedMappings = new AtomicReference<>(List.of());
    private volatile Instant expiresAt = Instant.EPOCH;

    /**
     * 读取缓存中的映射规则；缓存过期时调用加载器刷新。
     * @param loader 数据加载器。
     * @return 当前有效映射规则。
     */
    public List<ChatQueryTermMapping> getMappings(Supplier<List<ChatQueryTermMapping>> loader) {
        Instant now = Instant.now();
        if (now.isBefore(expiresAt) && !cachedMappings.get().isEmpty()) {
            return cachedMappings.get();
        }
        List<ChatQueryTermMapping> loaded = loader.get();
        cachedMappings.set(loaded == null ? List.of() : loaded);
        expiresAt = now.plusMillis(CACHE_TTL_MILLIS);
        return cachedMappings.get();
    }

    /**
     * 主动清空缓存，供后续后台更新映射规则后刷新。
     */
    public void clear() {
        cachedMappings.set(List.of());
        expiresAt = Instant.EPOCH;
    }
}
