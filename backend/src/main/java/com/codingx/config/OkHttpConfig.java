package com.codingx.config;

import java.time.Duration;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OkHttp 客户端配置，供模型 provider 调用复用统一连接与读取超时。
 */
@Configuration
public class OkHttpConfig {

    /**
     * 创建模型调用 HTTP 客户端。
     * @param dynamicAiProperties AI 运行时配置视图，用于读取可动态调整的超时参数。
     * @return 配置好连接超时与读取超时的 OkHttpClient。
     */
    @Bean
    public OkHttpClient okHttpClient(DynamicAiProperties dynamicAiProperties) {
        // 步骤 1：连接超时控制 provider 建连等待时间，避免网络不可达时阻塞聊天主链路。
        // 步骤 2：读取超时控制模型流式响应窗口，具体数值可通过运行时配置调整。
        return new OkHttpClient.Builder()
            .connectTimeout(Duration.ofMillis(dynamicAiProperties.connectTimeoutMs()))
            .readTimeout(Duration.ofMillis(dynamicAiProperties.readTimeoutMs()))
            .build();
    }
}
