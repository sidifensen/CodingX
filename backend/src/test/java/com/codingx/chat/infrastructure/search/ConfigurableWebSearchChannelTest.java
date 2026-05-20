package com.codingx.chat.infrastructure.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.codingx.chat.application.service.SearchReferenceCandidate;
import com.codingx.chat.application.service.SearchRequestContext;
import com.codingx.config.RuntimeProperties;
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
        RuntimeProperties runtimeProperties = new RuntimeProperties();
        runtimeProperties.getWebSearch().setEnabled(true);
        runtimeProperties.getWebSearch().setProvider("serper");
        runtimeProperties.getWebSearch().setBaseUrl("https://google.serper.dev/search");

        ConfigurableWebSearchChannel channel = new ConfigurableWebSearchChannel(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            runtimeProperties
        );

        assertFalse(channel.isEnabled(new SearchRequestContext("Spring Boot SSE")));
        assertEquals(List.of(), channel.search(new SearchRequestContext("Spring Boot SSE")));
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

        RuntimeProperties runtimeProperties = buildRuntimeProperties(
            "serper",
            "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/serper/search"
        );
        ConfigurableWebSearchChannel channel = new ConfigurableWebSearchChannel(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            runtimeProperties
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

        RuntimeProperties runtimeProperties = buildRuntimeProperties(
            "tavily",
            "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/tavily/search"
        );
        ConfigurableWebSearchChannel channel = new ConfigurableWebSearchChannel(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            runtimeProperties
        );

        List<SearchReferenceCandidate> candidates = channel.search(new SearchRequestContext("Qwen 最新更新"));

        assertEquals(1, candidates.size());
        assertEquals("Qwen Latest Update", candidates.getFirst().title());
        assertEquals(0.91D, candidates.getFirst().score());
    }

    /**
     * 构造最小运行时搜索配置，便于复用。
     * @param provider provider 编码。
     * @param baseUrl 搜索接口地址。
     * @return 运行时配置。
     */
    private RuntimeProperties buildRuntimeProperties(String provider, String baseUrl) {
        RuntimeProperties runtimeProperties = new RuntimeProperties();
        runtimeProperties.getWebSearch().setEnabled(true);
        runtimeProperties.getWebSearch().setProvider(provider);
        runtimeProperties.getWebSearch().setBaseUrl(baseUrl);
        runtimeProperties.getWebSearch().setApiKey("test-key");
        runtimeProperties.getWebSearch().setMaxResults(5);
        runtimeProperties.getWebSearch().setLanguage("zh-cn");
        runtimeProperties.getWebSearch().setCountry("cn");
        return runtimeProperties;
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
