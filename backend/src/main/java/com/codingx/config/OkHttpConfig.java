package com.codingx.config;
import java.time.Duration;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 负责配置 OkHttpConfig 所需的 Spring Bean 与基础设施。
 */
@Configuration
public class OkHttpConfig {

    /**
     * 执行 okHttpClient 定义的处理逻辑。
     * @param dynamicAiProperties 输入参数。
     * @return 输入参数。
     */
    @Bean
    public OkHttpClient okHttpClient(DynamicAiProperties dynamicAiProperties) {
        return new OkHttpClient.Builder()
            .connectTimeout(Duration.ofMillis(dynamicAiProperties.connectTimeoutMs()))
            .readTimeout(Duration.ofMillis(dynamicAiProperties.readTimeoutMs()))
            .build();
    }
}
