package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.domain.model.ChatIntentNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责识别跨系统同名主题的歧义场景，并渲染给用户的澄清提示。
 */
@Service
@RequiredArgsConstructor
public class ConversationIntentGuidanceService {

    private static final double AMBIGUITY_RATIO = 0.80D;
    private static final double AMBIGUITY_MARGIN = 0.15D;

    private final PromptTemplateLoader promptTemplateLoader;
    private final AiPromptExecutionService aiPromptExecutionService;

    /**
     * 判断当前候选是否需要澄清，并在需要时返回渲染后的提示。
     * @param question 用户问题。
     * @param candidates 意图候选。
     * @param allNodes 当前启用节点全集。
     * @return 澄清提示；无需澄清时返回 null。
     */
    @ConversationTraceNode(name = "intent-guidance", type = "INTENT")
    public String buildGuidancePrompt(String question, List<ConversationIntentCandidate> candidates, List<ChatIntentNode> allNodes) {
        if (candidates == null || candidates.size() < 2) {
            return null;
        }
        ConversationIntentCandidate top = candidates.getFirst();
        ConversationIntentCandidate second = candidates.get(1);
        if (top.node() == null || second.node() == null) {
            return null;
        }
        if (!StrUtil.equals(top.node().getName(), second.node().getName())) {
            return null;
        }
        if (top.score() <= 0) {
            return null;
        }
        double ratio = second.score() / top.score();
        Map<String, ChatIntentNode> nodeByCode = new HashMap<>();
        for (ChatIntentNode node : allNodes) {
            nodeByCode.put(node.getIntentCode(), node);
        }
        if (question != null) {
            String normalizedQuestion = normalize(question);
            if (normalizedQuestion.contains(normalize(resolveSystemName(top.node(), nodeByCode)))
                || normalizedQuestion.contains(normalize(resolveSystemName(second.node(), nodeByCode)))) {
                return null;
            }
        }
        if (!confirmAmbiguity(question, List.of(top, second))) {
            return null;
        }
        String prompt = promptTemplateLoader.render("guidance-prompt", Map.of(
            "topic_name", StrUtil.blankToDefault(top.node().getName(), "当前主题"),
            "options", "1) " + resolveFullPath(top.node(), nodeByCode) + "\n2) " + resolveFullPath(second.node(), nodeByCode)
        ));
        return prompt;
    }

    /**
     * 对边界分值的歧义场景使用 LLM 二次判定，减少硬阈值误伤。
     * @param question 用户问题。
     * @param candidates 排名前两位候选。
     * @return 是否需要澄清。
     */
    private boolean confirmAmbiguity(String question, List<ConversationIntentCandidate> candidates) {
        double top = candidates.getFirst().score();
        double second = candidates.get(1).score();
        double ratio = second / top;
        if (ratio >= AMBIGUITY_RATIO) {
            return true;
        }
        if (ratio < AMBIGUITY_RATIO - AMBIGUITY_MARGIN) {
            return false;
        }
        try {
            String prompt = promptTemplateLoader.render("guidance-ambiguity-check", Map.of(
                "question", StrUtil.blankToDefault(question, ""),
                "candidates", buildCandidateSummary(candidates)
            ));
            String raw = aiPromptExecutionService.complete(prompt, StrUtil.blankToDefault(question, ""));
            JSONObject root = JSONUtil.parseObj(raw);
            return Boolean.TRUE.equals(root.getBool("ambiguous"));
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * 构造二次判定所需的候选摘要。
     * @param candidates 候选列表。
     * @return 摘要文本。
     */
    private String buildCandidateSummary(List<ConversationIntentCandidate> candidates) {
        StringBuilder builder = new StringBuilder();
        for (ConversationIntentCandidate candidate : candidates) {
            builder.append("- id=").append(candidate.node().getIntentCode())
                .append(", score=").append(candidate.score())
                .append(", path=").append(candidate.node().getName())
                .append("\n");
        }
        return builder.toString().trim();
    }

    /**
     * 解析节点完整路径，优先使用父链拼接出的业务可读路径。
     * @param node 叶子节点。
     * @param nodeByCode 节点索引。
     * @return 完整路径文本。
     */
    private String resolveFullPath(ChatIntentNode node, Map<String, ChatIntentNode> nodeByCode) {
        java.util.LinkedList<String> segments = new java.util.LinkedList<>();
        ChatIntentNode current = node;
        while (current != null) {
            segments.addFirst(current.getName());
            current = StrUtil.isBlank(current.getParentCode()) ? null : nodeByCode.get(current.getParentCode());
        }
        return String.join(" > ", segments);
    }

    /**
     * 回溯到主题所属的系统级节点，用于判断用户问题是否已经显式指明系统。
     * @param node 当前候选。
     * @param nodeByCode 节点索引。
     * @return 系统名。
     */
    private String resolveSystemName(ChatIntentNode node, Map<String, ChatIntentNode> nodeByCode) {
        ChatIntentNode current = node;
        ChatIntentNode parent = current == null || StrUtil.isBlank(current.getParentCode()) ? null : nodeByCode.get(current.getParentCode());
        while (current != null) {
            if (parent != null && StrUtil.isBlank(parent.getParentCode())) {
                return current.getName();
            }
            current = parent;
            parent = current == null || StrUtil.isBlank(current.getParentCode()) ? null : nodeByCode.get(current.getParentCode());
        }
        return "";
    }

    /**
     * 标准化用户问题和系统名称，便于做包含匹配。
     * @param value 原始文本。
     * @return 标准化文本。
     */
    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Punct}\\s]+", "").toLowerCase(java.util.Locale.ROOT);
    }
}
