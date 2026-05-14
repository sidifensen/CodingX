package com.codingx.chat.infrastructure.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.annotation.TableName;
import java.lang.reflect.Method;
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
                "com.codingx.chat.domain.model.ChatIntentExample",
                "com.codingx.chat.domain.repository.ChatIntentExampleRepository",
                "com.codingx.chat.infrastructure.persistence.dataobject.ChatIntentExampleDO",
                "com.codingx.chat.infrastructure.persistence.mapper.ChatIntentExampleMapper",
                "com.codingx.chat.infrastructure.persistence.repository.ChatIntentExampleRepositoryImpl",
                "chat_intent_example",
                List.of("save", "findByIntentCode")
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
