package com.codingx.chat.application.service;

import com.codingx.chat.domain.model.ChatExecutionRun;
import com.codingx.chat.domain.model.ChatExecutionStep;
import com.codingx.chat.domain.model.ChatMessageArtifact;
import com.codingx.chat.domain.model.ChatMessageReference;
import com.codingx.chat.domain.model.ChatSkill;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.chat.domain.repository.ChatExecutionStepRepository;
import com.codingx.chat.domain.repository.ChatMessageArtifactRepository;
import com.codingx.chat.domain.repository.ChatMessageReferenceRepository;
import com.codingx.chat.domain.repository.ChatSkillRepository;
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

    private final ChatExecutionRunRepository chatExecutionRunRepository;
    private final ChatExecutionStepRepository chatExecutionStepRepository;
    private final ChatMessageReferenceRepository chatMessageReferenceRepository;
    private final ChatMessageArtifactRepository chatMessageArtifactRepository;
    private final ChatSkillRepository chatSkillRepository;

    /**
     * 返回当前会话最新一条运行记录对应的执行步骤列表。
     * @param conversationId 会话标识。
     * @return 步骤列表。
     */
    public List<ChatExecutionStep> listSteps(Long conversationId) {
        return latestRun(conversationId)
            .map(run -> chatExecutionStepRepository.findByRunId(run.getId()))
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
     * 返回当前会话最新运行任务绑定的技能列表，供工作区展示“当前技能”。
     * @param conversationId 会话标识。
     * @return 技能列表。
     */
    public List<ChatSkill> listCurrentSkills(Long conversationId) {
        return latestRun(conversationId)
            // 历史运行记录可能未补齐 taskId；此时回退 runId，兼容旧数据与单测构造。
            .map(run -> chatSkillRepository.findByTaskId(run.getTaskId() == null ? run.getId() : run.getTaskId()))
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
