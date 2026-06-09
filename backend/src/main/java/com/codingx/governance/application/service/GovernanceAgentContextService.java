package com.codingx.governance.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.governance.domain.model.GovernanceLongTermMemory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 治理 Agent 上下文服务，统一把仓库规范文件和已生效长期记忆转换成可注入模型的系统上下文。
 */
@Service
public class GovernanceAgentContextService {

    /** 仓库规范上下文服务，用于读取当前工作空间内的 AGENTS/CLAUDE/GEMINI 等规则文件。 */
    private final RepositoryInstructionContextService repositoryInstructionContextService;
    /** 长期记忆服务，用于检索 ACTIVE 记忆并在完成回复后提取新的生效记忆。 */
    private final LongTermMemoryService longTermMemoryService;
    /** 聊天前置上下文执行器，用于隔离仓库规范与长期记忆预加载任务。 */
    private final ExecutorService chatPreflightExecutor;

    /**
     * 创建治理 Agent 上下文服务。
     * @param repositoryInstructionContextService 仓库规范上下文服务，用于读取当前工作空间规则文件。
     * @param longTermMemoryService 长期记忆服务，用于检索和提取已生效记忆。
     * @param chatPreflightExecutor 聊天前置上下文执行器，用于隔离模型调用前的轻量 IO 预加载。
     */
    public GovernanceAgentContextService(
        RepositoryInstructionContextService repositoryInstructionContextService,
        LongTermMemoryService longTermMemoryService,
        @Qualifier("chatPreflightExecutor") ExecutorService chatPreflightExecutor
    ) {
        this.repositoryInstructionContextService = repositoryInstructionContextService;
        this.longTermMemoryService = longTermMemoryService;
        this.chatPreflightExecutor = chatPreflightExecutor;
    }

    /**
     * 构建本轮模型调用的治理上下文。
     * @param userId 当前用户 ID。
     * @param workspaceId 当前会话工作空间 ID，可为空。
     * @param query 用户本轮问题，用于检索相关长期记忆。
     * @return 可作为 system prompt 片段注入的上下文，缺少信息时返回空字符串。
     */
    public String buildAgentContext(Long userId, Long workspaceId, String query) {
        return buildAgentContextAsync(userId, workspaceId, query).join();
    }

    /**
     * 异步构建本轮模型调用的治理上下文。
     * 业务意图：调用方可在意图路由、搜索/MCP 判断期间提前启动上下文读取，减少最终模型调用前的串行等待。
     * @param userId 当前用户 ID。
     * @param workspaceId 当前会话工作空间 ID，可为空。
     * @param query 用户本轮问题，用于检索相关长期记忆。
     * @return 治理上下文异步结果。
     */
    public CompletableFuture<String> buildAgentContextAsync(Long userId, Long workspaceId, String query) {
        // 步骤 1：仓库规范读取和长期记忆检索互不依赖，并行启动以压缩每轮模型调用前的上下文等待。
        CompletableFuture<String> repositoryInstructionFuture = CompletableFuture.supplyAsync(
            () -> repositoryInstructionContextService.buildInstructionContext(userId, workspaceId),
            chatPreflightExecutor
        );
        CompletableFuture<List<GovernanceLongTermMemory>> memoriesFuture = CompletableFuture.supplyAsync(
            () -> longTermMemoryService.retrieveActiveMemories(userId, workspaceId, query, 6),
            chatPreflightExecutor
        );
        // 步骤 2：只组合两个读取结果，不提交父任务占用执行器线程，避免高并发下父任务等待子任务造成线程饥饿。
        return repositoryInstructionFuture.thenCombine(memoriesFuture, this::formatAgentContext);
    }

    /**
     * 按原有顺序格式化治理上下文，确保仓库规范仍位于长期记忆之前。
     * @param repositoryInstructionContext 仓库规范上下文。
     * @param memories 命中的长期记忆。
     * @return 可作为 system prompt 片段注入的上下文。
     */
    private String formatAgentContext(String repositoryInstructionContext, List<GovernanceLongTermMemory> memories) {
        List<String> sections = new ArrayList<>();
        if (StrUtil.isNotBlank(repositoryInstructionContext)) {
            sections.add(repositoryInstructionContext);
        }
        if (memories != null && !memories.isEmpty()) {
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
