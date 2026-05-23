package com.codingx.chat.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.config.AiProperties;
import com.codingx.common.support.ai.AiConversationRequest;
import com.codingx.common.support.ai.AiModelTarget;
import com.codingx.common.support.ai.AiStreamHandler;
import com.codingx.common.support.ai.OpenAiStyleStreamParser;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 验证 DeepSeek provider 会在上游 SSE 到达时立即向下游转发增量，而不是等整段响应结束。
 */
class DeepSeekOkHttpChatClientTest {

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    /**
     * 当上游分两段发送 SSE 时，首个 delta 应在连接完成前就被转发给应用层。
     */
    @Test
    void streamChatForwardsDeltaBeforeUpstreamCompletes() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/chat/completions", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                writeChunk(outputStream, """
                    data: {"choices":[{"delta":{"content":"Hello"}}]}

                    """);
                sleepSilently(350L);
                writeChunk(outputStream, """
                    data: {"choices":[{"delta":{"content":" world"}}]}

                    data: [DONE]

                    """);
            }
        });
        httpServer.start();

        DeepSeekOkHttpChatClient client = new DeepSeekOkHttpChatClient(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            buildAiProperties(httpServer.getAddress().getPort()),
            new OpenAiStyleStreamParser()
        );
        List<String> deltas = new CopyOnWriteArrayList<>();
        CountDownLatch firstDeltaLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(1);
        AiModelTarget target = buildTarget(httpServer.getAddress().getPort());
        CompletableFuture<Void> future = client.streamChat(buildRequest(), target, new AiStreamHandler() {
            @Override
            public void onContentDelta(String delta) {
                deltas.add(delta);
                firstDeltaLatch.countDown();
            }

            @Override
            public void onComplete() {
                doneLatch.countDown();
            }
        }).completion();

        assertTrue(firstDeltaLatch.await(200, TimeUnit.MILLISECONDS), "first delta should arrive before upstream completes");
        assertEquals(1L, doneLatch.getCount(), "completion should not happen before the stream finishes");

        future.get(2, TimeUnit.SECONDS);

        assertEquals(List.of("Hello", " world"), new ArrayList<>(deltas));
        assertTrue(doneLatch.await(100, TimeUnit.MILLISECONDS), "completion event should be emitted after [DONE]");
    }

    /**
     * 未暴露模型工具时请求体不应带 tools:null，避免兼容 provider 对 null 数组字段解析失败。
     */
    @Test
    void streamChatOmitsToolsFieldWhenNoToolSpecsAreVisible() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>("");
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                writeChunk(outputStream, """
                    data: {"choices":[{"delta":{"content":"ok"}}]}

                    data: [DONE]

                    """);
            }
        });
        httpServer.start();

        DeepSeekOkHttpChatClient client = new DeepSeekOkHttpChatClient(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            buildAiProperties(httpServer.getAddress().getPort()),
            new OpenAiStyleStreamParser()
        );

        client.streamChat(buildRequest(), buildTarget(httpServer.getAddress().getPort()), new AiStreamHandler() {
        }).completion().get(2, TimeUnit.SECONDS);

        JSONObject sentBody = JSONUtil.parseObj(requestBody.get());
        assertFalse(sentBody.containsKey("tools"), "空工具列表应省略 tools 字段");
    }

    /**
     * 统一写入单个 SSE 数据块并立即 flush，模拟上游 provider 的真实分段行为。
     * @param outputStream 响应输出流。
     * @param chunk SSE 文本块。
     * @throws IOException 写入失败时抛出。
     */
    private void writeChunk(OutputStream outputStream, String chunk) throws IOException {
        outputStream.write(chunk.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    /**
     * 构造最小 AI 请求，确保 provider 会走真实流式代码路径。
     * @return 对话请求。
     */
    private AiConversationRequest buildRequest() {
        return AiConversationRequest.builder()
            .messages(List.of(ChatMessage.userMessage(1L, "你好")))
            .preferredModel("test-model")
            .stream(true)
            .thinkingEnabled(false)
            .build();
    }

    /**
     * 组装测试专用 AI 配置，指向本地临时 SSE 服务。
     * @param port 临时服务端口。
     * @return AI 配置。
     */
    private AiProperties buildAiProperties(int port) {
        AiProperties aiProperties = new AiProperties();
        aiProperties.setApiKey("test-key");
        aiProperties.setBaseUrl("http://127.0.0.1:" + port);
        aiProperties.setChatModel("test-model");
        return aiProperties;
    }

    /**
     * 生成测试使用的模型目标，模拟路由层传给 provider 的真实参数。
     * @param port 本地测试 HTTP 服务端口。
     * @return 模型目标。
     */
    private AiModelTarget buildTarget(int port) {
        AiProperties.Provider provider = new AiProperties.Provider();
        provider.setBaseUrl("http://127.0.0.1:" + port);
        provider.setApiKey("test-key");
        AiProperties.ChatCandidate candidate = new AiProperties.ChatCandidate();
        candidate.setId("test-model");
        candidate.setProvider("deepseek");
        candidate.setModel("test-model");
        candidate.setEnabled(true);
        candidate.setPriority(1);
        return new AiModelTarget("test-model", candidate, provider);
    }

    /**
     * 测试中通过短暂等待制造稳定的分段边界，便于观察客户端是否实时转发。
     * @param millis 等待毫秒数。
     */
    private void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating upstream SSE delay", exception);
        }
    }
}
