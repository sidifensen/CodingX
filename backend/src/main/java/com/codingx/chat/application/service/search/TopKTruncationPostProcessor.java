package com.codingx.chat.application.service;

import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 对搜索结果做最终数量截断。
 */
@Component
@Order(300)
public class TopKTruncationPostProcessor implements SearchResultPostProcessor {

    private final RuntimeSettingService runtimeSettingService;

    public TopKTruncationPostProcessor(RuntimeSettingService runtimeSettingService) {
        this.runtimeSettingService = runtimeSettingService;
    }

    @Override
    public List<SearchReferenceCandidate> process(SearchRequestContext context, List<SearchReferenceCandidate> candidates) {
        int configuredTopK = runtimeSettingService.searchTopK();
        int topK = Math.max(1, Math.min(50, configuredTopK));
        return candidates.stream()
            .limit(topK)
            .toList();
    }
}
