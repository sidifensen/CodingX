package com.codingx.governance.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.governance.domain.model.GovernanceLongTermMemory;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 治理 Agent 上下文服务，统一把仓库规范文件和已生效长期记忆转换成可注入模型的系统上下文。
 */
@Service
@RequiredArgsConstructor
public class GovernanceAgentContextService {

    /** 仓库规范上下文服务，用于读取当前工作空间内的 AGENTS/CLAUDE/GEMINI 等规则文件。 */
    private final RepositoryInstructionContextService repositoryInstructionContextService;
    /** 长期记忆服务，用于检索 ACTIVE 记忆并在完成回复后提取新的生效记忆。 */
    private final LongTermMemoryService longTermMemoryService;

    /**
     * 构建本轮模型调用的治理上下文。
     * @param userId 当前用户 ID。
     * @param workspaceId 当前会话工作空间 ID，可为空。
     * @param query 用户本轮问题，用于检索相关长期记忆。
     * @return 可作为 system prompt 片段注入的上下文，缺少信息时返回空字符串。
     */
    public String buildAgentContext(Long userId, Long workspaceId, String query) {
        List<String> sections = new ArrayList<>();
        String repositoryInstructionContext = repositoryInstructionContextService.buildInstructionContext(userId, workspaceId);
        if (StrUtil.isNotBlank(repositoryInstructionContext)) {
            sections.add(repositoryInstructionContext);
        }
        List<GovernanceLongTermMemory> memories = longTermMemoryService.retrieveActiveMemories(userId, workspaceId, query, 6);
        if (!memories.isEmpty()) {
            StringBuilder memorySection = new StringBuilder();
            memorySection.append("# 长期记忆\n");
            memorySection.append("使用方式：以下内容是用户或项目已生效的偏好/约束。只在与当前任务相关时应用，不要逐字复述。\n");
            int index = 1;
            for (GovernanceLongTermMemory memory : memories) {
                memorySection.append(index++)
                    .append(". [")
                    .append(StrUtil.blankToDefault(memory.getMemoryScope(), "USER"))
                    .append("] ")
                    .append(StrUtil.maxLength(StrUtil.blankToDefault(memory.getContent(), ""), 240))
                    .append('\n');
            }
            sections.add(memorySection.toString().trim());
        }
        return String.join("\n\n", sections);
    }

    /**
     * 在助手成功完成后提取长期记忆。
     * @param conversation 当前会话。
     * @param userMessage 用户消息。
     * @param assistantMessage 助手完成消息。
     * @return 新增且已生效的长期记忆列表。
     */
    public List<GovernanceLongTermMemory> extractMemoryCandidates(
        ChatConversation conversation,
        ChatMessage userMessage,
        ChatMessage assistantMessage
    ) {
        // 步骤 1：提取逻辑下沉到长期记忆服务，聊天主流程只负责传递完整来源链路。
        return longTermMemoryService.extractCandidates(conversation, userMessage, assistantMessage);
    }
}
