package com.codingx.chat.infrastructure.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 验证当前项目已经吸收意图 SQL 核心种子数据，避免后续回退到旧示例集合。
 */
class ChatIntentSeedScriptTest {

    /**
     * 初始化脚本和迁移脚本都应包含 ticket、weather 与 sys-feedback 等核心节点。
     * @throws IOException 读取脚本失败时抛出。
     */
    @Test
    void seedScriptsContainIntentSeedData() throws IOException {
        String initSql = Files.readString(Path.of("src/main/resources/db/init.sql"));
        String migrationSql = Files.readString(Path.of("src/main/resources/db/migration/V20260516_112500__sync_intent_seed.sql"));
        String toolMigrationSql = Files.readString(Path.of("src/main/resources/db/migration/V20260517_235600__seed_chat_tool_from_codex_cli.sql"));

        for (String marker : new String[] {
            "'ticket'", "'ticket-data'", "'weather'", "'weather-data'",
            "'code'", "'code-search'", "'code_search'",
            "'shell_command'", "'apply_patch'", "'update_plan'",
            "'sys-feedback'", "企业内部知识助手「小码」"
        }) {
            assertTrue(initSql.contains(marker), "init.sql 缺少种子标记: " + marker);
            if (!"'shell_command'".equals(marker) && !"'apply_patch'".equals(marker) && !"'update_plan'".equals(marker)) {
                assertTrue(migrationSql.contains(marker), "migration 缺少种子标记: " + marker);
            } else {
                assertTrue(toolMigrationSql.contains(marker), "tool migration 缺少种子标记: " + marker);
            }
        }
    }
}
