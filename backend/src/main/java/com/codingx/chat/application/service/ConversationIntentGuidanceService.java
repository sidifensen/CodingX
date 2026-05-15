package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatIntentNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 负责识别跨系统同名主题的歧义场景，并渲染给用户的澄清提示。
 */
@Service
public class ConversationIntentGuidanceService {

    private static final double AMBIGUITY_RATIO = 0.80D;

    private final PromptTemplateLoader promptTemplateLoader;

    /**
     * 构造澄清服务。
     * @param promptTemplateLoader Prompt 加载器。
     */
    public ConversationIntentGuidanceService(PromptTemplateLoader promptTemplateLoader) {
        this.promptTemplateLoader = promptTemplateLoader;
    }

    /**
     * 判断当前候选是否需要澄清，并在需要时返回渲染后的提示。
     * @param question 用户问题。
     * @param candidates 意图候选。
     * @param allNodes 当前启用节点全集。
     * @return 澄清提示；无需澄清时返回 null。
     */
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
        if (top.score() <= 0 || second.score() / top.score() < AMBIGUITY_RATIO) {
            return null;
        }
        Map<String, ChatIntentNode> nodeByCode = new HashMap<>();
        for (ChatIntentNode node : allNodes) {
            nodeByCode.put(node.getIntentCode(), node);
        }
        String prompt = promptTemplateLoader.render("guidance-prompt", Map.of(
            "topic_name", StrUtil.blankToDefault(top.node().getName(), "当前主题"),
            "options", "1) " + resolveFullPath(top.node(), nodeByCode) + "\n2) " + resolveFullPath(second.node(), nodeByCode)
        ));
        if (question != null) {
            String normalizedQuestion = normalize(question);
            if (normalizedQuestion.contains(normalize(resolveSystemName(top.node(), nodeByCode)))
                || normalizedQuestion.contains(normalize(resolveSystemName(second.node(), nodeByCode)))) {
                return null;
            }
        }
        return prompt;
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
