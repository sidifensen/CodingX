package com.codingx.chat.application.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 负责调度搜索通道并串行执行后处理链，返回标准化来源候选。
 */
@Service
public class WebSearchExecutionService {

    private final List<SearchChannel> channels;
    private final List<SearchResultPostProcessor> postProcessors;
    private final ExecutorService searchExecutor;
    private final RuntimeSettingService runtimeSettingService;

    /**
     * 使用可注入通道和后处理器构造搜索服务。
     * @param channels 搜索通道集合。
     * @param postProcessors 后处理器集合。
     */
    public WebSearchExecutionService(
        List<SearchChannel> channels,
        List<SearchResultPostProcessor> postProcessors,
        @Qualifier("searchExecutor")
        ExecutorService searchExecutor,
        RuntimeSettingService runtimeSettingService
    ) {
        this.channels = channels;
        this.postProcessors = postProcessors;
        this.searchExecutor = searchExecutor;
        this.runtimeSettingService = runtimeSettingService;
    }

    /**
     * 汇总所有搜索通道结果，并按顺序执行后处理器。
     * @param question 搜索问题。
     * @return 来源候选列表。
     */
    @ConversationTraceNode(name = "web-search", type = "SEARCH")
    public List<SearchReferenceCandidate> search(String question) {
        long timeoutMs = Math.max(1_000L, runtimeSettingService.searchTimeoutMs());
        SearchRequestContext context = new SearchRequestContext(question, timeoutMs);
        List<CompletableFuture<List<SearchReferenceCandidate>>> futures = channels.stream()
            .filter(channel -> channel.isEnabled(context))
            .sorted(java.util.Comparator.comparingInt(SearchChannel::getPriority))
            .map(channel -> CompletableFuture.supplyAsync(() -> channel.search(context), searchExecutor))
            .toList();
        List<SearchReferenceCandidate> merged = new java.util.ArrayList<>();
        for (CompletableFuture<List<SearchReferenceCandidate>> future : futures) {
            try {
                List<SearchReferenceCandidate> result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                if (result != null && !result.isEmpty()) {
                    merged.addAll(result);
                }
            } catch (TimeoutException timeoutException) {
                future.cancel(true);
            } catch (Exception ignored) {
                // 单通道异常不影响整体搜索结果聚合。
            }
        }
        List<SearchReferenceCandidate> current = merged;
        for (SearchResultPostProcessor postProcessor : postProcessors) {
            current = postProcessor.process(context, current);
        }
        return current;
    }
}
