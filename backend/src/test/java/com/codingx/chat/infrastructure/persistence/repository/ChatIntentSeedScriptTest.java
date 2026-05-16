package com.codingx.chat.infrastructure.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 验证当前项目已经吸收 ragent 意图 SQL 的核心种子数据，避免后续回退到旧示例集合。
 */
class ChatIntentSeedScriptTest {

    /**
     * 初始化脚本和迁移脚本都应包含 ticket、weather 与 sys-feedback 等 ragent 节点。
     * @throws IOException 读取脚本失败时抛出。
     */
    @Test
    void seedScriptsContainRagentIntentData() throws IOException {
        String initSql = Files.readString(Path.of("src/main/resources/db/init.sql"));
        String migrationSql = Files.readString(Path.of("src/main/resources/db/migration/V20260516_112500__sync_ragent_intent_seed.sql"));

        for (String marker : new String[] {"'ticket'", "'ticket-data'", "'weather'", "'weather-data'", "'sys-feedback'", "企业内部知识助手「小码」"}) {
            assertTrue(initSql.contains(marker), "init.sql 缺少种子标记: " + marker);
            assertTrue(migrationSql.contains(marker), "migration 缺少种子标记: " + marker);
        }
    }
}
