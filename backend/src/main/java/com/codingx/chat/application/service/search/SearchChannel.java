package com.codingx.chat.application.service;

import java.util.List;

/**
 * 定义单个搜索源通道的统一契约。
 */
public interface SearchChannel {

    /**
     * 返回通道名称，供日志和后台展示使用。
     * @return 通道名称。
     */
    String getName();

    /**
     * 返回通道优先级，值越小优先级越高。
     * @return 优先级。
     */
    int getPriority();

    /**
     * 判断当前上下文下通道是否启用。
     * @param context 搜索上下文。
     * @return 是否启用。
     */
    default boolean isEnabled(SearchRequestContext context) {
        return true;
    }

    /**
     * 执行一次搜索。
     * @param context 搜索上下文。
     * @return 搜索结果。
     */
    List<SearchReferenceCandidate> search(SearchRequestContext context);
}
