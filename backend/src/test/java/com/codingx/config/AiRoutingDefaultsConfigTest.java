package com.codingx.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
     * application.yml 仅保留主密钥与静态模型候选骨架，不再承载 AI 默认 provider / API Key 等业务运行时默认值。
     * @throws IOException 读取配置文件失败时抛出。
     */
    @Test
    void applicationYamlKeepsOnlyStaticAiSkeleton() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(applicationYaml.contains("master-key: ${APP_CONFIG_MASTER_KEY:}"), "系统配置主密钥应保留在 application.yml 骨架中");
        assertTrue(!applicationYaml.contains("provider: ${AI_PROVIDER:siliconflow}"), "AI provider 运行时默认值不应继续留在 application.yml");
        assertTrue(!applicationYaml.contains("default-model:"), "默认聊天模型应收口到 AiProperties 默认值与系统配置表，不再写在 application.yml");
        assertTrue(!applicationYaml.contains("deep-thinking-model:"), "深度思考模型默认值应收口到 AiProperties 默认值与系统配置表");
        assertTrue(applicationYaml.contains("id: siliconflow-deepseek-v4-flash"), "候选池应包含 siliconflow-deepseek-v4-flash");
        assertTrue(applicationYaml.contains("id: siliconflow-qwen3.5-122b-a10b"), "候选池应包含 siliconflow-qwen3.5-122b-a10b 视觉候选");
        assertTrue(applicationYaml.contains("provider: siliconflow # 模型所属提供商"), "硅基流动候选应排在百炼候选之前");
        assertFalse(applicationYaml.contains("id: stub-chat"), "聊天候选池不应继续保留 stub-chat 兜底");
        assertFalse(applicationYaml.contains("base-url: stub://local"), "application.yml 不应继续声明 stub provider 运行时地址");
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
        assertTrue(initSql.contains("ai.providers.siliconflow.endpoints.chat'"), "初始化数据应包含硅基流动 chat endpoint 配置键");
        assertTrue(initSql.contains("ai.chat.candidates.10.id'"), "初始化数据应包含模型候选池扁平键");
        assertTrue(initSql.contains("ai.chat.candidates.10.provider'"), "初始化数据应包含候选 provider 扁平键");
        assertTrue(initSql.contains("ai.chat.candidates.10.supports_vision'"), "初始化数据应包含候选视觉能力扁平键");
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
                            "ai.chat.deep_thinking_model', 'siliconflow-deepseek-v4-flash-thinking'",
                            "ai.providers.siliconflow.endpoints.chat'",
                            "ai.chat.candidates.10.id'"
                        );
                    } catch (IOException exception) {
                        throw new IllegalStateException("读取迁移文件失败：" + path, exception);
                    }
                });

            assertTrue(found, "应存在一条迁移把 AI 路由默认值更新为 SiliconFlow DeepSeek");
        }
    }

    /**
     * 历史环境升级时必须补齐 provider 基础地址与密钥配置行，否则路由只剩模型 ID 却拿不到真实 provider 凭据。
     * @throws IOException 读取迁移文件失败时抛出。
     */
    @Test
    void migrationSeedsProviderRuntimeSettingsForExistingDatabases() throws IOException {
        try (Stream<Path> migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
            boolean found = migrations
                .filter(path -> path.getFileName().toString().endsWith(".sql"))
                .anyMatch(path -> {
                    try {
                        String content = Files.readString(path);
                        return containsAll(
                            content,
                            "ai.provider",
                            "ai.base_url",
                            "ai.api_key",
                            "ai.providers.siliconflow.base_url",
                            "ai.providers.siliconflow.api_key",
                            "ai.providers.bailian.base_url",
                            "ai.providers.bailian.api_key",
                            "ai.providers.deepseek.base_url",
                            "ai.providers.deepseek.api_key"
                        );
                    } catch (IOException exception) {
                        throw new IllegalStateException("读取迁移文件失败：" + path, exception);
                    }
                });

            assertTrue(found, "应存在一条迁移为历史数据库补齐 AI provider 运行时配置");
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
