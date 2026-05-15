package com.codingx.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一管理聊天运行时使用的线程池，避免公共池混用。
 */
@Configuration
public class ChatExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService chatStreamExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService searchExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
