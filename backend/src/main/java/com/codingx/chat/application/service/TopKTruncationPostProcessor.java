package com.codingx.chat.application.service;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 对搜索结果做最终数量截断。
 */
@Component
public class TopKTruncationPostProcessor implements SearchResultPostProcessor {

    private static final int DEFAULT_TOP_K = 5;

    @Override
    public List<SearchReferenceCandidate> process(SearchRequestContext context, List<SearchReferenceCandidate> candidates) {
        return candidates.stream()
            .limit(DEFAULT_TOP_K)
            .toList();
    }
}
