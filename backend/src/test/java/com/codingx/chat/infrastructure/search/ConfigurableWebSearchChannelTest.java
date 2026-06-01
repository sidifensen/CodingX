package com.codingx.chat.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.service.SearchReferenceCandidate;
import com.codingx.chat.application.service.SearchRequestContext;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 验证可配置联网搜索通道会按 provider 协议请求并归一化返回结果。
 */
class ConfigurableWebSearchChannelTest {

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    /**
     * 未提供 API Key 时应直接禁用通道，避免误发无效请求。
     */
    @Test
    void isEnabledReturnsFalseWhenApiKeyIsBlank() {
        com.codingx.chat.application.service.RuntimeSettingService runtimeSettingService = Mockito.mock(
            com.codingx.chat.application.service.RuntimeSettingService.class
        );
        when(runtimeSettingService.webSearchEnabled()).thenReturn(true);
        when(runtimeSettingService.webSearchProviderOrder()).thenReturn(List.of("serpapi"));
        when(runtimeSettingService.webSearchProviderBaseUrl("serpapi")).thenReturn("https://serpapi.com/search.json");
        when(runtimeSettingService.webSearchProviderApiKey("serpapi")).thenReturn("");

        ConfigurableWebSearchChannel channel = new ConfigurableWebSearchChannel(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            runtimeSettingService,
            new SearchProviderHealthRegistry(2, 30_000L)
        );

        assertFalse(channel.isEnabled(new SearchRequestContext("Spring Boot SSE")));
        assertThrows(IllegalStateException.class, () -> channel.search(new SearchRequestContext("Spring Boot SSE")));
    }

    /**
     * Serper 返回的 organic 结果应被映射为标准来源候选。
     */
    @Test
    void searchParsesSerperResponse() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/serper/search", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                writeBody(outputStream, """
                    {
                      "organic": [
                        {
                          "title": "Spring Boot SSE Guide",
                          "link": "https://docs.spring.io/spring-sse",
                          "snippet": "Explains Server-Sent Events support."
                        },
                        {
                          "title": "SSE Example",
                          "link": "https://example.com/sse",
                          "snippet": "Shows a practical SSE sample."
                        }
                      ]
                    }
                    """);
            }
        });
        httpServer.start();

        com.codingx.chat.application.service.RuntimeSettingService runtimeSettingService = buildRuntimeSettingService(
            "serper",
            "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/serper/search"
        );
        ConfigurableWebSearchChannel channel = new ConfigurableWebSearchChannel(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            runtimeSettingService,
            new SearchProviderHealthRegistry(2, 30_000L)
        );

        List<SearchReferenceCandidate> candidates = channel.search(new SearchRequestContext("Spring Boot SSE"));

        assertEquals(2, candidates.size());
        assertEquals("Spring Boot SSE Guide", candidates.get(0).title());
        assertEquals("docs.spring.io", candidates.get(0).siteName());
        assertEquals(1.0D, candidates.get(0).score());
        assertEquals("SSE Example", candidates.get(1).title());
        assertEquals(0.5D, candidates.get(1).score());
    }

    /**
     * Tavily 返回的 results 结果应被映射为标准来源候选，并保留 provider 分数。
     */
    @Test
    void searchParsesTavilyResponse() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/tavily/search", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                writeBody(outputStream, """
                    {
                      "results": [
                        {
                          "title": "Qwen Latest Update",
                          "url": "https://news.example.com/qwen-update",
                          "content": "Latest model update details.",
                          "score": 0.91
                        }
                      ]
                    }
                    """);
            }
        });
        httpServer.start();

        com.codingx.chat.application.service.RuntimeSettingService runtimeSettingService = buildRuntimeSettingService(
            "tavily",
            "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/tavily/search"
        );
        ConfigurableWebSearchChannel channel = new ConfigurableWebSearchChannel(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            runtimeSettingService,
            new SearchProviderHealthRegistry(2, 30_000L)
        );

        List<SearchReferenceCandidate> candidates = channel.search(new SearchRequestContext("Qwen 最新更新"));

        assertEquals(1, candidates.size());
        assertEquals("Qwen Latest Update", candidates.getFirst().title());
        assertEquals(0.91D, candidates.getFirst().score());
    }

    /**
     * Bing 返回的 webPages 结果应被映射为标准来源候选，并保留排名顺序分数。
     */
    @Test
    void searchParsesBingResponse() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/bing/search", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                writeBody(outputStream, """
                    {
                      "webPages": {
                        "value": [
                          {
                            "name": "Bing Result One",
                            "url": "https://learn.microsoft.com/bing/result-one",
                            "snippet": "First result snippet."
                          },
                          {
                            "name": "Bing Result Two",
                            "url": "https://example.com/bing-two",
                            "snippet": "Second result snippet."
                          }
                        ]
                      }
                    }
                    """);
            }
        });
        httpServer.start();

        com.codingx.chat.application.service.RuntimeSettingService runtimeSettingService = buildRuntimeSettingService(
            "bing",
            "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/bing/search"
        );
        ConfigurableWebSearchChannel channel = new ConfigurableWebSearchChannel(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            runtimeSettingService,
            new SearchProviderHealthRegistry(2, 30_000L)
        );

        List<SearchReferenceCandidate> candidates = channel.search(new SearchRequestContext("Bing Web Search API"));

        assertEquals(2, candidates.size());
        assertEquals("Bing Result One", candidates.get(0).title());
        assertEquals("learn.microsoft.com", candidates.get(0).siteName());
        assertEquals(1.0D, candidates.get(0).score());
        assertEquals("Bing Result Two", candidates.get(1).title());
        assertEquals(0.5D, candidates.get(1).score());
    }

    /**
     * 按配置顺序尝试 provider，前一个失败后应继续请求下一个可用 provider。
     */
    @Test
    void searchFallsBackByConfiguredProviderOrder() throws Exception {
        List<String> requestedPaths = new ArrayList<>();
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/tavily/search", exchange -> {
            requestedPaths.add(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(500, 0);
            exchange.getResponseBody().close();
        });
        httpServer.createContext("/serpapi/search", exchange -> {
            requestedPaths.add(exchange.getRequestURI().getPath());
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                writeBody(outputStream, """
                    {
                      "organic_results": [
                        {
                          "title": "Fallback SerpApi Result",
                          "link": "https://serpapi.example/fallback",
                          "snippet": "Fallback result."
                        }
                      ]
                    }
                    """);
            }
        });
        httpServer.start();

        int port = httpServer.getAddress().getPort();
        com.codingx.chat.application.service.RuntimeSettingService runtimeSettingService = buildFallbackRuntimeSettingService(
            List.of("tavily", "serpapi"),
            "http://127.0.0.1:" + port + "/tavily/search",
            "http://127.0.0.1:" + port + "/serpapi/search"
        );
        ConfigurableWebSearchChannel channel = new ConfigurableWebSearchChannel(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            runtimeSettingService,
            new SearchProviderHealthRegistry(1, 30_000L)
        );

        List<SearchReferenceCandidate> candidates = channel.search(new SearchRequestContext("fallback"));

        assertEquals(List.of("/tavily/search", "/serpapi/search"), requestedPaths);
        assertEquals("Fallback SerpApi Result", candidates.getFirst().title());
    }

    /**
     * 已熔断 provider 应被跳过，避免故障节点继续拖慢搜索主链路。
     */
    @Test
    void searchSkipsOpenCircuitProvider() throws Exception {
        AtomicInteger tavilyCalls = new AtomicInteger();
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/tavily/search", exchange -> {
            tavilyCalls.incrementAndGet();
            exchange.sendResponseHeaders(500, 0);
            exchange.getResponseBody().close();
        });
        httpServer.createContext("/exa/search", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                writeBody(outputStream, """
                    {
                      "results": [
                        {
                          "title": "Exa Result",
                          "url": "https://exa.example/result",
                          "text": "Exa text.",
                          "score": 0.82
                        }
                      ]
                    }
                    """);
            }
        });
        httpServer.start();

        SearchProviderHealthRegistry healthRegistry = new SearchProviderHealthRegistry(1, 30_000L);
        healthRegistry.markFailure("tavily");
        int port = httpServer.getAddress().getPort();
        com.codingx.chat.application.service.RuntimeSettingService runtimeSettingService = buildFallbackRuntimeSettingService(
            List.of("tavily", "exa"),
            "http://127.0.0.1:" + port + "/tavily/search",
            "http://127.0.0.1:" + port + "/exa/search"
        );
        ConfigurableWebSearchChannel channel = new ConfigurableWebSearchChannel(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            runtimeSettingService,
            healthRegistry
        );

        List<SearchReferenceCandidate> candidates = channel.search(new SearchRequestContext("skip open"));

        assertEquals(0, tavilyCalls.get());
        assertEquals("Exa Result", candidates.getFirst().title());
    }

    /**
     * SerpApi 返回的 organic_results 结果应被映射为标准来源候选。
     */
    @Test
    void searchParsesSerpApiResponse() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/serpapi/search", exchange -> {
            assertEquals("test-key", queryValue(exchange.getRequestURI().getRawQuery(), "api_key"));
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                writeBody(outputStream, """
                    {
                      "organic_results": [
                        {
                          "title": "SerpApi Result",
                          "link": "https://serpapi.example/result",
                          "snippet": "SerpApi snippet."
                        }
                      ]
                    }
                    """);
            }
        });
        httpServer.start();

        com.codingx.chat.application.service.RuntimeSettingService runtimeSettingService = buildRuntimeSettingService(
            "serpapi",
            "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/serpapi/search"
        );
        ConfigurableWebSearchChannel channel = new ConfigurableWebSearchChannel(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            runtimeSettingService,
            new SearchProviderHealthRegistry(2, 30_000L)
        );

        List<SearchReferenceCandidate> candidates = channel.search(new SearchRequestContext("SerpApi"));

        assertEquals(1, candidates.size());
        assertEquals("SerpApi Result", candidates.getFirst().title());
        assertEquals("serpapi.example", candidates.getFirst().siteName());
    }

    /**
     * Exa 返回的 results 结果应被映射为标准来源候选，并保留 provider 分数。
     */
    @Test
    void searchParsesExaResponse() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/exa/search", exchange -> {
            assertEquals("test-key", exchange.getRequestHeaders().getFirst("x-api-key"));
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                writeBody(outputStream, """
                    {
                      "results": [
                        {
                          "title": "Exa Search Result",
                          "url": "https://docs.exa.ai/result",
                          "text": "Exa result text.",
                          "score": 0.77
                        }
                      ]
                    }
                    """);
            }
        });
        httpServer.start();

        com.codingx.chat.application.service.RuntimeSettingService runtimeSettingService = buildRuntimeSettingService(
            "exa",
            "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/exa/search"
        );
        ConfigurableWebSearchChannel channel = new ConfigurableWebSearchChannel(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            runtimeSettingService,
            new SearchProviderHealthRegistry(2, 30_000L)
        );

        List<SearchReferenceCandidate> candidates = channel.search(new SearchRequestContext("Exa"));

        assertEquals("Exa Search Result", candidates.getFirst().title());
        assertEquals(0.77D, candidates.getFirst().score());
    }

    /**
     * Bing HTML 结果页应解析标题、链接与摘要，支持无 API Key 的兜底搜索。
     */
    @Test
    void searchParsesBingHtmlResponse() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/bing/html", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                writeBody(outputStream, """
                    <html><body>
                      <li class="b_algo">
                        <h2><a href="https://example.com/bing-html">Bing HTML Result</a></h2>
                        <p>Bing HTML snippet.</p>
                      </li>
                    </body></html>
                    """);
            }
        });
        httpServer.start();

        com.codingx.chat.application.service.RuntimeSettingService runtimeSettingService = buildRuntimeSettingService(
            "bing_html",
            "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/bing/html"
        );
        ConfigurableWebSearchChannel channel = new ConfigurableWebSearchChannel(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            runtimeSettingService,
            new SearchProviderHealthRegistry(2, 30_000L)
        );

        List<SearchReferenceCandidate> candidates = channel.search(new SearchRequestContext("Bing HTML"));

        assertEquals("Bing HTML Result", candidates.getFirst().title());
        assertEquals("Bing HTML snippet.", candidates.getFirst().snippet());
    }

    /**
     * DuckDuckGo HTML 结果页应解析标题、链接与摘要，支持无 API Key 的兜底搜索。
     */
    @Test
    void searchParsesDuckDuckGoHtmlResponse() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/duck/html", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                writeBody(outputStream, """
                    <html><body>
                      <div class="result">
                        <a class="result__a" href="https://duck.example/result">Duck Result</a>
                        <a class="result__snippet">Duck snippet.</a>
                      </div>
                    </body></html>
                    """);
            }
        });
        httpServer.start();

        com.codingx.chat.application.service.RuntimeSettingService runtimeSettingService = buildRuntimeSettingService(
            "duckduckgo_html",
            "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/duck/html"
        );
        ConfigurableWebSearchChannel channel = new ConfigurableWebSearchChannel(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            runtimeSettingService,
            new SearchProviderHealthRegistry(2, 30_000L)
        );

        List<SearchReferenceCandidate> candidates = channel.search(new SearchRequestContext("DuckDuckGo HTML"));

        assertEquals("Duck Result", candidates.getFirst().title());
        assertEquals("Duck snippet.", candidates.getFirst().snippet());
    }

    /**
     * provider 级配置缺失时不读取旧单源配置，确保隐藏的历史配置不会继续驱动搜索请求。
     */
    @Test
    void searchIgnoresLegacySingleProviderConfigWhenProviderScopedConfigMissing() throws Exception {
        com.codingx.chat.application.service.RuntimeSettingService runtimeSettingService = Mockito.mock(
            com.codingx.chat.application.service.RuntimeSettingService.class
        );
        when(runtimeSettingService.webSearchEnabled()).thenReturn(true);
        when(runtimeSettingService.webSearchProviderOrder()).thenReturn(List.of("serpapi"));
        when(runtimeSettingService.webSearchProviderBaseUrl("serpapi")).thenReturn("");
        when(runtimeSettingService.webSearchProviderApiKey("serpapi")).thenReturn("");
        ConfigurableWebSearchChannel channel = new ConfigurableWebSearchChannel(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            runtimeSettingService,
            new SearchProviderHealthRegistry(2, 30_000L)
        );

        assertFalse(channel.isEnabled(new SearchRequestContext("legacy")));
        assertThrows(IllegalStateException.class, () -> channel.search(new SearchRequestContext("legacy")));
    }

    /**
     * 构造最小运行时搜索配置，便于复用。
     * @param provider provider 编码。
     * @param baseUrl 搜索接口地址。
     * @return 运行时配置。
     */
    private com.codingx.chat.application.service.RuntimeSettingService buildRuntimeSettingService(String provider, String baseUrl) {
        com.codingx.chat.application.service.RuntimeSettingService runtimeSettingService = Mockito.mock(
            com.codingx.chat.application.service.RuntimeSettingService.class
        );
        when(runtimeSettingService.webSearchEnabled()).thenReturn(true);
        when(runtimeSettingService.webSearchProviderOrder()).thenReturn(List.of(provider));
        when(runtimeSettingService.webSearchProviderBaseUrl(provider)).thenReturn(baseUrl);
        when(runtimeSettingService.webSearchProviderApiKey(provider)).thenReturn("test-key");
        when(runtimeSettingService.webSearchMaxResults()).thenReturn(5);
        when(runtimeSettingService.webSearchLanguage()).thenReturn("zh-cn");
        when(runtimeSettingService.webSearchCountry()).thenReturn("cn");
        return runtimeSettingService;
    }

    /**
     * 构造带 provider 顺序的运行时搜索配置，用于验证故障切换链路。
     * @param providerOrder provider 尝试顺序。
     * @param firstBaseUrl 第一个 provider 地址。
     * @param secondBaseUrl 第二个 provider 地址。
     * @return 运行时配置。
     */
    private com.codingx.chat.application.service.RuntimeSettingService buildFallbackRuntimeSettingService(
        List<String> providerOrder,
        String firstBaseUrl,
        String secondBaseUrl
    ) {
        com.codingx.chat.application.service.RuntimeSettingService runtimeSettingService = Mockito.mock(
            com.codingx.chat.application.service.RuntimeSettingService.class
        );
        when(runtimeSettingService.webSearchEnabled()).thenReturn(true);
        when(runtimeSettingService.webSearchProviderOrder()).thenReturn(providerOrder);
        when(runtimeSettingService.webSearchProviderBaseUrl(providerOrder.get(0))).thenReturn(firstBaseUrl);
        when(runtimeSettingService.webSearchProviderApiKey(providerOrder.get(0))).thenReturn("test-key");
        when(runtimeSettingService.webSearchProviderBaseUrl(providerOrder.get(1))).thenReturn(secondBaseUrl);
        when(runtimeSettingService.webSearchProviderApiKey(providerOrder.get(1))).thenReturn("test-key");
        when(runtimeSettingService.webSearchMaxResults()).thenReturn(5);
        when(runtimeSettingService.webSearchLanguage()).thenReturn("zh-cn");
        when(runtimeSettingService.webSearchCountry()).thenReturn("cn");
        return runtimeSettingService;
    }

    /**
     * 从测试服务端收到的 query 中提取单个参数，便于断言密钥只出现在 provider 协议要求的位置。
     * @param rawQuery 原始 query。
     * @param name 参数名。
     * @return 参数值。
     */
    private String queryValue(String rawQuery, String name) {
        if (rawQuery == null) {
            return "";
        }
        for (String part : rawQuery.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && name.equals(pair[0])) {
                return pair[1];
            }
        }
        return "";
    }

    /**
     * 统一写入本地 HTTP 响应体。
     * @param outputStream 响应输出流。
     * @param body 响应内容。
     * @throws IOException 写入失败时抛出。
     */
    private void writeBody(OutputStream outputStream, String body) throws IOException {
        outputStream.write(body.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }
}
