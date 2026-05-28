package com.codingx.config;

import com.codingx.chat.application.service.RuntimeSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 提供模型路由相关的动态运行时配置读取能力，统一从数据库覆盖值与默认配置中取值。
 */
@Component
@RequiredArgsConstructor
public class DynamicAiRoutingProperties {

    private final RuntimeSettingService runtimeSettingService;

    /**
     * 获取连续失败阈值。
     * @return 连续失败阈值。
     */
    public int failureThreshold() {
        return runtimeSettingService.aiFailureThreshold();
    }

    /**
     * 获取熔断打开时长毫秒。
     * @return 熔断打开时长毫秒。
     */
    public long openDurationMs() {
        return runtimeSettingService.aiOpenDurationMs();
    }

    /**
     * 获取首包等待超时毫秒。
     * @return 首包等待超时毫秒。
     */
    public long firstPacketTimeoutMs() {
        return runtimeSettingService.aiFirstPacketTimeoutMs();
    }

}

