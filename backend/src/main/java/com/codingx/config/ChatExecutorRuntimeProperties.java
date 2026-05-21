package com.codingx.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 聊天线程池运行时配置。
 */
@Data
@ConfigurationProperties(prefix = "app.chat.executor")
public class ChatExecutorRuntimeProperties {

    /**
     * 聊天入口线程池核心线程数。
     */
    private int streamCorePoolSize = 2;

    /**
     * 聊天入口线程池最大线程数。
     */
    private int streamMaxPoolSize = 8;

    /**
     * 聊天入口线程池队列容量。
     */
    private int streamQueueCapacity = 256;

    /**
     * 搜索线程池核心线程数。
     */
    private int searchCorePoolSize = 4;

    /**
     * 搜索线程池最大线程数。
     */
    private int searchMaxPoolSize = 8;

    /**
     * 搜索线程池队列容量。
     */
    private int searchQueueCapacity = 256;

    /**
     * 线程空闲保活秒数。
     */
    private long keepAliveSeconds = 60L;
}
