package com.codingx.chat.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.application.service.ChatAttachmentService;
import com.codingx.config.AiProperties;
import com.codingx.common.support.ai.AiConversationRequest;
import com.codingx.common.support.ai.AiModelTarget;
import com.codingx.common.support.ai.AiStreamHandler;
import com.codingx.common.support.ai.AiStreamSession;
import com.codingx.common.support.ai.OpenAiStyleStreamParser;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * 验证 OpenAI 兼容 provider 会按深度思考契约下发请求体，并转发上游 reasoning_content。
 */
class OpenAiCompatibleChatClientTest {

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    /**
     * 深度思考请求必须显式发送 enable_thinking，并把真实 reasoning_content 作为 thinking 增量输出。
     */
    @Test
    void streamChatSendsEnableThinkingAndForwardsReasoningContent() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>("");
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/compatible-mode/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                writeChunk(outputStream, """
                    data: {"choices":[{"delta":{"reasoning_content":"先拆解用户问题"}}]}

                    data: {"choices":[{"delta":{"content":"最终回答"}}]}

                    data: [DONE]

                    """);
            }
        });
        httpServer.start();

        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            new OpenAiStyleStreamParser(),
            Mockito.mock(ChatAttachmentService.class)
        );
        List<String> thinkingDeltas = new CopyOnWriteArrayList<>();
        List<String> contentDeltas = new CopyOnWriteArrayList<>();
        client.streamChat(buildThinkingRequest(), buildTarget(httpServer.getAddress().getPort()), new AiStreamHandler() {
            @Override
            public void onThinkingDelta(String delta) {
                thinkingDeltas.add(delta);
            }

            @Override
            public void onContentDelta(String delta) {
                contentDeltas.add(delta);
            }
        }).completion().get(2, TimeUnit.SECONDS);

        JSONObject sentBody = JSONUtil.parseObj(requestBody.get());
        assertTrue(sentBody.getBool("enable_thinking"), "OpenAI 兼容深度思考请求需要显式开启 enable_thinking");
        assertEquals(List.of("先拆解用户问题"), new ArrayList<>(thinkingDeltas));
        assertEquals(List.of("最终回答"), new ArrayList<>(contentDeltas));
    }

    /**
     * 用户上传非图片文件时应把附件摘要注入文本上下文，避免文档附件在模型请求中被丢弃。
     */
    @Test
    void streamChatInjectsTextAttachmentSummaryIntoMessagePayload() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>("");
        ChatAttachmentService chatAttachmentService = Mockito.mock(ChatAttachmentService.class);
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/compatible-mode/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                writeChunk(outputStream, """
                    data: {"choices":[{"delta":{"content":"收到"}}]}

                    data: [DONE]

                    """);
            }
        });
        httpServer.start();

        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            new OpenAiStyleStreamParser(),
            chatAttachmentService
        );
        client.streamChat(buildTextAttachmentRequest(), buildTarget(httpServer.getAddress().getPort()), new AiStreamHandler() {
        }).completion().get(2, TimeUnit.SECONDS);

        JSONObject sentBody = JSONUtil.parseObj(requestBody.get());
        String serializedBody = sentBody.toString();
        assertTrue(serializedBody.contains("以下是用户上传文件的文本摘要"), "请求体应包含附件摘要提示");
        assertTrue(serializedBody.contains("resume.pdf"), "请求体应包含附件文件名");
        assertTrue(serializedBody.contains("这是候选人的简历摘要"), "请求体应包含附件摘要正文");
        Mockito.verify(chatAttachmentService, Mockito.never()).downloadContent(ArgumentMatchers.any());
    }

    /**
     * 路由层首包超时后取消会话时，provider 必须同步取消底层 HTTP Call，避免旧连接继续占用线程和连接池。
     */
    @Test
    void cancelStopsUnderlyingHttpCallPromptly() throws Exception {
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch releaseServer = new CountDownLatch(1);
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/compatible-mode/v1/chat/completions", exchange -> {
            requestStarted.countDown();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write("data: ".getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
                releaseServer.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while holding test SSE stream", exception);
            }
        });
        httpServer.start();

        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(
            new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            new OpenAiStyleStreamParser(),
            Mockito.mock(ChatAttachmentService.class)
        );

        AiStreamSession session = client.streamChat(buildThinkingRequest(), buildTarget(httpServer.getAddress().getPort()), new AiStreamHandler() {
        });
        assertTrue(requestStarted.await(1, TimeUnit.SECONDS), "测试服务应收到 provider 请求");
        Thread.sleep(100L);

        session.cancel();

        try {
            session.completion().handle((ignored, throwable) -> null).get(500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new AssertionError("取消会话后底层 HTTP Call 应快速结束", exception);
        } finally {
            releaseServer.countDown();
        }
    }

    /**
     * 统一写入上游 SSE 块并 flush，模拟 provider 分段响应。
     * @param outputStream 响应输出流。
     * @param chunk SSE 文本块。
     * @throws IOException 写入失败时抛出。
     */
    private void writeChunk(OutputStream outputStream, String chunk) throws IOException {
        outputStream.write(chunk.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    /**
     * 构造开启深度思考的最小请求。
     * @return AI 对话请求。
     */
    private AiConversationRequest buildThinkingRequest() {
        return AiConversationRequest.builder()
            .messages(List.of(ChatMessage.userMessage(1L, "请深度分析这个问题")))
            .preferredModel("qwen3-max")
            .stream(true)
            .thinkingEnabled(true)
            .build();
    }

    /**
     * 构造包含文本附件摘要的请求。
     * @return AI 对话请求。
     */
    private AiConversationRequest buildTextAttachmentRequest() {
        ChatMessage userMessage = ChatMessage.userMessage(1L, "请结合我上传的文件给建议");
        ChatAttachment attachment = ChatAttachment.builder()
            .id(9001L)
            .conversationId(1L)
            .messageId(userMessage.getId())
            .attachmentType("file")
            .fileName("resume.pdf")
            .mimeType("application/pdf")
            .contentSummary("这是候选人的简历摘要")
            .build();
        return AiConversationRequest.builder()
            .messages(List.of(userMessage))
            .attachments(List.of(attachment))
            .preferredModel("qwen3-max")
            .stream(true)
            .thinkingEnabled(false)
            .build();
    }

    /**
     * 生成指向本地 HTTP 服务的百炼兼容模型目标。
     * @param port 本地服务端口。
     * @return 模型目标。
     */
    private AiModelTarget buildTarget(int port) {
        AiProperties.Provider provider = new AiProperties.Provider();
        provider.setBaseUrl("http://127.0.0.1:" + port);
        provider.setApiKey("test-key");
        provider.getEndpoints().put("chat", "/compatible-mode/v1/chat/completions");

        AiProperties.ChatCandidate candidate = new AiProperties.ChatCandidate();
        candidate.setId("qwen3-max");
        candidate.setProvider("bailian");
        candidate.setModel("qwen3-max");
        candidate.setSupportsThinking(true);
        candidate.setEnabled(true);
        candidate.setPriority(1);
        return new AiModelTarget("qwen3-max", candidate, provider);
    }
}
