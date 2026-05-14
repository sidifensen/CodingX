package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证搜索执行服务的最小 provider 抽象与返回归一化。
 */
class WebSearchExecutionServiceTest {

    /**
     * 搜索服务应优先使用可用 provider，并返回标准化来源结果。
     */
    @Test
    void searchReturnsNormalizedReferencesFromProvider() {
        WebSearchExecutionService service = new WebSearchExecutionService(List.of(
            question -> List.of(new SearchReferenceCandidate("Spring Boot SSE 指南", "https://example.com/sse", "Example", "snippet"))
        ));

        List<SearchReferenceCandidate> references = service.search("请搜索 Spring Boot SSE");

        assertEquals(1, references.size());
        assertEquals("Spring Boot SSE 指南", references.getFirst().title());
    }
}
