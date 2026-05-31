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

    /** 搜索通道集合，用于按优先级串行调用不同外部搜索来源。 */
    private final List<SearchChannel> channels;
    /** 搜索结果后处理器集合，用于对聚合结果执行去重、重排和截断。 */
    private final List<SearchResultPostProcessor> postProcessors;
    /** 搜索执行线程池，保留给调用方统一治理搜索任务资源。 */
    private final ExecutorService searchExecutor;
    /** 运行时配置服务，用于读取搜索超时和后处理相关动态参数。 */
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
        // 步骤 1：基于运行时配置解析本次搜索超时时间，并封装通道共享的请求上下文。
        long timeoutMs = Math.max(1_000L, runtimeSettingService.searchTimeoutMs());
        SearchRequestContext context = new SearchRequestContext(question, timeoutMs);

        // 步骤 2：过滤当前上下文可用的搜索通道，再按优先级排序，保证执行顺序稳定可控。
        List<SearchChannel> enabledChannels = channels.stream()
            .filter(channel -> channel.isEnabled(context))
            .sorted(java.util.Comparator.comparingInt(SearchChannel::getPriority))
            .toList();
        if (enabledChannels.isEmpty()) {
            throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_UNAVAILABLE);
        }

        // 步骤 3：串行调用各搜索通道，允许单个通道失败后继续聚合其他通道的有效结果。
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

        // 步骤 4：如果所有通道都未产出结果且至少发生过一次异常，将最后一次异常作为根因返回给上层。
        if (merged.isEmpty() && lastFailure != null) {
            throw new IllegalStateException(ErrorMessageCatalog.WEB_SEARCH_UNAVAILABLE, lastFailure);
        }

        // 步骤 5：按 Spring 排序结果执行后处理链，用于去重、截断或补齐展示所需字段。
        List<SearchReferenceCandidate> current = merged;
        for (SearchResultPostProcessor postProcessor : postProcessors) {
            current = postProcessor.process(context, current);
        }

        // 步骤 6：记录聚合前后的规模指标，便于排查通道可用性和后处理过滤效果。
        log.info(
            "搜索聚合/完成: 问题={}, 通道数={}, 原始结果数={}, 最终结果数={}",
            StrUtil.maxLength(question, 120),
            enabledChannels.size(),
            merged.size(),
            current.size()
        );
        return current;
    }
}
