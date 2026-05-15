package com.codingx.chat.application.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.springframework.stereotype.Service;

/**
 * 负责调度搜索通道并串行执行后处理链，返回标准化来源候选。
 */
@Service
public class WebSearchExecutionService {

    private final List<SearchChannel> channels;
    private final List<SearchResultPostProcessor> postProcessors;
    private final ExecutorService searchExecutor;

    /**
     * 使用可注入通道和后处理器构造搜索服务。
     * @param channels 搜索通道集合。
     * @param postProcessors 后处理器集合。
     */
    public WebSearchExecutionService(List<SearchChannel> channels, List<SearchResultPostProcessor> postProcessors, ExecutorService searchExecutor) {
        this.channels = channels;
        this.postProcessors = postProcessors;
        this.searchExecutor = searchExecutor;
    }

    /**
     * 汇总所有搜索通道结果，并按顺序执行后处理器。
     * @param question 搜索问题。
     * @return 来源候选列表。
     */
    @ConversationTraceNode(name = "web-search", type = "SEARCH")
    public List<SearchReferenceCandidate> search(String question) {
        SearchRequestContext context = new SearchRequestContext(question);
        List<CompletableFuture<List<SearchReferenceCandidate>>> futures = channels.stream()
            .filter(channel -> channel.isEnabled(context))
            .sorted(java.util.Comparator.comparingInt(SearchChannel::getPriority))
            .map(channel -> CompletableFuture.supplyAsync(() -> channel.search(context), searchExecutor))
            .toList();
        List<SearchReferenceCandidate> merged = new java.util.ArrayList<>();
        for (CompletableFuture<List<SearchReferenceCandidate>> future : futures) {
            List<SearchReferenceCandidate> result = future.join();
            if (result != null && !result.isEmpty()) {
                merged.addAll(result);
            }
        }
        List<SearchReferenceCandidate> current = merged;
        for (SearchResultPostProcessor postProcessor : postProcessors) {
            current = postProcessor.process(context, current);
        }
        return current;
    }
}
