package com.codingx.chat.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 验证联网搜索 provider 健康状态登记与三态熔断判断的关键约束。
 */
class SearchProviderHealthRegistryTest {

    /**
     * 连续失败达到阈值后应熔断，成功后应恢复为健康状态。
     */
    @Test
    void markFailureTripsCircuitAndSuccessResetsState() {
        SearchProviderHealthRegistry registry = new SearchProviderHealthRegistry(2, 30_000L);

        assertTrue(registry.allowCall("tavily"));
        registry.markFailure("tavily");
        assertTrue(registry.allowCall("tavily"));

        registry.markFailure("tavily");
        assertFalse(registry.allowCall("tavily"));

        registry.markSuccess("tavily");
        assertTrue(registry.allowCall("tavily"));
        assertEquals(0, registry.failureCount("tavily"));
    }

    /**
     * 熔断窗口结束后应进入半开状态，并且只允许一个探测请求通过。
     */
    @Test
    void allowCallMovesToHalfOpenAfterCooldownAndOnlyAllowsSingleProbe() {
        SearchProviderHealthRegistry registry = new SearchProviderHealthRegistry(1, 5L);

        assertTrue(registry.allowCall("serpapi"));
        registry.markFailure("serpapi");
        assertFalse(registry.allowCall("serpapi"));

        sleepSilently(15L);

        assertTrue(registry.allowCall("serpapi"));
        assertFalse(registry.allowCall("serpapi"));

        registry.markSuccess("serpapi");
        assertTrue(registry.allowCall("serpapi"));
    }

    /**
     * 半开探测失败后应重新回到熔断状态，而不是继续放行。
     */
    @Test
    void markFailureInHalfOpenReopensCircuit() {
        SearchProviderHealthRegistry registry = new SearchProviderHealthRegistry(1, 5L);

        assertTrue(registry.allowCall("exa"));
        registry.markFailure("exa");
        sleepSilently(15L);

        assertTrue(registry.allowCall("exa"));
        registry.markFailure("exa");

        assertFalse(registry.allowCall("exa"));
    }

    /**
     * 用短暂等待跨过测试用熔断窗口，避免把时间参数泄漏到业务实现里。
     * @param millis 等待毫秒数。
     */
    private void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for search provider cooldown", exception);
        }
    }
}
