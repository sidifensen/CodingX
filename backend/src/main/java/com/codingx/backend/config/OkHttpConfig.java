package com.codingx.backend.config;
import java.time.Duration;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Spring beans and infrastructure required by OkHttpConfig.
 */
@Configuration
public class OkHttpConfig {

    /**
     * Executes the logic defined by okHttpClient.
     * @param aiProperties input argument.
     * @return processing result.
     */
    @Bean
    public OkHttpClient okHttpClient(AiProperties aiProperties) {
        return new OkHttpClient.Builder()
            .connectTimeout(Duration.ofMillis(aiProperties.getConnectTimeoutMs()))
            .readTimeout(Duration.ofMillis(aiProperties.getReadTimeoutMs()))
            .build();
    }
}
