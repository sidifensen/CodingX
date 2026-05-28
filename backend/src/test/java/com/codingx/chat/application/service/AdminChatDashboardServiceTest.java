package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.codingx.admin.application.service.AdminChatDashboardService;
import com.codingx.admin.application.service.AdminChatDashboardView;
import com.codingx.chat.domain.model.ChatIntentNode;
import com.codingx.chat.domain.model.ChatQueryTermMapping;
import com.codingx.chat.domain.model.ChatSampleQuestion;
import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import com.codingx.chat.domain.repository.ChatQueryTermMappingRepository;
import com.codingx.chat.domain.repository.ChatSampleQuestionRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatConversationDO;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatMessageDO;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatTraceRunDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatConversationMapper;
import com.codingx.chat.infrastructure.persistence.mapper.ChatMessageMapper;
import com.codingx.chat.infrastructure.persistence.mapper.ChatTraceRunMapper;
import com.codingx.expert.domain.model.ChatExpert;
import com.codingx.expert.domain.repository.ChatExpertRepository;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.tool.domain.model.ChatTool;
import com.codingx.tool.domain.repository.ChatToolRepository;
import com.codingx.workspace.infrastructure.persistence.mapper.WorkspaceMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证管理端 Dashboard 聚合逻辑的窗口分桶和性能摘要。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatDashboardServiceTest {

    @Mock
    private ChatConversationMapper chatConversationMapper;

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @Mock
    private ChatTraceRunMapper chatTraceRunMapper;

    @Mock
    private WorkspaceMapper workspaceMapper;

    @Mock
    private ChatIntentNodeRepository chatIntentNodeRepository;

    @Mock
    private ChatMcpRepository chatMcpRepository;

    @Mock
    private ChatQueryTermMappingRepository chatQueryTermMappingRepository;

    @Mock
    private ChatSampleQuestionRepository chatSampleQuestionRepository;

    @Mock
    private ChatSkillRepository chatSkillRepository;

    @Mock
    private ChatToolRepository chatToolRepository;

    @Mock
    private ChatExpertRepository chatExpertRepository;

    @InjectMocks
    private AdminChatDashboardService adminChatDashboardService;

    /**
     * 24h 窗口应补齐 24 个小时 bucket，并基于链路状态计算成功率与 P95。
     */
    @Test
    void getDashboardBuildsHourlyBucketsAndPerformanceSummary() {
        LocalDateTime now = LocalDateTime.now();
        when(chatConversationMapper.selectList(any())).thenReturn(List.of(
            conversation(1L, 1001L, now.minusHours(1)),
            conversation(2L, 1002L, now.minusDays(2))
        ));
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(
            message(11L, now.minusHours(1)),
            message(12L, now.minusHours(1))
        ));
        when(chatTraceRunMapper.selectList(any())).thenReturn(List.of(
            trace("trace-success", 1001L, "SUCCESS", now.minusHours(1), 8000L),
            trace("trace-failed", 1002L, "ERROR", now.minusHours(1), 12000L),
            trace("trace-running", 1003L, "RUNNING", now.minusMinutes(10), null)
        ));
        when(workspaceMapper.selectCount(any())).thenReturn(6L);
        when(chatSkillRepository.findAll()).thenReturn(List.of(mockSkill(), mockSkill()));
        when(chatToolRepository.findAll()).thenReturn(List.of(mockTool()));
        when(chatExpertRepository.findAll()).thenReturn(List.of(mockExpert(), mockExpert(), mockExpert()));
        when(chatMcpRepository.findAll()).thenReturn(List.of(mockMcp()));
        when(chatIntentNodeRepository.findAllNodes()).thenReturn(List.of(mockIntent(), mockIntent()));
        when(chatQueryTermMappingRepository.findAllMappings()).thenReturn(List.of(mockMapping()));
        when(chatSampleQuestionRepository.findEnabledQuestions()).thenReturn(List.of(mockQuestion(), mockQuestion()));

        AdminChatDashboardView result = adminChatDashboardService.getDashboard("24h");

        assertEquals("24h", result.window());
        assertEquals(24, result.trendBuckets().size());
        assertEquals(1, result.kpis().conversationCount());
        assertEquals(2, result.kpis().messageCount());
        assertEquals(3, result.kpis().activeUserCount());
        assertEquals(3, result.kpis().traceCount());
        assertEquals(1, result.kpis().runningTraceCount());
        assertEquals(33.3, result.performance().successRate());
        assertEquals(33.3, result.performance().failureRate());
        assertEquals(33.3, result.performance().runningRate());
        assertEquals(10000L, result.performance().avgTraceDurationMs());
        assertEquals(12000L, result.performance().p95TraceDurationMs());
    }

    /**
     * 7d 与 30d 窗口应分别补齐 7 和 30 个天级 bucket。
     */
    @Test
    void getDashboardBuildsDailyBucketsForLongWindows() {
        when(chatConversationMapper.selectList(any())).thenReturn(List.of());
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());
        when(chatTraceRunMapper.selectList(any())).thenReturn(List.of());
        when(workspaceMapper.selectCount(any())).thenReturn(0L);
        when(chatSkillRepository.findAll()).thenReturn(List.of());
        when(chatToolRepository.findAll()).thenReturn(List.of());
        when(chatExpertRepository.findAll()).thenReturn(List.of());
        when(chatMcpRepository.findAll()).thenReturn(List.of());
        when(chatIntentNodeRepository.findAllNodes()).thenReturn(List.of());
        when(chatQueryTermMappingRepository.findAllMappings()).thenReturn(List.of());
        when(chatSampleQuestionRepository.findEnabledQuestions()).thenReturn(List.of());

        AdminChatDashboardView sevenDays = adminChatDashboardService.getDashboard("7d");
        AdminChatDashboardView thirtyDays = adminChatDashboardService.getDashboard("30d");

        assertEquals(7, sevenDays.trendBuckets().size());
        assertEquals(30, thirtyDays.trendBuckets().size());
        assertEquals(0.0, sevenDays.performance().successRate());
        assertEquals(0L, sevenDays.performance().p95TraceDurationMs());
    }

    private ChatConversationDO conversation(Long id, Long createdBy, LocalDateTime createdAt) {
        ChatConversationDO dataObject = new ChatConversationDO();
        dataObject.setId(id);
        dataObject.setCreatedBy(createdBy);
        dataObject.setCreatedAt(createdAt);
        dataObject.setDeleted(0);
        return dataObject;
    }

    private ChatMessageDO message(Long id, LocalDateTime createdAt) {
        ChatMessageDO dataObject = new ChatMessageDO();
        dataObject.setId(id);
        dataObject.setCreatedAt(createdAt);
        dataObject.setDeleted(0);
        return dataObject;
    }

    private ChatTraceRunDO trace(String traceId, Long userId, String status, LocalDateTime startedAt, Long durationMs) {
        ChatTraceRunDO dataObject = new ChatTraceRunDO();
        dataObject.setTraceId(traceId);
        dataObject.setUserId(userId);
        dataObject.setStatus(status);
        dataObject.setStartedAt(startedAt);
        dataObject.setCreatedAt(startedAt);
        dataObject.setDurationMs(durationMs);
        dataObject.setDeleted(0);
        return dataObject;
    }

    private ChatSkill mockSkill() {
        return ChatSkill.builder().id(1L).skillCode("skill").build();
    }

    private ChatTool mockTool() {
        return ChatTool.builder().id(1L).toolCode("tool").build();
    }

    private ChatExpert mockExpert() {
        return ChatExpert.builder().id(1L).expertCode("expert").build();
    }

    private ChatMcp mockMcp() {
        return ChatMcp.builder().id(1L).mcpCode("mcp").build();
    }

    private ChatIntentNode mockIntent() {
        return ChatIntentNode.builder().id(1L).intentCode("intent").build();
    }

    private ChatQueryTermMapping mockMapping() {
        return ChatQueryTermMapping.builder().id(1L).sourceTerm("a").targetTerm("b").build();
    }

    private ChatSampleQuestion mockQuestion() {
        return ChatSampleQuestion.builder().id(1L).questionText("问题").build();
    }
}
