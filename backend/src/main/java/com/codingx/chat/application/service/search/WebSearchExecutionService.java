package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;
import com.codingx.common.error.ErrorMessageCatalog;

/**
 * 负责调度搜索通道并串行执行后处理链，返回标准化来源候选。
 */
@Service
@Slf4j
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
        this.postProcessors = postProcessors.stream()
            .sorted(AnnotationAwareOrderComparator.INSTANCE)
            .toList();
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
        List<SearchChannel> enabledChannels = channels.stream()
            .filter(channel -> channel.isEnabled(context))
            .sorted(java.util.Comparator.comparingInt(SearchChannel::getPriority))
            .toList();
        if (enabledChannels.isEmpty()) {
            throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_UNAVAILABLE);
        }
        List<SearchReferenceCandidate> merged = new java.util.ArrayList<>();
        RuntimeException lastFailure = null;
        for (SearchChannel channel : enabledChannels) {
            try {
                List<SearchReferenceCandidate> result = channel.search(context);
                if (result != null && !result.isEmpty()) {
                    merged.addAll(result);
                }
            } catch (RuntimeException exception) {
                lastFailure = exception;
                log.warn("搜索通道执行失败: channel={}, question={}", channel.getName(), StrUtil.maxLength(question, 120), exception);
            }
        }
        if (merged.isEmpty() && lastFailure != null) {
            throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_UNAVAILABLE, lastFailure);
        }
        List<SearchReferenceCandidate> current = merged;
        for (SearchResultPostProcessor postProcessor : postProcessors) {
            current = postProcessor.process(context, current);
        }
        log.info(
            "搜索聚合: 问题={}, 通道数={}, 原始结果数={}, 最终结果数={}",
            StrUtil.maxLength(question, 120),
            enabledChannels.size(),
            merged.size(),
            current.size()
        );
        return current;
    }
}
