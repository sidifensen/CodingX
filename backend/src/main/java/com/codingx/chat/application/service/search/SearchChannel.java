package com.codingx.chat.application.service;

import java.util.List;

/**
 * 单个搜索源通道契约，屏蔽本地、联网或第三方检索源的启用判断与结果获取差异。
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
     * @param context 本次搜索请求上下文，包含查询词、用户问题和动态配置。
     * @return true 表示该通道参与本轮搜索，false 表示跳过并交由其他通道处理。
     */
    default boolean isEnabled(SearchRequestContext context) {
        return true;
    }

    /**
     * 执行一次搜索。
     * @param context 本次搜索请求上下文，调用方已完成基础参数校验。
     * @return 搜索结果候选列表，空列表表示当前通道没有可用引用。
     */
    List<SearchReferenceCandidate> search(SearchRequestContext context);
}
