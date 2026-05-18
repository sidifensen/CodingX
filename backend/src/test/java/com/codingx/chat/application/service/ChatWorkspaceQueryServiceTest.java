package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatExecutionRun;
import com.codingx.chat.domain.model.ChatExecutionStep;
import com.codingx.chat.domain.model.ChatMessageArtifact;
import com.codingx.chat.domain.model.ChatMessageReference;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.chat.domain.repository.ChatExecutionStepRepository;
import com.codingx.chat.domain.repository.ChatMessageArtifactRepository;
import com.codingx.chat.domain.repository.ChatMessageReferenceRepository;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证聊天工作区回放查询服务。
 */
@ExtendWith(MockitoExtension.class)
class ChatWorkspaceQueryServiceTest {

    @Mock private ChatExecutionRunRepository chatExecutionRunRepository;
    @Mock private ChatExecutionStepRepository chatExecutionStepRepository;
    @Mock private ChatMessageReferenceRepository chatMessageReferenceRepository;
    @Mock private ChatMessageArtifactRepository chatMessageArtifactRepository;
    @Mock private ChatMcpRepository chatMcpRepository;
    @Mock private ChatSkillRepository chatSkillRepository;

    @InjectMocks
    private ChatWorkspaceQueryService chatWorkspaceQueryService;

    /**
     * 查询步骤时即使仓储返回顺序无序，也应选取最新运行记录。
     */
    @Test
    void listStepsUsesNewestRunWhenRepositoryOrderIsNotSorted() {
        when(chatExecutionRunRepository.findByConversationId(2001L)).thenReturn(List.of(
            run(5001L, LocalDateTime.of(2026, 5, 15, 10, 0)),
            run(5002L, LocalDateTime.of(2026, 5, 15, 10, 5))
        ));
        when(chatExecutionStepRepository.findByRunId(5002L)).thenReturn(List.of(
            ChatExecutionStep.builder().id(1L).runId(5002L).stepTitle("搜索资料").build()
        ));

        List<ChatExecutionStep> steps = chatWorkspaceQueryService.listSteps(2001L);

        assertEquals(1, steps.size());
        assertEquals("搜索资料", steps.getFirst().getStepTitle());
    }

    /**
     * 查询来源时应与步骤回放共享相同的最新运行选择规则。
     */
    @Test
    void listReferencesUsesNewestRunWhenRepositoryOrderIsNotSorted() {
        when(chatExecutionRunRepository.findByConversationId(2001L)).thenReturn(List.of(
            run(5001L, LocalDateTime.of(2026, 5, 15, 10, 0)),
            run(5002L, LocalDateTime.of(2026, 5, 15, 10, 5))
        ));
        when(chatMessageReferenceRepository.findByRunId(5002L)).thenReturn(List.of(
            ChatMessageReference.builder().id(11L).runId(5002L).title("Spring Boot SSE 指南").build()
        ));

        List<ChatMessageReference> references = chatWorkspaceQueryService.listReferences(2001L);

        assertEquals(1, references.size());
        assertEquals("Spring Boot SSE 指南", references.getFirst().getTitle());
    }

    /**
     * 查询产物时无执行记录应直接返回空集合，避免前端回放空指针。
     */
    @Test
    void listArtifactsReturnsEmptyWhenConversationHasNoRuns() {
        when(chatExecutionRunRepository.findByConversationId(2001L)).thenReturn(List.of());

        List<ChatMessageArtifact> artifacts = chatWorkspaceQueryService.listArtifacts(2001L);

        assertEquals(List.of(), artifacts);
    }

    /**
     * 当前会话存在任务技能绑定时应返回技能列表供右栏展示。
     */
    @Test
    void listCurrentSkillsReturnsBoundSkillsFromLatestTask() {
        when(chatExecutionRunRepository.findByConversationId(2001L)).thenReturn(List.of(
            run(5002L, LocalDateTime.of(2026, 5, 15, 10, 5))
        ));
        when(chatSkillRepository.findByTaskId(5002L)).thenReturn(List.of(
            ChatSkill.builder().id(1L).skillCode("conversation-core").displayName("会话核心").category("核心能力").enabled(1).sortNo(1).build()
        ));

        List<ChatSkill> skills = chatWorkspaceQueryService.listCurrentSkills(2001L);

        assertEquals(1, skills.size());
        assertEquals("conversation-core", skills.getFirst().getSkillCode());
    }

    /**
     * 当前会话存在任务 MCP 绑定时应返回 MCP 列表供工作区展示。
     */
    @Test
    void listCurrentMcpsReturnsBoundMcpsFromLatestTask() {
        when(chatExecutionRunRepository.findByConversationId(2001L)).thenReturn(List.of(
            run(5002L, LocalDateTime.of(2026, 5, 15, 10, 5))
        ));
        when(chatMcpRepository.findByTaskId(5002L)).thenReturn(List.of(
            ChatMcp.builder().id(1L).mcpCode("sales_query").displayName("销售查询").category("销售").enabled(1).sortNo(1).build()
        ));

        List<ChatMcp> mcps = chatWorkspaceQueryService.listCurrentMcps(2001L);

        assertEquals(1, mcps.size());
        assertEquals("sales_query", mcps.getFirst().getMcpCode());
    }

    /**
     * 构造带时间戳的运行记录，便于验证最新 run 选择逻辑。
     * @param runId 运行标识。
     * @param createdAt 创建时间。
     * @return 运行记录。
     */
    private ChatExecutionRun run(Long runId, LocalDateTime createdAt) {
        return ChatExecutionRun.builder()
            .id(runId)
            .conversationId(2001L)
            .createdAt(createdAt)
            .build();
    }
}
