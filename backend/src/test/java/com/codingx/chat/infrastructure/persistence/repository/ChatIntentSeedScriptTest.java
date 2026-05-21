package com.codingx.chat.infrastructure.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 验证当前项目已经吸收意图 SQL 核心种子数据，避免后续回退到旧示例集合。
 */
class ChatIntentSeedScriptTest {

    /**
     * 初始化脚本应包含核心在线能力节点，并且不再回填已下线的销售与工单相关意图/MCP。
     * @throws IOException 读取脚本失败时抛出。
     */
    @Test
    void seedScriptsContainIntentSeedData() throws IOException {
        String initSql = Files.readString(Path.of("src/main/resources/db/init.sql"));
        String migrationSql = Files.readString(Path.of("src/main/resources/db/migration/V20260516_112500__sync_intent_seed.sql"));
        String toolMigrationSql = Files.readString(Path.of("src/main/resources/db/migration/V20260517_235600__seed_tool_from_codex_cli.sql"));
        String cleanupMigrationSql = Files.readString(
            Path.of("src/main/resources/db/migration/V20260522_023500__physical_remove_sales_and_ticket_mcps_and_intents.sql")
        );

        for (String marker : new String[] {
            "'weather'", "'weather-data'",
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

        for (String removedMarker : new String[] {"'sales'", "'sales-data'", "'ticket'", "'ticket-data'"}) {
            assertFalse(initSql.contains(removedMarker), "init.sql 不应再包含已下线意图: " + removedMarker);
        }
        for (String removedMcp : new String[] {"'sales_query'", "'ticket_query'"}) {
            assertFalse(initSql.contains(removedMcp), "init.sql 不应再包含已下线 MCP: " + removedMcp);
            assertTrue(cleanupMigrationSql.contains(removedMcp), "清理迁移应覆盖已下线 MCP: " + removedMcp);
        }
    }
}
