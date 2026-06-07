package com.codingx.governance.application;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.governance.application.service.GovernanceAgentContextService;
import com.codingx.governance.application.service.LongTermMemoryService;
import com.codingx.governance.application.service.ProjectProfileService;
import com.codingx.governance.domain.model.GovernanceLongTermMemory;
import com.codingx.governance.domain.model.GovernanceProjectProfile;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证治理上下文服务会把项目画像与长期记忆组装成可注入模型的系统上下文。
 */
@ExtendWith(MockitoExtension.class)
class GovernanceAgentContextServiceTest {

    @Mock
    private ProjectProfileService projectProfileService;
    @Mock
    private LongTermMemoryService longTermMemoryService;

    /**
     * 存在最新项目画像和命中的 ACTIVE 记忆时，应生成包含两类信息的上下文片段。
     */
    @Test
    void buildAgentContextShouldIncludeProjectProfileAndActiveMemories() {
        org.mockito.Mockito.when(projectProfileService.findLatestByWorkspaceId(300L)).thenReturn(GovernanceProjectProfile.builder()
            .id(1L)
            .workspaceId(300L)
            .workspacePath("D:/code/CodingX")
            .summary("检测到 CodingX 多模块项目")
            .moduleMapJson("[{\"moduleCode\":\"backend\"}]")
            .testCommandsJson("[\"cd backend && mvn test\"]")
            .riskPointsJson("[\"frontend/admin/src/pages/GovernanceCenterPage.tsx 文件较大\"]")
            .agentContext("# 项目画像\\n模块地图：backend\\n验证命令：cd backend && mvn test")
            .status("COMPLETED")
            .scannedAt(LocalDateTime.now())
            .build());
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
        GovernanceAgentContextService service = new GovernanceAgentContextService(projectProfileService, longTermMemoryService);

        String context = service.buildAgentContext(200L, 300L, "请按业务注释规范修改代码");

        assertTrue(context.contains("项目画像"));
        assertTrue(context.contains("cd backend && mvn test"));
        assertTrue(context.contains("长期记忆"));
        assertTrue(context.contains("优先写清楚业务注释"));
        assertTrue(context.contains("不要逐字复述"));
    }
}
