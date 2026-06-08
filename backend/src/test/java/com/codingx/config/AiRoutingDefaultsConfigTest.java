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
 * 验证 AI 路由默认顺序只由候选池优先级维护，避免默认模型指针重新进入运行时配置。
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
        assertFalse(applicationYaml.contains("id: siliconflow-deepseek-v4-flash"), "AI 候选池应由数据库初始化数据维护，不应继续散落在 YAML");
        assertFalse(applicationYaml.contains("id: siliconflow-qwen3.5-122b-a10b"), "视觉候选也应由数据库初始化数据维护");
        assertFalse(applicationYaml.contains("provider: siliconflow # 模型所属提供商"), "provider 候选优先级不应继续写死在 YAML");
        assertFalse(applicationYaml.contains("id: stub-chat"), "聊天候选池不应继续保留 stub-chat 兜底");
        assertFalse(applicationYaml.contains("base-url: stub://local"), "application.yml 不应继续声明 stub provider 运行时地址");
    }

    /**
     * init.sql 必须只写入候选池默认顺序，不再写入默认模型指针。
     * @throws IOException 读取 SQL 文件失败时抛出。
     */
    @Test
    void initSqlSeedsCandidatePoolWithoutDefaultModelPointers() throws IOException {
        String initSql = Files.readString(Path.of("src/main/resources/db/init.sql"));

        assertFalse(initSql.contains("ai.chat.default_model"), "初始化数据不应继续写入普通默认模型指针");
        assertFalse(initSql.contains("ai.chat.deep_thinking_model"), "初始化数据不应继续写入深度思考默认模型指针");
        assertTrue(initSql.contains("ai.providers.siliconflow.endpoints.chat'"), "初始化数据应包含硅基流动 chat endpoint 配置键");
        assertTrue(initSql.contains("ai.chat.candidates.10.id'"), "初始化数据应包含模型候选池扁平键");
        assertTrue(initSql.contains("ai.chat.candidates.10.provider'"), "初始化数据应包含候选 provider 扁平键");
        assertTrue(initSql.contains("ai.chat.candidates.10.supports_vision'"), "初始化数据应包含候选视觉能力扁平键");
        assertTrue(initSql.contains("ai.selection.first_packet_timeout_ms', '15000'"), "初始化数据应把首包等待窗口控制在 15 秒");
    }

    /**
     * 至少有一条迁移必须清理历史环境里的 AI 默认模型指针，避免已存在数据库继续暴露旧配置入口。
     * @throws IOException 读取迁移文件失败时抛出。
     */
    @Test
    void migrationRemovesExistingDefaultModelPointers() throws IOException {
        try (Stream<Path> migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
            boolean found = migrations
                .filter(path -> path.getFileName().toString().endsWith(".sql"))
                .anyMatch(path -> {
                    try {
                        String content = Files.readString(path);
                        return containsAll(
                            content,
                            "ai.chat.default_model",
                            "ai.chat.deep_thinking_model",
                            "deleted = 1"
                        );
                    } catch (IOException exception) {
                        throw new IllegalStateException("读取迁移文件失败：" + path, exception);
                    }
                });

            assertTrue(found, "应存在一条迁移删除 AI 默认模型指针");
        }
    }

    /**
     * 历史环境升级时必须补齐 provider endpoint 与候选池键位，候选 priority 默认顺序才能解析到真实 provider。
     * @throws IOException 读取迁移文件失败时抛出。
     */
    @Test
    void migrationSeedsAiEndpointAndCandidateSettingsForExistingDatabases() throws IOException {
        try (Stream<Path> migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
            boolean found = migrations
                .filter(path -> path.getFileName().toString().endsWith(".sql"))
                .anyMatch(path -> {
                    try {
                        String content = Files.readString(path);
                        return containsAll(
                            content,
                            "ai.providers.siliconflow.endpoints.chat'",
                            "ai.chat.candidates.10.id'",
                            "ai.chat.candidates.10.provider'",
                            "ai.chat.candidates.10.supports_vision'"
                        );
                    } catch (IOException exception) {
                        throw new IllegalStateException("读取迁移文件失败：" + path, exception);
                    }
                });

            assertTrue(found, "应存在一条迁移为历史数据库补齐 AI endpoint 与候选池配置");
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
     * 历史环境升级时必须把首包等待窗口降到 15 秒，避免慢 provider 长时间占住用户请求。
     * @throws IOException 读取迁移文件失败时抛出。
     */
    @Test
    void migrationReducesFirstPacketTimeoutForExistingDatabases() throws IOException {
        try (Stream<Path> migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
            boolean found = migrations
                .filter(path -> path.getFileName().toString().endsWith(".sql"))
                .anyMatch(path -> {
                    try {
                        String content = Files.readString(path);
                        return containsAll(
                            content,
                            "ai.selection.first_packet_timeout_ms",
                            "'15000'"
                        );
                    } catch (IOException exception) {
                        throw new IllegalStateException("读取迁移文件失败：" + path, exception);
                    }
                });

            assertTrue(found, "应存在一条迁移把首包等待窗口更新为 15 秒");
        }
    }

    /**
     * 历史消息中的 openai-compatible 只是协议适配器，升级时应按唯一模型名映射回填真实候选 provider。
     * @throws IOException 读取迁移文件失败时抛出。
     */
    @Test
    void migrationBackfillsOpenAiCompatibleChatMessageProviderFromUniqueCandidateModel() throws IOException {
        try (Stream<Path> migrations = Files.list(Path.of("src/main/resources/db/migration"))) {
            boolean found = migrations
                .filter(path -> path.getFileName().toString().endsWith(".sql"))
                .anyMatch(path -> {
                    try {
                        String content = Files.readString(path);
                        return containsAll(
                            content,
                            "chat_message",
                            "openai-compatible",
                            "candidate_provider",
                            "HAVING COUNT(DISTINCT provider_value.setting_value) = 1"
                        );
                    } catch (IOException exception) {
                        throw new IllegalStateException("读取迁移文件失败：" + path, exception);
                    }
                });

            assertTrue(found, "应存在一条迁移按唯一候选模型回填历史消息真实 provider");
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
