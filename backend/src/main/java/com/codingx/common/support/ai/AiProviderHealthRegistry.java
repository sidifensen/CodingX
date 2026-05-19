package com.codingx.common.support.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 维护模型候选的三态熔断状态，确保故障模型不会持续拖垮主链路。
 */
public class AiProviderHealthRegistry {

    private final int failureThreshold;
    private final long openDurationMs;
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
            return false;
        }
        long now = System.currentTimeMillis();
        AtomicBoolean allowed = new AtomicBoolean(false);
        healthStates.compute(modelId, (ignored, current) -> {
            HealthState state = current == null ? new HealthState() : current;
            if (state.status == Status.OPEN) {
                if (state.openUntil > now) {
                    return state;
                }
                state.status = Status.HALF_OPEN;
                state.halfOpenInFlight = true;
                allowed.set(true);
                return state;
            }
            if (state.status == Status.HALF_OPEN) {
                if (state.halfOpenInFlight) {
                    return state;
                }
                state.halfOpenInFlight = true;
                allowed.set(true);
                return state;
            }
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
            return;
        }
        healthStates.compute(modelId, (ignored, current) -> {
            HealthState state = current == null ? new HealthState() : current;
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
            return;
        }
        long now = System.currentTimeMillis();
        healthStates.compute(modelId, (ignored, current) -> {
            HealthState state = current == null ? new HealthState() : current;
            if (state.status == Status.HALF_OPEN) {
                state.status = Status.OPEN;
                state.openUntil = now + openDurationMs;
                state.halfOpenInFlight = false;
                state.consecutiveFailures = 0;
                return state;
            }
            state.consecutiveFailures++;
            if (state.consecutiveFailures >= failureThreshold) {
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
        HealthState state = healthStates.get(modelId);
        return state == null ? 0 : state.consecutiveFailures;
    }

    /**
     * 内部健康状态对象。
     */
    private static final class HealthState {
        private int consecutiveFailures;
        private long openUntil;
        private boolean halfOpenInFlight;
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

