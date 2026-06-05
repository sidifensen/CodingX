package com.codingx.chat.application.service;

import com.codingx.chat.domain.model.ChatExecutionRun;
import com.codingx.chat.domain.model.ChatExecutionStep;
import com.codingx.chat.domain.model.ChatMessageArtifact;
import com.codingx.chat.domain.model.ChatMessageReference;
import com.codingx.chat.application.service.ChatRunContextStepSupport;
import com.codingx.expert.domain.model.ChatExpert;
import com.codingx.expert.domain.repository.ChatExpertRepository;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.chat.domain.repository.ChatExecutionStepRepository;
import com.codingx.chat.domain.repository.ChatMessageArtifactRepository;
import com.codingx.chat.domain.repository.ChatMessageReferenceRepository;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import java.util.List;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责提供右侧工作区所需的步骤、来源和产物回放数据。
 */
@Service
@RequiredArgsConstructor
public class ChatWorkspaceQueryService {

    /** 执行运行仓储，用于定位会话最新一次 run 及其绑定的上下文。 */
    private final ChatExecutionRunRepository chatExecutionRunRepository;
    /** 执行步骤仓储，用于回放右侧工作区的过程步骤。 */
    private final ChatExecutionStepRepository chatExecutionStepRepository;
    /** 消息引用仓储，用于回放最新 run 生成的搜索来源。 */
    private final ChatMessageReferenceRepository chatMessageReferenceRepository;
    /** 消息产物仓储，用于回放最新 run 生成的文档或文件产物。 */
    private final ChatMessageArtifactRepository chatMessageArtifactRepository;
    /** MCP 仓储，用于把 run 上下文中的 MCP 编码还原为展示对象。 */
    private final ChatMcpRepository chatMcpRepository;
    /** 技能仓储，用于把 run 上下文中的技能编码还原为展示对象。 */
    private final ChatSkillRepository chatSkillRepository;
    /** 专家仓储，用于把 run 上下文中的专家编码还原为展示对象。 */
    private final ChatExpertRepository chatExpertRepository;

    /**
     * 返回当前会话最新一条运行记录对应的执行步骤列表。
     * @param conversationId 会话标识。
     * @return 步骤列表。
     */
    public List<ChatExecutionStep> listSteps(Long conversationId) {
        return latestRun(conversationId)
            .map(run -> chatExecutionStepRepository.findByRunId(run.getId()))
            .map(steps -> steps.stream()
                // 运行上下文只服务后端恢复 current skills/mcps，不展示为用户过程步骤。
                .filter(step -> !ChatRunContextStepSupport.STEP_TYPE.equalsIgnoreCase(step.getStepType()))
                .toList())
            .orElse(List.of());
    }

    /**
     * 返回当前会话最新运行记录对应的参考来源列表。
     * @param conversationId 会话标识。
     * @return 来源列表。
     */
    public List<ChatMessageReference> listReferences(Long conversationId) {
        return latestRun(conversationId)
            .map(run -> chatMessageReferenceRepository.findByRunId(run.getId()))
            .orElse(List.of());
    }

    /**
     * 返回当前会话最新运行记录对应的产物列表。
     * @param conversationId 会话标识。
     * @return 产物列表。
     */
    public List<ChatMessageArtifact> listArtifacts(Long conversationId) {
        return latestRun(conversationId)
            .map(run -> chatMessageArtifactRepository.findByRunId(run.getId()))
            .orElse(List.of());
    }

    /**
     * 返回当前会话最新 run 上下文记录的技能列表，供工作区展示“当前技能”。
     * @param conversationId 会话标识。
     * @return 技能列表。
     */
    public List<ChatSkill> listCurrentSkills(Long conversationId) {
        return latestRun(conversationId)
            .map(run -> ChatRunContextStepSupport.parseContext(chatExecutionStepRepository.findByRunId(run.getId())).skillCodes())
            .map(skillCodes -> skillCodes.stream()
                .map(chatSkillRepository::findBySkillCode)
                .filter(skill -> skill != null)
                .toList())
            .orElse(List.of());
    }

    /**
     * 返回当前会话最新 run 上下文记录的 MCP 列表，供工作区展示“当前 MCP”。
     * @param conversationId 会话标识。
     * @return MCP 列表。
     */
    public List<ChatMcp> listCurrentMcps(Long conversationId) {
        return latestRun(conversationId)
            .map(run -> ChatRunContextStepSupport.parseContext(chatExecutionStepRepository.findByRunId(run.getId())).mcpCodes())
            .map(mcpCodes -> mcpCodes.stream()
                .map(chatMcpRepository::findByMcpCode)
                .filter(mcp -> mcp != null)
                .toList())
            .orElse(List.of());
    }

    /**
     * 返回当前会话最新 run 上下文记录的专家列表，供工作区展示“当前专家”。
     * @param conversationId 会话标识。
     * @return 专家列表。
     */
    public List<ChatExpert> listCurrentExperts(Long conversationId) {
        return latestRun(conversationId)
            .map(run -> {
                ChatRunContextStepSupport.RunContext context =
                    ChatRunContextStepSupport.parseContext(chatExecutionStepRepository.findByRunId(run.getId()));
                // 专家选择只从隐藏 runtime_context 步骤回放；旧 task_expert 表已删除，缺失时直接返回空。
                if (context.expertCode() == null || context.expertCode().isBlank()) {
                    return List.<ChatExpert>of();
                }
                ChatExpert expert = chatExpertRepository.findByExpertCode(context.expertCode());
                return expert == null ? List.<ChatExpert>of() : List.of(expert);
            })
            .orElse(List.of());
    }

    /**
     * 显式挑选当前会话最新的执行 run，避免不同仓储实现的返回顺序差异影响回放结果。
     * 优先比较创建时间；若时间缺失，则回退到较大的主键，兼容历史骨架数据。
     * @param conversationId 会话标识。
     * @return 最新执行记录。
     */
    private java.util.Optional<ChatExecutionRun> latestRun(Long conversationId) {
        return chatExecutionRunRepository.findByConversationId(conversationId).stream()
            .max(Comparator
                .comparing(ChatExecutionRun::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ChatExecutionRun::getId, Comparator.nullsFirst(Comparator.naturalOrder())));
    }
}
