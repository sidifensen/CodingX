package com.codingx.backend.config;

import java.time.Duration;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OkHttpConfig {

    @Bean
    public OkHttpClient okHttpClient(AiProperties aiProperties) {
        return new OkHttpClient.Builder()
            .connectTimeout(Duration.ofMillis(aiProperties.getConnectTimeoutMs()))
            .readTimeout(Duration.ofMillis(aiProperties.getReadTimeoutMs()))
            .build();
    }
}