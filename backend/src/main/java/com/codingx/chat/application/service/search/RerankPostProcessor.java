package com.codingx.chat.application.service;

import java.util.Comparator;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 使用启发式分数对搜索结果做重排，为后续真实 rerank 服务预留接口位置。
 */
@Component
@Order(100)
public class RerankPostProcessor implements SearchResultPostProcessor {

    /** 运行时配置服务，用于读取搜索结果重排开关。 */
    private final RuntimeSettingService runtimeSettingService;

    public RerankPostProcessor(RuntimeSettingService runtimeSettingService) {
        this.runtimeSettingService = runtimeSettingService;
    }

    @Override
    public List<SearchReferenceCandidate> process(SearchRequestContext context, List<SearchReferenceCandidate> candidates) {
        if (!runtimeSettingService.searchRerankEnabled()) {
            return candidates;
        }
        return candidates.stream()
            .sorted(Comparator.comparingDouble((SearchReferenceCandidate candidate) -> candidate.score() == null ? 0D : candidate.score()).reversed())
            .toList();
    }
}
