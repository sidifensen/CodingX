package com.codingx.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 验证 AI 路由默认值优先落到 SiliconFlow 的 DeepSeek / 千问候选，避免新环境或升级后继续回到百炼默认模型。
 */
class AiRoutingDefaultsConfigTest {

    /**
     * application.yml 必须把 SiliconFlow 作为默认 AI provider，并将默认聊天模型切到 SiliconFlow 的 DeepSeek。
     * @throws IOException 读取配置文件失败时抛出。
     */
    @Test
    void applicationYamlPrefersSiliconFlowDeepSeekByDefault() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(applicationYaml.contains("provider: ${AI_PROVIDER:siliconflow}"), "AI provider 默认值应为 siliconflow");
        assertTrue(applicationYaml.contains("default-model: ${AI_DEFAULT_MODEL:siliconflow-deepseek-v4-flash}"), "默认聊天模型应优先使用 siliconflow-deepseek-v4-flash");
        assertTrue(applicationYaml.contains("deep-thinking-model: ${AI_DEEP_THINKING_MODEL:siliconflow-deepseek-v4-flash-thinking}"), "深度思考模型应优先使用 siliconflow-deepseek-v4-flash-thinking");
        assertTrue(applicationYaml.contains("id: siliconflow-deepseek-v4-flash"), "候选池应包含 siliconflow-deepseek-v4-flash");
        assertTrue(applicationYaml.contains("id: siliconflow-qwen3.5-122b-a10b"), "候选池应包含 siliconflow-qwen3.5-122b-a10b 视觉候选");
        assertTrue(applicationYaml.contains("provider: siliconflow # 模型所属提供商"), "硅基流动候选应排在百炼候选之前");
    }

    /**
     * init.sql 必须把默认运行时配置写成 SiliconFlow 的 DeepSeek，保证全新数据库初始化后立即生效。
     * @throws IOException 读取 SQL 文件失败时抛出。
     */
    @Test
    void initSqlSeedsSiliconFlowRuntimeDefaults() throws IOException {
        String initSql = Files.readString(Path.of("src/main/resources/db/init.sql"));

        assertTrue(initSql.contains("ai.chat.default_model', 'siliconflow-deepseek-v4-flash'"), "初始化数据应默认写入 siliconflow-deepseek-v4-flash");
        assertTrue(initSql.contains("ai.chat.deep_thinking_model', 'siliconflow-deepseek-v4-flash-thinking'"), "初始化数据应默认写入 siliconflow-deepseek-v4-flash-thinking");
    }

    /**
     * 至少有一条迁移必须覆盖历史环境里的 AI 路由默认值，避免已存在数据库继续停留在旧的 DeepSeek/百炼默认值。
     * @throws IOException 读取迁移文件失败时抛出。
     */
    @Test
    void migrationUpdatesExistingRuntimeDefaultsToSiliconFlowDeepSeek() throws IOException {
        try (Stream<Path> migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
            boolean found = migrations
                .filter(path -> path.getFileName().toString().endsWith(".sql"))
                .anyMatch(path -> {
                    try {
                        String content = Files.readString(path);
                        return containsAll(
                            content,
                            "ai.chat.default_model', 'siliconflow-deepseek-v4-flash'",
                            "ai.chat.deep_thinking_model', 'siliconflow-deepseek-v4-flash-thinking'"
                        );
                    } catch (IOException exception) {
                        throw new IllegalStateException("读取迁移文件失败：" + path, exception);
                    }
                });

            assertTrue(found, "应存在一条迁移把 AI 路由默认值更新为 SiliconFlow DeepSeek");
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
