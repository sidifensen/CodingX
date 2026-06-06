package com.codingx.governance.infrastructure;

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
        assertTrue(Files.exists(migrationPath), "缺少治理工作台迁移脚本");
        String migrationSql = Files.readString(migrationPath, StandardCharsets.UTF_8);
        String schemaSql = Files.readString(Path.of("src/main/resources/db/schema.sql"), StandardCharsets.UTF_8);

        for (String tableName : List.of(
            "governance_permission_policy",
            "governance_permission_audit",
            "governance_hook_rule",
            "governance_hook_audit",
            "governance_project_profile",
            "governance_slash_command"
        )) {
            assertTrue(migrationSql.contains("CREATE TABLE IF NOT EXISTS " + tableName), "迁移缺少表: " + tableName);
            assertTrue(schemaSql.contains("CREATE TABLE IF NOT EXISTS " + tableName), "schema 缺少表: " + tableName);
            assertTrue(migrationSql.contains("COMMENT ON TABLE " + tableName + " IS '"), "迁移缺少表注释: " + tableName);
            assertTrue(schemaSql.contains("COMMENT ON TABLE " + tableName + " IS '"), "schema 缺少表注释: " + tableName);
        }

        for (String comment : List.of(
            "COMMENT ON COLUMN governance_permission_policy.policy_code IS '策略编码'",
            "COMMENT ON COLUMN governance_permission_audit.decision IS '策略判定动作'",
            "COMMENT ON COLUMN governance_hook_rule.trigger_point IS 'Hook触发点'",
            "COMMENT ON COLUMN governance_project_profile.verification_commands_json IS '验证命令JSON'",
            "COMMENT ON COLUMN governance_slash_command.prompt_template IS '命令提示模板'"
        )) {
            assertTrue(migrationSql.contains(comment), "迁移缺少字段注释: " + comment);
            assertTrue(schemaSql.contains(comment), "schema 缺少字段注释: " + comment);
        }
    }
}
