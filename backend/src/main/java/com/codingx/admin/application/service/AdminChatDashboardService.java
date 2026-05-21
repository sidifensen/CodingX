package com.codingx.admin.application.service;

import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import com.codingx.chat.domain.repository.ChatQueryTermMappingRepository;
import com.codingx.chat.domain.repository.ChatSampleQuestionRepository;
import com.codingx.chat.domain.repository.ChatTraceRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供聊天运行时后台 Dashboard 聚合数据。
 */
@Service
@RequiredArgsConstructor
public class AdminChatDashboardService {

    private final ChatTraceRunRepository chatTraceRunRepository;
    private final ChatIntentNodeRepository chatIntentNodeRepository;
    private final com.codingx.mcp.domain.repository.ChatMcpRepository chatMcpRepository;
    private final ChatQueryTermMappingRepository chatQueryTermMappingRepository;
    private final ChatSampleQuestionRepository chatSampleQuestionRepository;

    public AdminChatDashboardView getDashboard() {
        java.util.List<com.codingx.chat.domain.model.ChatTraceRun> traces = chatTraceRunRepository.findRecent(500);
        return new AdminChatDashboardView(
            traces.size(),
            (int) traces.stream().filter(trace -> "RUNNING".equalsIgnoreCase(trace.getStatus())).count(),
            chatIntentNodeRepository.findAllNodes().size(),
            chatMcpRepository.findAll().size(),
            chatQueryTermMappingRepository.findAllMappings().size(),
            chatSampleQuestionRepository.findEnabledQuestions().size()
        );
    }
}
