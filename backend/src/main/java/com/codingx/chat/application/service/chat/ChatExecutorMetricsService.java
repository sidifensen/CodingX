package com.codingx.chat.application.service;

import com.codingx.config.ContextAwareExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 线程池运行态指标服务，统一提取聊天链路执行器指标。
 */
@Service
public class ChatExecutorMetricsService {

    /** 聊天流式执行线程池，用于统计 SSE 后台任务的活跃线程和排队情况。 */
    private final ExecutorService chatStreamExecutor;
    /** 搜索执行线程池，用于统计多子问题检索任务的资源占用情况。 */
    private final ExecutorService searchExecutor;

    public ChatExecutorMetricsService(
        @Qualifier("chatStreamExecutor") ExecutorService chatStreamExecutor,
        @Qualifier("searchExecutor") ExecutorService searchExecutor
    ) {
        this.chatStreamExecutor = chatStreamExecutor;
        this.searchExecutor = searchExecutor;
    }

    /**
     * 读取当前线程池指标快照。
     * @return 线程池指标。
     */
    public ChatRuntimeExecutorDashboardView snapshot() {
        ThreadPoolMetrics streamMetrics = resolveMetrics(chatStreamExecutor);
        ThreadPoolMetrics searchMetrics = resolveMetrics(searchExecutor);
        return new ChatRuntimeExecutorDashboardView(
            streamMetrics.activeCount(),
            streamMetrics.poolSize(),
            streamMetrics.queueSize(),
            streamMetrics.queueRemainingCapacity(),
            searchMetrics.activeCount(),
            searchMetrics.poolSize(),
            searchMetrics.queueSize(),
            searchMetrics.queueRemainingCapacity()
        );
    }

    /**
     * 从执行器提取线程池指标，非 ThreadPoolExecutor 场景回退为零值。
     * @param executorService 执行器实例。
     * @return 线程池指标。
     */
    private ThreadPoolMetrics resolveMetrics(ExecutorService executorService) {
        if (executorService instanceof ContextAwareExecutorService contextAwareExecutorService) {
            return resolveMetrics(contextAwareExecutorService.delegate());
        }
        if (executorService instanceof ThreadPoolExecutor threadPoolExecutor) {
            return new ThreadPoolMetrics(
                threadPoolExecutor.getActiveCount(),
                threadPoolExecutor.getPoolSize(),
                threadPoolExecutor.getQueue().size(),
                threadPoolExecutor.getQueue().remainingCapacity()
            );
        }
        return new ThreadPoolMetrics(0, 0, 0, 0);
    }

    /**
     * 线程池指标载体。
     */
    private record ThreadPoolMetrics(
        int activeCount,
        int poolSize,
        int queueSize,
        int queueRemainingCapacity
    ) {
    }
}
