package com.codingx.tool.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证工具执行服务会在进入注册表前统一处理模型可见别名。
 */
class ChatToolExecutionServiceTest {

    /**
     * Claude Code 风格工具名应归一到既有短工具编码执行，并在结果元数据保留归一化信息。
     */
    @Test
    void executeShouldNormalizeClaudeCodeAliasBeforeRegistryLookup() {
        ChatToolExecutor readExecutor = new ChatToolExecutor() {
            @Override
            public List<String> toolCodes() {
                return List.of("read");
            }

            @Override
            public ChatToolExecutionResult execute(String toolCode, String question) {
                return new ChatToolExecutionResult(toolCode, "读取成功", Map.of("question", question));
            }
        };
        ChatToolRegistry registry = new ChatToolRegistry(List.of(readExecutor));
        registry.init();
        ChatToolExecutionService service = new ChatToolExecutionService(registry, new LocalToolAliasService());

        ChatToolExecutionResult result = service.execute("ReadFile", "{\"path\":\"README.md\"}");

        assertEquals("read", result.toolCode());
        assertEquals("read", result.metadata().get("canonicalToolCode"));
        assertEquals("ReadFile", result.metadata().get("requestedToolCode"));
        assertEquals("{\"path\":\"README.md\"}", result.metadata().get("question"));
    }

    /**
     * 旧短工具名仍按原编码执行，元数据只补充统一 canonical 字段。
     */
    @Test
    void executeShouldKeepLegacyToolCodeBehavior() {
        ChatToolExecutor grepExecutor = new ChatToolExecutor() {
            @Override
            public List<String> toolCodes() {
                return List.of("grep");
            }

            @Override
            public ChatToolExecutionResult execute(String toolCode, String question) {
                return new ChatToolExecutionResult(toolCode, "命中 1 处", Map.of());
            }
        };
        ChatToolRegistry registry = new ChatToolRegistry(List.of(grepExecutor));
        registry.init();
        ChatToolExecutionService service = new ChatToolExecutionService(registry, new LocalToolAliasService());

        ChatToolExecutionResult result = service.execute("grep", "{\"pattern\":\"Agent\"}");

        assertEquals("grep", result.toolCode());
        assertEquals("grep", result.metadata().get("canonicalToolCode"));
        assertEquals("grep", result.metadata().get("requestedToolCode"));
    }
}
