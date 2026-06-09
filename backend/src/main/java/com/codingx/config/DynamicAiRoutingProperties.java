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

    /**
     * 运行时配置服务，用于从系统配置表读取模型路由熔断和首包等待参数。
     */
    private final RuntimeSettingService runtimeSettingService;

    /**
     * 获取连续失败阈值。
     * @return 连续失败阈值。
     */
    public int failureThreshold() {
        // 步骤 1：路由层只读取运行时配置值，默认值和类型兜底由 RuntimeSettingService 统一处理。
        return runtimeSettingService.aiFailureThreshold();
    }

    /**
     * 获取熔断打开时长毫秒。
     * @return 熔断打开时长毫秒。
     */
    public long openDurationMs() {
        // 步骤 1：熔断打开时长由系统配置动态控制，避免重启后端才能调整路由恢复窗口。
        return runtimeSettingService.aiOpenDurationMs();
    }

    /**
     * 获取首包等待超时毫秒。
     * @return 首包等待超时毫秒。
     */
    public long firstPacketTimeoutMs() {
        // 步骤 1：首包等待超时由系统配置动态控制，供模型调度层决定 fallback 窗口。
        return runtimeSettingService.aiFirstPacketTimeoutMs();
    }

    /**
     * 获取首包成功后的整流完成超时毫秒。
     * @return 首包后整流完成超时毫秒。
     */
    public long streamCompletionTimeoutMs() {
        // 步骤 1：整流完成超时由系统配置动态控制，避免慢流或异常长连接永久占用聊天线程。
        return runtimeSettingService.aiStreamCompletionTimeoutMs();
    }

}
