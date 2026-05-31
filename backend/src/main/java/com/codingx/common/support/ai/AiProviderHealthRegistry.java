package com.codingx.common.support.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 维护模型候选的三态熔断状态，确保故障模型不会持续拖垮主链路。
 */
public class AiProviderHealthRegistry {

    /**
     * 连续失败阈值，达到该次数后模型进入 OPEN 熔断状态。
     */
    private final int failureThreshold;

    /**
     * OPEN 状态保持时长，超过后允许一次 HALF_OPEN 探测请求。
     */
    private final long openDurationMs;

    /**
     * 模型健康状态表，key 为模型候选 ID，value 为对应熔断状态。
     */
    private final Map<String, HealthState> healthStates = new ConcurrentHashMap<>();

    /**
     * 使用默认熔断窗口构造健康注册表。
     * @param failureThreshold 连续失败阈值。
     */
    public AiProviderHealthRegistry(int failureThreshold) {
        this(failureThreshold, 30_000L);
    }

    /**
     * 使用自定义熔断窗口构造健康注册表。
     * @param failureThreshold 连续失败阈值。
     * @param openDurationMs 熔断持续时长。
     */
    public AiProviderHealthRegistry(int failureThreshold, long openDurationMs) {
        // 步骤 1：阈值和时间窗口至少为 1，避免错误配置导致熔断永远不开启或立即抖动。
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDurationMs = Math.max(1L, openDurationMs);
    }

    /**
     * 判断当前模型是否允许调用，并推进 OPEN/HALF_OPEN 状态机。
     * @param modelId 模型标识。
     * @return 是否允许当前调用。
     */
    public boolean allowCall(String modelId) {
        if (modelId == null) {
            // 模型 ID 缺失时无法记录健康状态，直接拒绝调用。
            return false;
        }
        long now = System.currentTimeMillis();
        AtomicBoolean allowed = new AtomicBoolean(false);
        healthStates.compute(modelId, (ignored, current) -> {
            HealthState state = current == null ? new HealthState() : current;
            if (state.status == Status.OPEN) {
                if (state.openUntil > now) {
                    // 步骤 1：OPEN 窗口未结束时继续拒绝调用，避免故障模型拖慢主链路。
                    return state;
                }
                // 步骤 2：OPEN 窗口结束后进入 HALF_OPEN，只放行一个探测请求。
                state.status = Status.HALF_OPEN;
                state.halfOpenInFlight = true;
                allowed.set(true);
                return state;
            }
            if (state.status == Status.HALF_OPEN) {
                if (state.halfOpenInFlight) {
                    // HALF_OPEN 已有探测请求在路上时拒绝并发探测，避免雪崩式恢复。
                    return state;
                }
                // 步骤 3：没有探测请求时允许一次调用，并标记 in-flight。
                state.halfOpenInFlight = true;
                allowed.set(true);
                return state;
            }
            // 步骤 4：CLOSED 状态正常放行，由 markSuccess/markFailure 后续更新健康状态。
            allowed.set(true);
            return state;
        });
        return allowed.get();
    }

    /**
     * 标记一次成功，恢复到 CLOSED 状态并清理失败计数。
     * @param modelId 模型标识。
     */
    public void markSuccess(String modelId) {
        if (modelId == null) {
            // 缺少模型 ID 时无法定位状态，直接忽略。
            return;
        }
        healthStates.compute(modelId, (ignored, current) -> {
            HealthState state = current == null ? new HealthState() : current;
            // 步骤 1：任意成功都视为模型恢复正常，清空失败计数和半开探测标记。
            state.consecutiveFailures = 0;
            state.openUntil = 0L;
            state.halfOpenInFlight = false;
            state.status = Status.CLOSED;
            return state;
        });
    }

    /**
     * 标记一次失败，并根据当前状态推进熔断机。
     * @param modelId 模型标识。
     */
    public void markFailure(String modelId) {
        if (modelId == null) {
            // 缺少模型 ID 时无法定位状态，直接忽略，避免空 key 污染健康表。
            return;
        }
        long now = System.currentTimeMillis();
        healthStates.compute(modelId, (ignored, current) -> {
            HealthState state = current == null ? new HealthState() : current;
            if (state.status == Status.HALF_OPEN) {
                // 步骤 1：HALF_OPEN 探测失败说明模型仍不可用，重新进入 OPEN 窗口。
                state.status = Status.OPEN;
                state.openUntil = now + openDurationMs;
                state.halfOpenInFlight = false;
                state.consecutiveFailures = 0;
                return state;
            }
            // 步骤 2：CLOSED 或 OPEN 结束后的失败累计连续失败次数。
            state.consecutiveFailures++;
            if (state.consecutiveFailures >= failureThreshold) {
                // 步骤 3：达到阈值后打开熔断窗口，并重置计数等待下一轮恢复探测。
                state.status = Status.OPEN;
                state.openUntil = now + openDurationMs;
                state.consecutiveFailures = 0;
                state.halfOpenInFlight = false;
            }
            return state;
        });
    }

    /**
     * 返回当前累计失败次数，供测试断言使用。
     * @param modelId 模型标识。
     * @return 连续失败次数。
     */
    public int failureCount(String modelId) {
        // 步骤 1：测试或诊断读取失败计数时不创建新状态，避免观察操作改变路由行为。
        HealthState state = healthStates.get(modelId);
        return state == null ? 0 : state.consecutiveFailures;
    }

    /**
     * 内部健康状态对象。
     */
    private static final class HealthState {
        /**
         * 当前连续失败次数，仅 CLOSED 累计；进入 OPEN 后会重置。
         */
        private int consecutiveFailures;

        /**
         * OPEN 熔断状态结束时间戳，单位毫秒。
         */
        private long openUntil;

        /**
         * HALF_OPEN 状态下是否已有探测请求正在执行。
         */
        private boolean halfOpenInFlight;

        /**
         * 当前熔断状态，默认 CLOSED 表示正常放行。
         */
        private Status status = Status.CLOSED;
    }

    /**
     * 三态熔断状态。
     */
    private enum Status {
        CLOSED,
        OPEN,
        HALF_OPEN
    }
}

