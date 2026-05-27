package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证搜索执行服务的最小 provider 抽象与返回归一化。
 */
@ExtendWith(MockitoExtension.class)
class WebSearchExecutionServiceTest {

    @Mock
    private RuntimeSettingService runtimeSettingService;

    /**
     * 搜索服务应优先使用可用 provider，并返回标准化来源结果。
     */
    @Test
    void searchReturnsNormalizedReferencesFromProvider() {
        when(runtimeSettingService.searchTimeoutMs()).thenReturn(15_000L);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        WebSearchExecutionService service = new WebSearchExecutionService(
            List.of(new SearchChannel() {
                @Override
                public String getName() {
                    return "mock";
                }

                @Override
                public int getPriority() {
                    return 1;
                }

                @Override
                public List<SearchReferenceCandidate> search(SearchRequestContext context) {
                    return List.of(new SearchReferenceCandidate("Spring Boot SSE 指南", "https://example.com/sse", "Example", "snippet", 0.72D));
                }
            }),
            List.of(),
            executorService,
            runtimeSettingService
        );

        List<SearchReferenceCandidate> references = service.search("请搜索 Spring Boot SSE");

        assertEquals(1, references.size());
        assertEquals("Spring Boot SSE 指南", references.getFirst().title());
        executorService.shutdownNow();
    }

    /**
     * 搜索服务应汇总多个通道结果，并按后处理顺序执行去重与重排。
     */
    @Test
    void searchAggregatesChannelsAndAppliesPostProcessors() {
        when(runtimeSettingService.searchTimeoutMs()).thenReturn(15_000L);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        SearchChannel primaryChannel = new SearchChannel() {
            @Override
            public String getName() {
                return "primary";
            }

            @Override
            public int getPriority() {
                return 1;
            }

            @Override
            public List<SearchReferenceCandidate> search(SearchRequestContext context) {
                return List.of(
                    new SearchReferenceCandidate("重复来源", "https://example.com/a", "Primary", "A", 0.60D),
                    new SearchReferenceCandidate("二级结果", "https://example.com/b", "Primary", "B", 0.40D)
                );
            }
        };
        SearchChannel backupChannel = new SearchChannel() {
            @Override
            public String getName() {
                return "backup";
            }

            @Override
            public int getPriority() {
                return 2;
            }

            @Override
            public List<SearchReferenceCandidate> search(SearchRequestContext context) {
                return List.of(
                    new SearchReferenceCandidate("重复来源", "https://example.com/a", "Backup", "A2", 0.80D),
                    new SearchReferenceCandidate("头部结果", "https://example.com/c", "Backup", "C", 0.95D)
                );
            }
        };
        SearchResultPostProcessor dedup = (context, candidates) -> candidates.stream()
            .collect(java.util.stream.Collectors.toMap(
                SearchReferenceCandidate::url,
                candidate -> candidate,
                (left, right) -> left.score() >= right.score() ? left : right,
                java.util.LinkedHashMap::new
            ))
            .values()
            .stream()
            .toList();
        SearchResultPostProcessor rerank = (context, candidates) -> candidates.stream()
            .sorted(java.util.Comparator.comparingDouble(SearchReferenceCandidate::score).reversed())
            .limit(2)
            .toList();
        WebSearchExecutionService service = new WebSearchExecutionService(
            List.of(primaryChannel, backupChannel),
            List.of(dedup, rerank),
            executorService,
            runtimeSettingService
        );

        List<SearchReferenceCandidate> references = service.search("请搜索 Spring Boot SSE");

        assertEquals(2, references.size());
        assertIterableEquals(
            List.of("头部结果", "重复来源"),
            references.stream().map(SearchReferenceCandidate::title).toList()
        );
        assertEquals("Backup", references.get(1).siteName());
        executorService.shutdownNow();
    }

    /**
     * 最新模型问题遇到多个官方 OpenAI 结果时，应优先把版本号更新的官方文档放入证据首位。
     */
    @Test
    void searchReranksOfficialLatestModelResultAheadOfOlderOfficialResult() {
        when(runtimeSettingService.searchTimeoutMs()).thenReturn(15_000L);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        SearchChannel searchChannel = new SearchChannel() {
            @Override
            public String getName() {
                return "mock";
            }

            @Override
            public int getPriority() {
                return 1;
            }

            @Override
            public List<SearchReferenceCandidate> search(SearchRequestContext context) {
                return List.of(
                    new SearchReferenceCandidate(
                        "GPT-5.4 Model",
                        "https://developers.openai.com/api/docs/models/gpt-5.4/",
                        "developers.openai.com",
                        "GPT-5.4 is an OpenAI model.",
                        0.99D
                    ),
                    new SearchReferenceCandidate(
                        "GPT-5.5 Model",
                        "https://developers.openai.com/api/docs/models/gpt-5.5/",
                        "developers.openai.com",
                        "GPT-5.5 is OpenAI's latest flagship model.",
                        0.72D
                    ),
                    new SearchReferenceCandidate(
                        "媒体称 OpenAI 发布 GPT-5.6",
                        "https://news.example.com/openai-gpt-5-6",
                        "news.example.com",
                        "第三方媒体报道了未经官方确认的新版本。",
                        1.0D
                    )
                );
            }
        };
        WebSearchExecutionService service = new WebSearchExecutionService(
            List.of(searchChannel),
            List.of(new AuthoritativeLatestSearchPostProcessor()),
            executorService,
            runtimeSettingService
        );

        List<SearchReferenceCandidate> references = service.search("gpt 最新模型是什么");

        assertEquals("GPT-5.5 Model", references.getFirst().title());
        assertEquals("developers.openai.com", references.getFirst().siteName());
        executorService.shutdownNow();
    }

    /**
     * 构造器应按后处理器注解顺序执行，确保普通分数重排不会覆盖权威最新排序。
     */
    @Test
    void searchSortsPostProcessorsSoAuthoritativeLatestRunsAfterGenericRerank() {
        when(runtimeSettingService.searchTimeoutMs()).thenReturn(15_000L);
        when(runtimeSettingService.searchRerankEnabled()).thenReturn(true);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        SearchChannel searchChannel = new SearchChannel() {
            @Override
            public String getName() {
                return "mock";
            }

            @Override
            public int getPriority() {
                return 1;
            }

            @Override
            public List<SearchReferenceCandidate> search(SearchRequestContext context) {
                return List.of(
                    new SearchReferenceCandidate(
                        "GPT-5.4 Model",
                        "https://developers.openai.com/api/docs/models/gpt-5.4/",
                        "developers.openai.com",
                        "GPT-5.4 is an OpenAI model.",
                        0.99D
                    ),
                    new SearchReferenceCandidate(
                        "GPT-5.5 Model",
                        "https://developers.openai.com/api/docs/models/gpt-5.5/",
                        "developers.openai.com",
                        "GPT-5.5 is OpenAI's latest flagship model.",
                        0.72D
                    )
                );
            }
        };
        WebSearchExecutionService service = new WebSearchExecutionService(
            List.of(searchChannel),
            List.of(new AuthoritativeLatestSearchPostProcessor(), new RerankPostProcessor(runtimeSettingService)),
            executorService,
            runtimeSettingService
        );

        List<SearchReferenceCandidate> references = service.search("gpt 最新模型是什么");

        assertEquals("GPT-5.5 Model", references.getFirst().title());
        executorService.shutdownNow();
    }
}
