package com.codingx.governance.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证 Hook 自动化迁移和基线结构只保留规则配置，不再依赖 Hook 审计表。
 */
class HookAutomationSchemaCompatibilityTest {

    /**
     * Hook 自动化迁移必须删除旧审计表、清理旧审计种子，并内置四个任务生命周期触发点。
     * @throws Exception 文件读取失败时抛出。
     */
    @Test
    void hookAutomationShouldRemoveAuditPersistenceAndSeedTaskLifecycleRules() throws Exception {
        Path initialGovernanceMigrationPath = Path.of(
            "src/main/resources/db/migration/V20260606_211000__create_governance_workbench_tables.sql"
        );
        Path hookAutomationMigrationPath = Path.of(
            "src/main/resources/db/migration/V20260608_174801__remove_hook_audit_and_seed_automation_hooks.sql"
        );
        assertTrue(Files.exists(initialGovernanceMigrationPath), "缺少治理工作台初始迁移脚本");
        assertTrue(Files.exists(hookAutomationMigrationPath), "缺少 Hook 自动化迁移脚本");

        String initialGovernanceMigrationSql = Files.readString(initialGovernanceMigrationPath, StandardCharsets.UTF_8);
        String hookAutomationMigrationSql = Files.readString(hookAutomationMigrationPath, StandardCharsets.UTF_8);
        String combinedMigrationSql = initialGovernanceMigrationSql + "\n" + hookAutomationMigrationSql;
        String schemaSql = Files.readString(Path.of("src/main/resources/db/schema.sql"), StandardCharsets.UTF_8);

        // 步骤 1：Hook 不再记录数据库审计流水，迁移和基线都不能重新创建 governance_hook_audit。
        assertFalse(
            combinedMigrationSql.contains("CREATE TABLE IF NOT EXISTS governance_hook_audit"),
            "迁移不应继续创建 Hook 审计表"
        );
        assertFalse(
            schemaSql.contains("CREATE TABLE IF NOT EXISTS governance_hook_audit"),
            "schema 不应继续创建 Hook 审计表"
        );
        assertTrue(
            hookAutomationMigrationSql.contains("DROP TABLE IF EXISTS governance_hook_audit"),
            "Hook 自动化迁移缺少审计表删除语句"
        );

        // 步骤 2：既有环境升级时必须清理旧审计种子，避免管理端继续展示 AUDIT 动作。
        assertTrue(
            hookAutomationMigrationSql.contains("DELETE FROM governance_hook_rule")
                && hookAutomationMigrationSql.contains("audit-before-tool")
                && hookAutomationMigrationSql.contains("audit-task-complete"),
            "Hook 自动化迁移必须清理旧审计种子规则"
        );

        // 步骤 3：内置 Hook 覆盖任务开始、需要确认、失败和完成，供桌面通知或宠物联动消费。
        for (String triggerPoint : List.of(
            "BEFORE_TASK_START",
            "TASK_CONFIRM_REQUIRED",
            "TASK_FAILED",
            "TASK_COMPLETED"
        )) {
            assertTrue(combinedMigrationSql.contains(triggerPoint), "迁移缺少内置 Hook 触发点: " + triggerPoint);
            assertTrue(schemaSql.contains(triggerPoint), "schema 缺少内置 Hook 触发点: " + triggerPoint);
        }
        assertTrue(combinedMigrationSql.contains("DESKTOP_NOTIFY"), "迁移缺少桌面通知动作类型");
        assertTrue(schemaSql.contains("DESKTOP_NOTIFY"), "schema 缺少桌面通知动作类型");
        assertFalse(schemaSql.contains("'AUDIT'"), "schema 不应继续内置 AUDIT Hook 动作");
    }
}
