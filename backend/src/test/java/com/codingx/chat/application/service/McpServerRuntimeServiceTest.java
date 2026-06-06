package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.mcp.application.executor.ChatMcpToolResult;
import com.codingx.mcp.application.service.McpServerRuntimeService;
import com.codingx.mcp.application.service.McpToolNameSupport;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * 验证外部 MCP Server 运行时能发现工具并通过命名空间路由调用。
 */
class McpServerRuntimeServiceTest {

    /** 测试用本地 HTTP MCP Server。 */
    private HttpServer httpServer;

    @AfterEach
    void stopServer() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    /**
     * tools/list 发现结果应写回 schema 快照和健康状态，供模型工具清单动态暴露。
     *
     * @throws Exception 测试 HTTP 服务异常时抛出。
     */
    @Test
    void discoverToolsShouldPersistSchemaSnapshotAndHealth() throws Exception {
        String endpointUrl = startJsonRpcServer();
        ChatMcpRepository repository = Mockito.mock(ChatMcpRepository.class);
        ChatMcp githubMcp = externalMcp(endpointUrl);
        when(repository.findByMcpCode("github")).thenReturn(githubMcp);
        McpServerRuntimeService service = new McpServerRuntimeService(repository, okHttpClient());

        List<com.codingx.tool.application.service.ChatToolSpec> specs = service.discoverTools("github");

        assertEquals(List.of("mcp__github__search"), specs.stream().map(com.codingx.tool.application.service.ChatToolSpec::name).toList());
        assertEquals("mcp__github__search", specs.getFirst().canonicalToolCode());
        ArgumentCaptor<ChatMcp> savedCaptor = ArgumentCaptor.forClass(ChatMcp.class);
        verify(repository).save(savedCaptor.capture());
        assertEquals("AVAILABLE", savedCaptor.getValue().getHealthStatus());
        assertTrue(savedCaptor.getValue().getToolSchemaJson().contains("search"));
    }

    /**
     * 命名空间工具调用应被拆成 MCP 编码和工具名，再通过远程 JSON-RPC tools/call 执行。
     *
     * @throws Exception 测试 HTTP 服务异常时抛出。
     */
    @Test
    void executeNamespacedToolShouldCallRemoteJsonRpcServer() throws Exception {
        String endpointUrl = startJsonRpcServer();
        ChatMcpRepository repository = Mockito.mock(ChatMcpRepository.class);
        when(repository.findByMcpCode("github")).thenReturn(externalMcp(endpointUrl));
        McpServerRuntimeService service = new McpServerRuntimeService(repository, okHttpClient());

        ChatMcpToolResult result = service.execute(
            McpToolNameSupport.buildToolName("github", "search"),
            "{\"query\":\"CodingX\"}"
        );

        assertEquals("mcp__github__search", result.toolId());
        assertTrue(result.content().contains("CodingX repository"));
        assertEquals("github", result.metadata().get("mcpCode"));
        assertEquals("search", result.metadata().get("toolName"));
    }

    private ChatMcp externalMcp(String endpointUrl) {
        return ChatMcp.builder()
            .id(1L)
            .mcpCode("github")
            .displayName("GitHub")
            .sourceType("external")
            .transportType("http")
            .endpointUrl(endpointUrl)
            .enabled(1)
            .deleted(0)
            .build();
    }

    private OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(2))
            .readTimeout(Duration.ofSeconds(2))
            .build();
    }

    private String startJsonRpcServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/mcp", this::handleMcpRequest);
        httpServer.start();
        return "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/mcp";
    }

    private void handleMcpRequest(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String responseBody;
        if (requestBody.contains("\"tools/list\"")) {
            responseBody = """
                {"jsonrpc":"2.0","id":1,"result":{"tools":[{"name":"search","description":"Search repositories","inputSchema":{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}}]}}
                """;
        } else {
            responseBody = """
                {"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"CodingX repository"}],"structuredContent":{"count":1}}}
                """;
        }
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
