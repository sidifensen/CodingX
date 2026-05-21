package com.codingx.config;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.codingx.chat.application.service.RuntimeSettingService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 聊天链路线程池配置，按入口与搜索拆分专用执行器并统一透传线程上下文。
 */
@Configuration
public class ChatExecutorConfig {

    /**
     * 聊天流入口线程池，承担 stream 入口派发与执行主链路。
     * @param runtimeSettingService 运行时配置服务。
     * @return 上下文透传执行器。
     */
    @Bean(name = "chatStreamExecutor", destroyMethod = "shutdown")
    public ExecutorService chatStreamExecutor(RuntimeSettingService runtimeSettingService) {
        int streamCorePoolSize = Math.max(1, runtimeSettingService.chatExecutorStreamCorePoolSize());
        int streamMaxPoolSize = Math.max(streamCorePoolSize, runtimeSettingService.chatExecutorStreamMaxPoolSize());
        ThreadPoolExecutor delegate = new ThreadPoolExecutor(
            streamCorePoolSize,
            streamMaxPoolSize,
            Math.max(1L, runtimeSettingService.chatExecutorKeepAliveSeconds()),
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(Math.max(1, runtimeSettingService.chatExecutorStreamQueueCapacity())),
            ThreadFactoryBuilder.create().setNamePrefix("chat_stream_executor_").build(),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        return new ContextAwareExecutorService(delegate);
    }

    /**
     * 搜索并行执行线程池，承担联网检索与后处理并发任务。
     * @param runtimeSettingService 运行时配置服务。
     * @return 上下文透传执行器。
     */
    @Bean(name = "searchExecutor", destroyMethod = "shutdown")
    public ExecutorService searchExecutor(RuntimeSettingService runtimeSettingService) {
        int searchCorePoolSize = Math.max(1, runtimeSettingService.chatExecutorSearchCorePoolSize());
        int searchMaxPoolSize = Math.max(searchCorePoolSize, runtimeSettingService.chatExecutorSearchMaxPoolSize());
        ThreadPoolExecutor delegate = new ThreadPoolExecutor(
            searchCorePoolSize,
            searchMaxPoolSize,
            Math.max(1L, runtimeSettingService.chatExecutorKeepAliveSeconds()),
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(Math.max(1, runtimeSettingService.chatExecutorSearchQueueCapacity())),
            ThreadFactoryBuilder.create().setNamePrefix("chat_search_executor_").build(),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        return new ContextAwareExecutorService(delegate);
    }
}
