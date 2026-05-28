package com.codingx.chat.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.service.SearchReferenceCandidate;
import com.codingx.chat.application.service.SearchRequestContext;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
        when(runtimeSettingService.webSearchProvider()).thenReturn("serper");
        when(runtimeSettingService.webSearchBaseUrl()).thenReturn("https://google.serper.dev/search");
        when(runtimeSettingService.webSearchApiKey()).thenReturn("");

        ConfigurableWebSearchChannel channel = new ConfigurableWebSearchChannel(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            runtimeSettingService
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
            runtimeSettingService
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
            runtimeSettingService
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
            runtimeSettingService
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
        when(runtimeSettingService.webSearchProvider()).thenReturn(provider);
        when(runtimeSettingService.webSearchBaseUrl()).thenReturn(baseUrl);
        when(runtimeSettingService.webSearchApiKey()).thenReturn("test-key");
        when(runtimeSettingService.webSearchMaxResults()).thenReturn(5);
        when(runtimeSettingService.webSearchLanguage()).thenReturn("zh-cn");
        when(runtimeSettingService.webSearchCountry()).thenReturn("cn");
        return runtimeSettingService;
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
