package com.codingx.support.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 验证模型健康状态登记与三态熔断判断的关键约束。
 */
class AiProviderHealthRegistryTest {

    /**
     * 连续失败达到阈值后应熔断，成功后应恢复为健康状态。
     */
    @Test
    void markFailureTripsCircuitAndSuccessResetsState() {
        AiProviderHealthRegistry registry = new AiProviderHealthRegistry(2, 30_000L);

        assertTrue(registry.allowCall("primary"));
        registry.markFailure("primary");
        assertTrue(registry.allowCall("primary"));

        registry.markFailure("primary");
        assertFalse(registry.allowCall("primary"));

        registry.markSuccess("primary");
        assertTrue(registry.allowCall("primary"));
        assertEquals(0, registry.failureCount("primary"));
    }

    /**
     * 熔断窗口结束后应进入半开状态，并且只允许一个探测请求通过。
     */
    @Test
    void allowCallMovesToHalfOpenAfterCooldownAndOnlyAllowsSingleProbe() {
        AiProviderHealthRegistry registry = new AiProviderHealthRegistry(1, 5L);

        assertTrue(registry.allowCall("primary"));
        registry.markFailure("primary");
        assertFalse(registry.allowCall("primary"));

        sleepSilently(15L);

        assertTrue(registry.allowCall("primary"));
        assertFalse(registry.allowCall("primary"));

        registry.markSuccess("primary");
        assertTrue(registry.allowCall("primary"));
    }

    /**
     * 半开探测失败后应重新回到熔断状态，而不是继续放行。
     */
    @Test
    void markFailureInHalfOpenReopensCircuit() {
        AiProviderHealthRegistry registry = new AiProviderHealthRegistry(1, 5L);

        assertTrue(registry.allowCall("primary"));
        registry.markFailure("primary");
        sleepSilently(15L);

        assertTrue(registry.allowCall("primary"));
        registry.markFailure("primary");

        assertFalse(registry.allowCall("primary"));
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
            throw new IllegalStateException("Interrupted while waiting for circuit breaker cooldown", exception);
        }
    }
}
