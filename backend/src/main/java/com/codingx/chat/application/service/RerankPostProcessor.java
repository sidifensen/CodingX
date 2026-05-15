package com.codingx.chat.application.service;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 使用启发式分数对搜索结果做重排，为后续真实 rerank 服务预留接口位置。
 */
@Component
public class RerankPostProcessor implements SearchResultPostProcessor {

    @Override
    public List<SearchReferenceCandidate> process(SearchRequestContext context, List<SearchReferenceCandidate> candidates) {
        return candidates.stream()
            .sorted(Comparator.comparingDouble((SearchReferenceCandidate candidate) -> candidate.score() == null ? 0D : candidate.score()).reversed())
            .toList();
    }
}
