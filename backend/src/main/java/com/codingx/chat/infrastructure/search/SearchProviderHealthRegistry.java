package com.codingx.chat.infrastructure.search;

import com.codingx.chat.application.service.RuntimeSettingService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 维护联网搜索 provider 的三态熔断状态，避免故障搜索源持续拖慢用户请求。
 */
@Component
public class SearchProviderHealthRegistry {

    /** 连续失败阈值，达到后 provider 进入 OPEN 熔断状态。 */
    private final int failureThreshold;
    /** 熔断打开持续时长，到期后允许一次 HALF_OPEN 探测。 */
    private final long openDurationMs;
    /** provider 到健康状态的并发映射，用于跨请求共享熔断窗口。 */
    private final Map<String, HealthState> healthStates = new ConcurrentHashMap<>();

    /**
     * 使用系统配置表中的搜索熔断参数构造运行时健康注册表。
     * @param runtimeSettingService 运行时配置读取服务。
     */
    @Autowired
    public SearchProviderHealthRegistry(RuntimeSettingService runtimeSettingService) {
        this(runtimeSettingService.webSearchFailureThreshold(), runtimeSettingService.webSearchOpenDurationMs());
    }

    /**
     * 使用默认熔断窗口构造搜索 provider 健康注册表。
     * @param failureThreshold 连续失败阈值。
     */
    public SearchProviderHealthRegistry(int failureThreshold) {
        this(failureThreshold, 30_000L);
    }

    /**
     * 使用自定义熔断窗口构造搜索 provider 健康注册表。
     * @param failureThreshold 连续失败阈值。
     * @param openDurationMs 熔断持续时长。
     */
    public SearchProviderHealthRegistry(int failureThreshold, long openDurationMs) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDurationMs = Math.max(1L, openDurationMs);
    }

    /**
     * 判断当前 provider 是否允许调用，并推进 OPEN/HALF_OPEN 状态机。
     * @param provider provider 编码。
     * @return 是否允许当前调用。
     */
    public boolean allowCall(String provider) {
        // 步骤 1：空 provider 无法维护健康状态，直接拒绝调用。
        if (provider == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        AtomicBoolean allowed = new AtomicBoolean(false);
        healthStates.compute(provider, (ignored, current) -> {
            HealthState state = current == null ? new HealthState() : current;
            // 步骤 2：OPEN 状态未到期时继续拒绝；到期后转 HALF_OPEN 并允许一次探测。
            if (state.status == Status.OPEN) {
                if (state.openUntil > now) {
                    return state;
                }
                state.status = Status.HALF_OPEN;
                state.halfOpenInFlight = true;
                allowed.set(true);
                return state;
            }
            // 步骤 3：HALF_OPEN 同一时间只允许一个探测请求，避免故障源被并发打满。
            if (state.status == Status.HALF_OPEN) {
                if (state.halfOpenInFlight) {
                    return state;
                }
                state.halfOpenInFlight = true;
                allowed.set(true);
                return state;
            }
            // 步骤 4：CLOSED 状态正常放行，由调用方根据执行结果回写成功或失败。
            allowed.set(true);
            return state;
        });
        return allowed.get();
    }

    /**
     * 标记一次成功，恢复到 CLOSED 状态并清理失败计数。
     * @param provider provider 编码。
     */
    public void markSuccess(String provider) {
        if (provider == null) {
            return;
        }
        healthStates.compute(provider, (ignored, current) -> {
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
     * @param provider provider 编码。
     */
    public void markFailure(String provider) {
        if (provider == null) {
            return;
        }
        long now = System.currentTimeMillis();
        healthStates.compute(provider, (ignored, current) -> {
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
     * @param provider provider 编码。
     * @return 连续失败次数。
     */
    public int failureCount(String provider) {
        HealthState state = healthStates.get(provider);
        return state == null ? 0 : state.consecutiveFailures;
    }

    /**
     * 内部健康状态对象。
     */
    private static final class HealthState {
        /** 连续失败次数，达到阈值后进入 OPEN 状态并清零。 */
        private int consecutiveFailures;
        /** OPEN 状态截止时间戳，当前时间超过该值后允许半开探测。 */
        private long openUntil;
        /** 半开探测占用标记，避免同一 provider 同时放行多个试探请求。 */
        private boolean halfOpenInFlight;
        /** 当前熔断状态，默认 CLOSED 表示 provider 可正常调用。 */
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
