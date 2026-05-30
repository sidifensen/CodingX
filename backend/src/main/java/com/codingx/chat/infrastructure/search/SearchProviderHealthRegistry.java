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

    private final int failureThreshold;
    private final long openDurationMs;
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
        if (provider == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        AtomicBoolean allowed = new AtomicBoolean(false);
        healthStates.compute(provider, (ignored, current) -> {
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
