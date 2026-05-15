package com.codingx.chat.application.service;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 负责按顺序调用搜索 provider，并返回标准化来源候选。
 */
@Service
public class WebSearchExecutionService {

    private final List<WebSearchProvider> providers;

    /**
     * 使用可注入 provider 列表构造搜索服务。
     * @param providers 搜索 provider 列表。
     */
    public WebSearchExecutionService(List<WebSearchProvider> providers) {
        this.providers = providers;
    }

    /**
     * 依次尝试搜索 provider，返回首个非空结果。
     * @param question 搜索问题。
     * @return 来源候选列表。
     */
    @ConversationTraceNode(name = "web-search", type = "SEARCH")
    public List<SearchReferenceCandidate> search(String question) {
        for (WebSearchProvider provider : providers) {
            List<SearchReferenceCandidate> result = provider.search(question);
            if (result != null && !result.isEmpty()) {
                return result;
            }
        }
        return List.of();
    }

    /**
     * 定义搜索 provider 契约。
     */
    @FunctionalInterface
    public interface WebSearchProvider {
        List<SearchReferenceCandidate> search(String question);
    }
}
