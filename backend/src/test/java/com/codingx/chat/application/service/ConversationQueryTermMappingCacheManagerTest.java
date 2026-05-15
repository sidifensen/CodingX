package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.codingx.chat.domain.model.ChatQueryTermMapping;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 验证查询词映射缓存会在 TTL 内复用已加载数据。
 */
class ConversationQueryTermMappingCacheManagerTest {

    /**
     * 在缓存未过期时，应复用上次结果而不是重复调用加载器。
     */
    @Test
    void getMappingsReusesCachedValueBeforeExpiry() {
        ConversationQueryTermMappingCacheManager cacheManager = new ConversationQueryTermMappingCacheManager();
        AtomicInteger loadCount = new AtomicInteger();

        List<ChatQueryTermMapping> first = cacheManager.getMappings(() -> {
            loadCount.incrementAndGet();
            return List.of(ChatQueryTermMapping.builder().sourceTerm("oa").targetTerm("OA系统").build());
        });
        List<ChatQueryTermMapping> second = cacheManager.getMappings(() -> {
            loadCount.incrementAndGet();
            return List.of(ChatQueryTermMapping.builder().sourceTerm("vpn").targetTerm("VPN").build());
        });

        assertEquals(1, loadCount.get());
        assertEquals("oa", first.getFirst().getSourceTerm());
        assertEquals("oa", second.getFirst().getSourceTerm());
    }
}
