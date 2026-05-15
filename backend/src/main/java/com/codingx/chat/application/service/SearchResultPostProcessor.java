package com.codingx.chat.application.service;

import java.util.List;

/**
 * 定义搜索结果后处理器契约。
 */
@FunctionalInterface
public interface SearchResultPostProcessor {

    /**
     * 对候选结果做一次后处理。
     * @param context 搜索上下文。
     * @param candidates 当前候选结果。
     * @return 处理后的候选结果。
     */
    List<SearchReferenceCandidate> process(SearchRequestContext context, List<SearchReferenceCandidate> candidates);
}
