package com.codingx.chat.application.service;

import java.util.List;

/**
 * 搜索结果后处理器契约，负责对不同搜索通道返回的候选来源做排序、去重或可信度增强。
 */
@FunctionalInterface
public interface SearchResultPostProcessor {

    /**
     * 对候选结果做一次后处理。
     * @param context 本次搜索请求上下文，包含原问题、查询词和运行时配置。
     * @param candidates 当前候选结果，可能为空列表；实现不得直接修改调用方持有的集合。
     * @return 处理后的候选结果，顺序会影响最终引用排序。
     */
    List<SearchReferenceCandidate> process(SearchRequestContext context, List<SearchReferenceCandidate> candidates);
}
