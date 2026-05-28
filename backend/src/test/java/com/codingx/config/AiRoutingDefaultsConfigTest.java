package com.codingx.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 验证 AI 路由默认值优先落到 DeepSeek，避免新环境或升级后继续回到百炼默认模型。
 */
class AiRoutingDefaultsConfigTest {

    /**
     * application.yml 必须把 DeepSeek 作为默认 AI provider 和默认聊天模型。
     * @throws IOException 读取配置文件失败时抛出。
     */
    @Test
    void applicationYamlPrefersDeepSeekByDefault() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(applicationYaml.contains("provider: ${AI_PROVIDER:deepseek}"), "AI provider 默认值应为 deepseek");
        assertTrue(applicationYaml.contains("default-model: ${AI_DEFAULT_MODEL:deepseek-chat}"), "默认聊天模型应优先使用 deepseek-chat");
        assertTrue(applicationYaml.contains("deep-thinking-model: ${AI_DEEP_THINKING_MODEL:deepseek-reasoner}"), "深度思考模型应优先使用 deepseek-reasoner");
        assertTrue(applicationYaml.contains("id: deepseek-chat"), "候选池应包含 deepseek-chat");
        assertTrue(applicationYaml.contains("id: deepseek-reasoner"), "候选池应包含 deepseek-reasoner");
    }

    /**
     * init.sql 必须把默认运行时配置写成 DeepSeek，保证全新数据库初始化后立即生效。
     * @throws IOException 读取 SQL 文件失败时抛出。
     */
    @Test
    void initSqlSeedsDeepSeekRuntimeDefaults() throws IOException {
        String initSql = Files.readString(Path.of("src/main/resources/db/init.sql"));

        assertTrue(initSql.contains("ai.chat.default_model', 'deepseek-chat'"), "初始化数据应默认写入 deepseek-chat");
        assertTrue(initSql.contains("ai.chat.deep_thinking_model', 'deepseek-reasoner'"), "初始化数据应默认写入 deepseek-reasoner");
    }

    /**
     * 至少有一条迁移必须覆盖历史环境里的 AI 路由默认值，避免已存在数据库继续停留在 qwen 默认值。
     * @throws IOException 读取迁移文件失败时抛出。
     */
    @Test
    void migrationUpdatesExistingRuntimeDefaultsToDeepSeek() throws IOException {
        try (Stream<Path> migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
            boolean found = migrations
                .filter(path -> path.getFileName().toString().endsWith(".sql"))
                .anyMatch(path -> {
                    try {
                        String content = Files.readString(path);
                        return containsAll(
                            content,
                            "ai.chat.default_model', 'deepseek-chat'",
                            "ai.chat.deep_thinking_model', 'deepseek-reasoner'"
                        );
                    } catch (IOException exception) {
                        throw new IllegalStateException("读取迁移文件失败：" + path, exception);
                    }
                });

            assertTrue(found, "应存在一条迁移把 AI 路由默认值更新为 DeepSeek");
        }
    }

    /**
     * 判断文本是否同时包含所有指定片段。
     * @param content 待检查文本。
     * @param snippets 需要命中的片段。
     * @return 是否全部命中。
     */
    private boolean containsAll(String content, String... snippets) {
        return Arrays.stream(snippets).allMatch(content::contains);
    }
}
