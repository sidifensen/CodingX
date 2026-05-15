package com.codingx.chat.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 对多通道结果按 URL 或标题去重，并优先保留分数更高的项。
 */
@Component
public class DeduplicationPostProcessor implements SearchResultPostProcessor {

    @Override
    public List<SearchReferenceCandidate> process(SearchRequestContext context, List<SearchReferenceCandidate> candidates) {
        return candidates.stream()
            .collect(java.util.stream.Collectors.toMap(
                candidate -> candidate.url() == null ? candidate.title() : candidate.url(),
                candidate -> candidate,
                (left, right) -> left.score() >= right.score() ? left : right,
                LinkedHashMap::new
            ))
            .values()
            .stream()
            .toList();
    }
}
