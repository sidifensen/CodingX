package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 验证代码检索 MCP 工具执行器的核心行为。
 */
class CodeSearchMcpToolExecutorTest {

    /**
     * 命中代码时应返回文件路径、行号和命中片段。
     *
     * @throws Exception 文件构造失败时抛出。
     */
    @Test
    void executeReturnsMatchedFileLineAndSnippet() throws Exception {
        Path root = Files.createTempDirectory("code-search-test-");
        Path srcFile = root.resolve("src/main/java/com/example/DemoController.java");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(
            srcFile,
            """
            package com.example;
            public class DemoController {
                public void sendMessage() {
                    System.out.println("hello");
                }
            }
            """,
            StandardCharsets.UTF_8
        );
        CodeSearchMcpToolExecutor executor = new CodeSearchMcpToolExecutor(root.toString(), 20, 1024 * 1024L);

        ChatMcpToolResult result = executor.execute("查找 sendMessage 实现");

        assertEquals("code_search", result.toolId());
        assertTrue(result.content().contains("DemoController.java"));
        assertTrue(result.content().contains("sendMessage"));
        assertTrue(result.content().contains(":3") || result.content().contains("第3行"));
    }

    /**
     * 检索时应跳过排除目录，避免扫描 node_modules 噪声。
     *
     * @throws Exception 文件构造失败时抛出。
     */
    @Test
    void executeSkipsExcludedDirectories() throws Exception {
        Path root = Files.createTempDirectory("code-search-test-");
        Path excludedFile = root.resolve("node_modules/pkg/index.js");
        Files.createDirectories(excludedFile.getParent());
        Files.writeString(excludedFile, "function sendMessage() {}", StandardCharsets.UTF_8);
        CodeSearchMcpToolExecutor executor = new CodeSearchMcpToolExecutor(root.toString(), 20, 1024 * 1024L);

        ChatMcpToolResult result = executor.execute("sendMessage");

        assertTrue(result.content().contains("未找到") || !result.content().contains("node_modules"));
    }
}
