package com.codingx.governance.application;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.governance.application.service.GovernanceAgentContextService;
import com.codingx.governance.application.service.LongTermMemoryService;
import com.codingx.governance.application.service.RepositoryInstructionContextService;
import com.codingx.governance.domain.model.GovernanceLongTermMemory;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证治理上下文服务会把仓库规范文件与长期记忆组装成可注入模型的系统上下文。
 */
@ExtendWith(MockitoExtension.class)
class GovernanceAgentContextServiceTest {

    @Mock
    private RepositoryInstructionContextService repositoryInstructionContextService;
    @Mock
    private LongTermMemoryService longTermMemoryService;

    /**
     * 存在仓库规范文件和命中的 ACTIVE 记忆时，应生成包含两类信息的上下文片段。
     */
    @Test
    void buildAgentContextShouldIncludeRepositoryInstructionsAndActiveMemories() {
        org.mockito.Mockito.when(repositoryInstructionContextService.buildInstructionContext(200L, 300L))
            .thenReturn("# 仓库规范文件\n## AGENTS.md\n提交信息必须使用中文");
        org.mockito.Mockito.when(longTermMemoryService.retrieveActiveMemories(200L, 300L, "请按业务注释规范修改代码", 6)).thenReturn(List.of(
            GovernanceLongTermMemory.builder()
                .id(10L)
                .memoryScope("USER")
                .userId(200L)
                .workspaceId(300L)
                .content("代码风格偏好：优先写清楚业务注释")
                .status("ACTIVE")
                .build()
        ));
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            GovernanceAgentContextService service = new GovernanceAgentContextService(
                repositoryInstructionContextService,
                longTermMemoryService,
                executorService
            );

            String context = service.buildAgentContext(200L, 300L, "请按业务注释规范修改代码");

            assertTrue(context.contains("仓库规范文件"));
            assertTrue(context.contains("提交信息必须使用中文"));
            assertTrue(context.contains("长期记忆"));
            assertTrue(context.contains("优先写清楚业务注释"));
            assertTrue(context.contains("不要逐字复述"));
            assertTrue(!context.contains("项目画像"));
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * 仓库规范文件读取和长期记忆检索互不依赖，应并行启动以减少每次普通模型回复前的上下文等待。
     */
    @Test
    void buildAgentContextShouldLoadRepositoryInstructionsAndMemoriesInParallel() {
        CountDownLatch startedLookups = new CountDownLatch(2);
        org.mockito.Mockito.when(repositoryInstructionContextService.buildInstructionContext(200L, 300L))
            .thenAnswer(invocation -> {
                startedLookups.countDown();
                assertTrue(startedLookups.await(1, TimeUnit.SECONDS), "长期记忆检索应与仓库规范读取并行启动");
                return "# 仓库规范文件\n## AGENTS.md\n提交信息必须使用中文";
            });
        org.mockito.Mockito.when(longTermMemoryService.retrieveActiveMemories(200L, 300L, "请帮我改一个功能", 6))
            .thenAnswer(invocation -> {
                startedLookups.countDown();
                assertTrue(startedLookups.await(1, TimeUnit.SECONDS), "仓库规范读取应与长期记忆检索并行启动");
                return List.of(
                    GovernanceLongTermMemory.builder()
                        .id(11L)
                        .memoryScope("PROJECT")
                        .userId(200L)
                        .workspaceId(300L)
                        .content("项目偏好：修改后补充测试")
                        .status("ACTIVE")
                        .build()
                );
            });
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            GovernanceAgentContextService service = new GovernanceAgentContextService(
                repositoryInstructionContextService,
                longTermMemoryService,
                executorService
            );

            String context = service.buildAgentContext(200L, 300L, "请帮我改一个功能");

            assertTrue(context.contains("提交信息必须使用中文"));
            assertTrue(context.contains("修改后补充测试"));
        } finally {
            executorService.shutdownNow();
        }
    }
}
