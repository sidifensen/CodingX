package com.codingx.support.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 验证 provider 健康状态登记与熔断判断的最小约束。
 */
class AiProviderHealthRegistryTest {

    /**
     * 连续失败达到阈值后应熔断，成功后应恢复为健康状态。
     */
    @Test
    void markFailureTripsCircuitAndSuccessResetsState() {
        AiProviderHealthRegistry registry = new AiProviderHealthRegistry(2);

        registry.markFailure("primary");
        assertTrue(registry.isAvailable("primary"));

        registry.markFailure("primary");
        assertFalse(registry.isAvailable("primary"));

        registry.markSuccess("primary");
        assertTrue(registry.isAvailable("primary"));
        assertEquals(0, registry.failureCount("primary"));
    }
}
