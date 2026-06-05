package com.codingx.chat.infrastructure.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.annotation.TableName;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;

/**
 * 验证聊天运行时新增持久化骨架已经按分层约定补齐。
 */
class ChatRuntimePersistenceStructureTest {

    /**
     * 运行时新增表必须同时具备领域、DO、Mapper 与 Repository 骨架。
     * @throws Exception 目标类缺失或结构不符合约定时抛出。
     */
    @Test
    void runtimePersistenceSkeletonsExistForAllNewTables() throws Exception {
        for (PersistenceSkeleton skeleton : expectedSkeletons()) {
            assertRepositorySkeleton(skeleton);
        }
    }

    /**
     * 管理端意图树需要在 DO 层暴露扩展字段，避免数据库迁移已存在但映射层不可用。
     * @throws Exception 目标 DO 类或字段缺失时抛出。
     */
    @Test
    void chatIntentNodeDataObjectContainsIntentAdminFields() throws Exception {
        Class<?> dataObjectClass = Class.forName("com.codingx.chat.infrastructure.persistence.dataobject.ChatIntentNodeDO");
        List<String> fieldNames = List.of(dataObjectClass.getDeclaredFields()).stream().map(Field::getName).toList();

        for (String expectedField : List.of("kbId", "level", "examples", "collectionName", "topK", "kind", "promptSnippet", "sortOrder")) {
            assertTrue(fieldNames.contains(expectedField), "Missing ChatIntentNodeDO field: " + expectedField);
        }
    }

    /**
     * Codex 工具运行时迁移必须启用已在 Java 后端接入的进程内工具，避免历史数据库沿用旧禁用状态。
     *
     * @throws Exception 迁移脚本缺失或内容不符合约定时抛出。
     */
    @Test
    void codexRuntimeToolMigrationEnablesImplementedTools() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V20260529_020000__enable_codex_runtime_tools.sql");
        assertTrue(Files.exists(migration), "缺少 Codex 运行时工具启用迁移脚本");
        String sql = Files.readString(migration, StandardCharsets.UTF_8);

        for (String toolCode : List.of("spawn_agent", "send_input", "wait_agent", "close_agent", "resume_agent", "request_permissions")) {
            assertTrue(sql.contains(toolCode), "迁移脚本应覆盖已接入工具：" + toolCode);
        }
        assertTrue(sql.contains("enabled = 1") || sql.contains("enabled=1"), "已接入工具必须启用");
        assertTrue(sql.contains("updated_at = CURRENT_TIMESTAMP"), "迁移应更新时间戳便于排查配置来源");
    }

    /**
     * 终端工具描述必须明确 Windows PowerShell 语法边界，避免模型按 Bash 习惯生成无法执行的命令。
     *
     * @throws Exception 迁移脚本缺失或内容不符合约定时抛出。
     */
    @Test
    void shellCommandRuntimeMigrationClarifiesPowerShellSyntax() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V20260527_153000__clarify_shell_command_windows_runtime.sql");
        assertTrue(Files.exists(migration), "缺少终端工具 PowerShell 语法说明迁移脚本");
        String sql = Files.readString(migration, StandardCharsets.UTF_8);

        assertTrue(sql.contains("shell_command"), "迁移脚本应覆盖 shell_command");
        assertTrue(sql.contains("Windows PowerShell"), "迁移脚本应说明 Windows PowerShell 运行时");
        assertTrue(sql.contains("mkdir -p"), "迁移脚本应提示 Bash mkdir -p 写法不适用");
        assertTrue(sql.contains("Set-Content"), "迁移脚本应给出 PowerShell 文件写入提示");
        assertTrue(sql.contains("apply_patch"), "迁移脚本应提示文件编辑优先使用 apply_patch");
    }

    /**
     * 歧义引导运行时参数必须进入系统配置数据，管理端才能按中文说明维护开关与阈值。
     *
     * @throws Exception 迁移脚本或初始化数据缺失时抛出。
     */
    @Test
    void chatIntentGuidanceRuntimeSettingsAreSeeded() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V20260527_200000__add_chat_intent_guidance_settings.sql");
        assertTrue(Files.exists(migration), "缺少聊天歧义引导运行时配置迁移脚本");
        String migrationSql = Files.readString(migration, StandardCharsets.UTF_8);
        String initSql = Files.readString(Path.of("src/main/resources/db/init.sql"), StandardCharsets.UTF_8);

        for (String sql : List.of(migrationSql, initSql)) {
            assertTrue(sql.contains("chat.intent.guidance.enabled"), "必须写入歧义引导开关配置");
            assertTrue(sql.contains("chat.intent.guidance.ambiguity_score_ratio"), "必须写入歧义分数比值阈值配置");
            assertTrue(sql.contains("chat.intent.guidance.ambiguity_margin"), "必须写入歧义边界缓冲配置");
            assertTrue(sql.contains("chat.intent.guidance.max_options"), "必须写入歧义候选数量配置");
            assertTrue(sql.contains("是否启用聊天歧义引导"), "配置说明必须为中文");
            assertTrue(sql.contains("DECIMAL"), "比例和边界配置必须声明为 DECIMAL 类型");
        }
    }

    /**
     * 聊天运行状态只能由 chat_execution_run 承担，基线结构不得再保留旧任务表定义。
     *
     * @throws Exception schema.sql 缺失或内容不符合运行时表契约时抛出。
     */
    @Test
    void schemaUsesChatExecutionRunWithoutLegacyTaskTables() throws Exception {
        String schemaSql = Files.readString(Path.of("src/main/resources/db/schema.sql"), StandardCharsets.UTF_8);

        assertTrue(
            schemaSql.contains("CREATE TABLE IF NOT EXISTS chat_execution_run ("),
            "聊天运行状态权威表 chat_execution_run 必须保留"
        );
        assertFalse(
            schemaSql.contains("CREATE TABLE IF NOT EXISTS task ("),
            "schema.sql 不应再定义旧 task 表"
        );
        assertFalse(
            schemaSql.contains("CREATE TABLE IF NOT EXISTS task_expert ("),
            "schema.sql 不应再定义旧 task_expert 表"
        );
    }

    /**
     * 校验单张表的持久化骨架结构完整性。
     * @param skeleton 骨架定义。
     * @throws Exception 目标类缺失或结构不符合约定时抛出。
     */
    private void assertRepositorySkeleton(PersistenceSkeleton skeleton) throws Exception {
        Class<?> domainClass = Class.forName(skeleton.domainClassName());
        Class<?> repositoryInterface = Class.forName(skeleton.repositoryInterfaceName());
        Class<?> dataObjectClass = Class.forName(skeleton.dataObjectClassName());
        Class<?> mapperClass = Class.forName(skeleton.mapperClassName());
        Class<?> repositoryImplClass = Class.forName(skeleton.repositoryImplClassName());

        assertNotNull(domainClass);
        assertTrue(repositoryInterface.isInterface());
        assertEquals(repositoryInterface, repositoryImplClass.getInterfaces()[0]);
        assertNotNull(repositoryImplClass.getAnnotation(Repository.class));
        assertNotNull(mapperClass.getAnnotation(Mapper.class));

        TableName tableName = dataObjectClass.getAnnotation(TableName.class);
        assertNotNull(tableName);
        assertEquals(skeleton.tableName(), tableName.value());

        List<String> methodNames = List.of(repositoryInterface.getDeclaredMethods()).stream().map(Method::getName).toList();
        for (String expectedMethod : skeleton.repositoryMethods()) {
            assertTrue(methodNames.contains(expectedMethod), "Missing repository method: " + expectedMethod);
        }
    }

    /**
     * 定义本阶段必须补齐的新增持久化骨架列表。
     * @return 期望骨架集合。
     */
    private List<PersistenceSkeleton> expectedSkeletons() {
        return List.of(
            new PersistenceSkeleton(
                "com.codingx.chat.domain.model.ChatConversationSummary",
                "com.codingx.chat.domain.repository.ChatConversationSummaryRepository",
                "com.codingx.chat.infrastructure.persistence.dataobject.ChatConversationSummaryDO",
                "com.codingx.chat.infrastructure.persistence.mapper.ChatConversationSummaryMapper",
                "com.codingx.chat.infrastructure.persistence.repository.ChatConversationSummaryRepositoryImpl",
                "chat_conversation_summary",
                List.of("save", "findLatestByConversationId")
            ),
            new PersistenceSkeleton(
                "com.codingx.chat.domain.model.ChatMessageFeedback",
                "com.codingx.chat.domain.repository.ChatMessageFeedbackRepository",
                "com.codingx.chat.infrastructure.persistence.dataobject.ChatMessageFeedbackDO",
                "com.codingx.chat.infrastructure.persistence.mapper.ChatMessageFeedbackMapper",
                "com.codingx.chat.infrastructure.persistence.repository.ChatMessageFeedbackRepositoryImpl",
                "chat_message_feedback",
                List.of("save", "findByMessageIdAndUserId")
            ),
            new PersistenceSkeleton(
                "com.codingx.chat.domain.model.ChatExecutionRun",
                "com.codingx.chat.domain.repository.ChatExecutionRunRepository",
                "com.codingx.chat.infrastructure.persistence.dataobject.ChatExecutionRunDO",
                "com.codingx.chat.infrastructure.persistence.mapper.ChatExecutionRunMapper",
                "com.codingx.chat.infrastructure.persistence.repository.ChatExecutionRunRepositoryImpl",
                "chat_execution_run",
                List.of("save", "findByConversationId")
            ),
            new PersistenceSkeleton(
                "com.codingx.chat.domain.model.ChatExecutionStep",
                "com.codingx.chat.domain.repository.ChatExecutionStepRepository",
                "com.codingx.chat.infrastructure.persistence.dataobject.ChatExecutionStepDO",
                "com.codingx.chat.infrastructure.persistence.mapper.ChatExecutionStepMapper",
                "com.codingx.chat.infrastructure.persistence.repository.ChatExecutionStepRepositoryImpl",
                "chat_execution_step",
                List.of("save", "findByRunId")
            ),
            new PersistenceSkeleton(
                "com.codingx.chat.domain.model.ChatMessageReference",
                "com.codingx.chat.domain.repository.ChatMessageReferenceRepository",
                "com.codingx.chat.infrastructure.persistence.dataobject.ChatMessageReferenceDO",
                "com.codingx.chat.infrastructure.persistence.mapper.ChatMessageReferenceMapper",
                "com.codingx.chat.infrastructure.persistence.repository.ChatMessageReferenceRepositoryImpl",
                "chat_message_reference",
                List.of("save", "findByRunId")
            ),
            new PersistenceSkeleton(
                "com.codingx.chat.domain.model.ChatMessageArtifact",
                "com.codingx.chat.domain.repository.ChatMessageArtifactRepository",
                "com.codingx.chat.infrastructure.persistence.dataobject.ChatMessageArtifactDO",
                "com.codingx.chat.infrastructure.persistence.mapper.ChatMessageArtifactMapper",
                "com.codingx.chat.infrastructure.persistence.repository.ChatMessageArtifactRepositoryImpl",
                "chat_message_artifact",
                List.of("save", "findByRunId")
            ),
            new PersistenceSkeleton(
                "com.codingx.chat.domain.model.ChatIntentNode",
                "com.codingx.chat.domain.repository.ChatIntentNodeRepository",
                "com.codingx.chat.infrastructure.persistence.dataobject.ChatIntentNodeDO",
                "com.codingx.chat.infrastructure.persistence.mapper.ChatIntentNodeMapper",
                "com.codingx.chat.infrastructure.persistence.repository.ChatIntentNodeRepositoryImpl",
                "chat_intent_node",
                List.of("save", "findEnabledNodes")
            ),
            new PersistenceSkeleton(
                "com.codingx.chat.domain.model.ChatTraceRun",
                "com.codingx.chat.domain.repository.ChatTraceRunRepository",
                "com.codingx.chat.infrastructure.persistence.dataobject.ChatTraceRunDO",
                "com.codingx.chat.infrastructure.persistence.mapper.ChatTraceRunMapper",
                "com.codingx.chat.infrastructure.persistence.repository.ChatTraceRunRepositoryImpl",
                "chat_trace_run",
                List.of("save", "findByTraceId")
            ),
            new PersistenceSkeleton(
                "com.codingx.chat.domain.model.ChatTraceNode",
                "com.codingx.chat.domain.repository.ChatTraceNodeRepository",
                "com.codingx.chat.infrastructure.persistence.dataobject.ChatTraceNodeDO",
                "com.codingx.chat.infrastructure.persistence.mapper.ChatTraceNodeMapper",
                "com.codingx.chat.infrastructure.persistence.repository.ChatTraceNodeRepositoryImpl",
                "chat_trace_node",
                List.of("save", "findByTraceId")
            )
        );
    }

    /**
     * 汇总单张表的持久化骨架约定。
     * @param domainClassName 领域模型类名。
     * @param repositoryInterfaceName 仓储接口类名。
     * @param dataObjectClassName 数据对象类名。
     * @param mapperClassName Mapper 类名。
     * @param repositoryImplClassName 仓储实现类名。
     * @param tableName 对应表名。
     * @param repositoryMethods 仓储接口必须暴露的方法名。
     */
    private record PersistenceSkeleton(
        String domainClassName,
        String repositoryInterfaceName,
        String dataObjectClassName,
        String mapperClassName,
        String repositoryImplClassName,
        String tableName,
        List<String> repositoryMethods
    ) {
    }
}
