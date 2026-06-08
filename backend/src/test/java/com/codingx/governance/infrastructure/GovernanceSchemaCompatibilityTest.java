package com.codingx.governance.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证治理工作台新增表在迁移脚本与基线 schema 中保持一致。
 */
class GovernanceSchemaCompatibilityTest {

    /**
     * 治理表必须同时出现在迁移脚本与 schema.sql，并保留中文表字段注释。
     * @throws Exception 文件读取失败时抛出。
     */
    @Test
    void governanceTablesShouldExistInMigrationAndSchemaWithChineseComments() throws Exception {
        Path migrationPath = Path.of("src/main/resources/db/migration/V20260606_211000__create_governance_workbench_tables.sql");
        Path memoryMigrationPath = Path.of("src/main/resources/db/migration/V20260607_011500__project_profile_long_term_memory.sql");
        Path profileUpsertMigrationPath = Path.of("src/main/resources/db/migration/V20260608_123000__deduplicate_project_profile.sql");
        Path dropProjectProfileMigrationPath = Path.of("src/main/resources/db/migration/V20260608_181525__drop_governance_project_profile.sql");
        assertTrue(Files.exists(migrationPath), "缺少治理工作台迁移脚本");
        assertTrue(Files.exists(memoryMigrationPath), "缺少项目画像与长期记忆迁移脚本");
        assertTrue(Files.exists(profileUpsertMigrationPath), "缺少项目画像去重迁移脚本");
        assertTrue(Files.exists(dropProjectProfileMigrationPath), "缺少项目画像删除迁移脚本");
        String migrationSql = Files.readString(migrationPath, StandardCharsets.UTF_8);
        String memoryMigrationSql = Files.readString(memoryMigrationPath, StandardCharsets.UTF_8);
        String profileUpsertMigrationSql = Files.readString(profileUpsertMigrationPath, StandardCharsets.UTF_8);
        String dropProjectProfileMigrationSql = Files.readString(dropProjectProfileMigrationPath, StandardCharsets.UTF_8);
        String combinedMigrationSql = migrationSql + "\n" + memoryMigrationSql + "\n" + profileUpsertMigrationSql
            + "\n" + dropProjectProfileMigrationSql;
        String schemaSql = Files.readString(Path.of("src/main/resources/db/schema.sql"), StandardCharsets.UTF_8);

        for (String tableName : List.of(
            "governance_permission_policy",
            "governance_permission_audit",
            "governance_hook_rule",
            "governance_long_term_memory",
            "governance_slash_command"
        )) {
            assertTrue(combinedMigrationSql.contains("CREATE TABLE IF NOT EXISTS " + tableName), "迁移缺少表: " + tableName);
            assertTrue(schemaSql.contains("CREATE TABLE IF NOT EXISTS " + tableName), "schema 缺少表: " + tableName);
            assertTrue(combinedMigrationSql.contains("COMMENT ON TABLE " + tableName + " IS '"), "迁移缺少表注释: " + tableName);
            assertTrue(schemaSql.contains("COMMENT ON TABLE " + tableName + " IS '"), "schema 缺少表注释: " + tableName);
        }

        // 项目画像已下线：历史迁移保留创建记录，最新迁移负责删除；基线 schema 不再创建该表和索引。
        assertTrue(combinedMigrationSql.contains("DROP TABLE IF EXISTS governance_project_profile"), "迁移缺少项目画像表删除语句");
        assertFalse(schemaSql.contains("CREATE TABLE IF NOT EXISTS governance_project_profile"), "schema 不应继续创建项目画像表");
        assertFalse(schemaSql.contains("uk_governance_project_profile_workspace_active"), "schema 不应继续保留项目画像唯一索引");

        for (String comment : List.of(
            "COMMENT ON COLUMN governance_permission_policy.policy_code IS '策略编码'",
            "COMMENT ON COLUMN governance_permission_audit.decision IS '策略判定动作'",
            "COMMENT ON COLUMN governance_hook_rule.trigger_point IS 'Hook触发点'",
            "COMMENT ON COLUMN governance_long_term_memory.memory_scope IS '记忆范围'",
            "COMMENT ON COLUMN governance_long_term_memory.status IS '记忆状态'",
            "COMMENT ON COLUMN governance_slash_command.prompt_template IS '命令提示模板'"
        )) {
            assertTrue(combinedMigrationSql.contains(comment), "迁移缺少字段注释: " + comment);
            assertTrue(schemaSql.contains(comment), "schema 缺少字段注释: " + comment);
        }

    }
}
