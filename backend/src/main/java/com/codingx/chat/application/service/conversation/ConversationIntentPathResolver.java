package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatIntentNode;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 解析意图节点父链相关的展示路径和系统边界，供歧义引导独立复用。
 */
@Component
public class ConversationIntentPathResolver {

    /**
     * 将启用节点列表构造成按意图编码索引的 Map，空节点会被跳过。
     * @param nodes 当前启用节点全集。
     * @return 节点索引。
     */
    public Map<String, ChatIntentNode> indexByCode(List<ChatIntentNode> nodes) {
        Map<String, ChatIntentNode> nodeByCode = new HashMap<>();
        if (nodes == null) {
            return nodeByCode;
        }
        for (ChatIntentNode node : nodes) {
            if (node != null && StrUtil.isNotBlank(node.getIntentCode())) {
                nodeByCode.put(node.getIntentCode(), node);
            }
        }
        return nodeByCode;
    }

    /**
     * 解析候选节点完整路径，优先沿 parentCode 回溯，保证提示选项可读。
     * @param node 候选节点。
     * @param nodeByCode 节点索引。
     * @return 完整路径。
     */
    public String resolveFullPath(ChatIntentNode node, Map<String, ChatIntentNode> nodeByCode) {
        LinkedList<String> segments = new LinkedList<>();
        ChatIntentNode current = node;
        while (current != null) {
            segments.addFirst(StrUtil.blankToDefault(current.getName(), current.getIntentCode()));
            current = parentOf(current, nodeByCode);
        }
        return String.join(" > ", segments);
    }

    /**
     * 解析候选所属系统名称，用于判断用户问题是否已显式指定范围。
     * @param node 候选节点。
     * @param nodeByCode 节点索引。
     * @return 系统名称。
     */
    public String resolveSystemName(ChatIntentNode node, Map<String, ChatIntentNode> nodeByCode) {
        ChatIntentNode systemNode = resolveSystemNode(node, nodeByCode);
        return systemNode == null ? "" : StrUtil.blankToDefault(systemNode.getName(), "");
    }

    /**
     * 解析用于系统级去重的稳定标识；优先使用系统节点编码，缺失时退回名称。
     * @param node 候选节点。
     * @param nodeByCode 节点索引。
     * @return 系统标识。
     */
    public String resolveSystemIdentity(ChatIntentNode node, Map<String, ChatIntentNode> nodeByCode) {
        ChatIntentNode systemNode = resolveSystemNode(node, nodeByCode);
        if (systemNode == null) {
            return "";
        }
        return StrUtil.blankToDefault(systemNode.getIntentCode(), systemNode.getName());
    }

    /**
     * 判断问题文本是否已经包含候选所属系统名，避免对用户已说明的范围重复澄清。
     * @param question 用户问题。
     * @param rankedCandidates 已排序候选。
     * @param nodeByCode 节点索引。
     * @return 是否显式包含系统名。
     */
    public boolean questionContainsSystemName(
        String question,
        List<ConversationIntentCandidate> rankedCandidates,
        Map<String, ChatIntentNode> nodeByCode
    ) {
        if (StrUtil.isBlank(question) || rankedCandidates == null) {
            return false;
        }
        String normalizedQuestion = normalize(question);
        return rankedCandidates.stream()
            .map(candidate -> resolveSystemName(candidate.node(), nodeByCode))
            .filter(StrUtil::isNotBlank)
            .distinct()
            .map(this::normalize)
            .filter(alias -> alias.length() >= 2)
            .anyMatch(normalizedQuestion::contains);
    }

    /**
     * 标准化名称字符串，便于做关键词包含匹配。
     * @param value 原始文本。
     * @return 标准化文本。
     */
    public String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Punct}\\s]+", "").toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 识别候选所属系统节点：当当前节点的父节点是根节点时，当前节点就是业务系统节点。
     */
    private ChatIntentNode resolveSystemNode(ChatIntentNode node, Map<String, ChatIntentNode> nodeByCode) {
        ChatIntentNode current = node;
        ChatIntentNode parent = parentOf(current, nodeByCode);
        while (current != null) {
            if (parent == null) {
                return current;
            }
            if (StrUtil.isBlank(parent.getParentCode())) {
                return current;
            }
            current = parent;
            parent = parentOf(current, nodeByCode);
        }
        return null;
    }

    /**
     * 根据 parentCode 获取父节点，统一封装空值边界。
     */
    private ChatIntentNode parentOf(ChatIntentNode node, Map<String, ChatIntentNode> nodeByCode) {
        if (node == null || nodeByCode == null || StrUtil.isBlank(node.getParentCode())) {
            return null;
        }
        return nodeByCode.get(node.getParentCode());
    }
}
