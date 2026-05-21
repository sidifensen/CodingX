package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.domain.model.ChatIntentExample;
import com.codingx.chat.domain.model.ChatIntentNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责根据问题文本、意图树节点和示例数据执行基于 Prompt 的意图识别。
 */
@Service
@RequiredArgsConstructor
public class ConversationIntentResolver {

    private final PromptTemplateLoader promptTemplateLoader;
    private final AiPromptExecutionService aiPromptExecutionService;

    /**
     * 返回当前问题最匹配的意图编码。
     * @param question 当前问题。
     * @param nodes 启用中的意图节点。
     * @param examples 示例问题。
     * @return 命中的意图编码。
     */
    @ConversationTraceNode(name = "intent-resolve", type = "INTENT")
    public String resolveIntent(String question, List<ChatIntentNode> nodes, List<ChatIntentExample> examples) {
        return resolveCandidates(question, nodes, examples).stream()
            .findFirst()
            .map(candidate -> candidate.node().getIntentCode())
            .orElse("chat.normal");
    }

    /**
     * 基于叶子节点和示例数据构建 Prompt，解析模型输出的候选得分。
     * @param question 当前问题。
     * @param nodes 启用中的意图节点。
     * @param examples 示例问题。
     * @return 按分数降序排序的候选。
     */
    @ConversationTraceNode(name = "intent-classify", type = "INTENT")
    public List<ConversationIntentCandidate> resolveCandidates(String question, List<ChatIntentNode> nodes, List<ChatIntentExample> examples) {
        if (StrUtil.isBlank(question) || nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        Map<String, ChatIntentNode> nodeByCode = new HashMap<>();
        Map<String, List<String>> examplesByCode = new HashMap<>();
        Set<String> parentCodes = new LinkedHashSet<>();
        for (ChatIntentNode node : nodes) {
            nodeByCode.put(node.getIntentCode(), node);
            if (StrUtil.isNotBlank(node.getParentCode())) {
                parentCodes.add(node.getParentCode());
            }
        }
        for (ChatIntentExample example : examples) {
            examplesByCode.computeIfAbsent(example.getIntentCode(), ignored -> new ArrayList<>()).add(example.getExampleText());
        }
        List<ChatIntentNode> leafNodes = nodes.stream()
            .filter(node -> !parentCodes.contains(node.getIntentCode()))
            .sorted(Comparator.comparing(ChatIntentNode::getSortNo, Comparator.nullsLast(Integer::compareTo)))
            .toList();
        if (leafNodes.isEmpty()) {
            return List.of();
        }
        List<ConversationIntentCandidate> heuristicCandidates = heuristicCandidates(question, leafNodes, examplesByCode);
        if (!heuristicCandidates.isEmpty()) {
            return heuristicCandidates;
        }
        String prompt = promptTemplateLoader.render("intent-classify", Map.of(
            "intent_list", buildIntentList(leafNodes, nodeByCode, examplesByCode)
        ));
        try {
            String raw = aiPromptExecutionService.complete(prompt, question);
            return parseCandidates(raw, nodeByCode);
        } catch (Exception exception) {
            return fallbackCandidates(question, leafNodes);
        }
    }

    /**
     * 构造分类 Prompt 中的意图候选文本。
     * @param leafNodes 叶子节点。
     * @param nodeByCode 节点索引。
     * @param examplesByCode 示例索引。
     * @return 候选文本。
     */
    private String buildIntentList(List<ChatIntentNode> leafNodes, Map<String, ChatIntentNode> nodeByCode, Map<String, List<String>> examplesByCode) {
        StringBuilder builder = new StringBuilder();
        for (ChatIntentNode node : leafNodes) {
            builder.append("- id=").append(node.getIntentCode()).append("\n");
            builder.append("  path=").append(resolveFullPath(node, nodeByCode)).append("\n");
            builder.append("  description=").append(StrUtil.blankToDefault(node.getDescription(), "")).append("\n");
            builder.append("  type=").append(StrUtil.blankToDefault(node.getIntentType(), "search").toUpperCase()).append("\n");
            List<String> nodeExamples = examplesByCode.getOrDefault(node.getIntentCode(), List.of());
            if (!nodeExamples.isEmpty()) {
                builder.append("  examples=").append(String.join(" / ", nodeExamples)).append("\n");
            }
            builder.append("\n");
        }
        return builder.toString().trim();
    }

    /**
     * 解析模型返回的 JSON 候选。
     * @param raw 模型原始输出。
     * @param nodeByCode 节点索引。
     * @return 排序后的候选。
     */
    private List<ConversationIntentCandidate> parseCandidates(String raw, Map<String, ChatIntentNode> nodeByCode) {
        JSONArray array;
        Object parsed = JSONUtil.parse(raw);
        if (parsed instanceof JSONArray jsonArray) {
            array = jsonArray;
        } else if (parsed instanceof JSONObject jsonObject && jsonObject.containsKey("results")) {
            array = jsonObject.getJSONArray("results");
        } else {
            return List.of();
        }
        List<ConversationIntentCandidate> candidates = new ArrayList<>();
        for (Object entry : array) {
            if (!(entry instanceof JSONObject item)) {
                continue;
            }
            String id = item.getStr("id");
            Double score = item.getDouble("score");
            ChatIntentNode node = nodeByCode.get(id);
            if (node == null || score == null) {
                continue;
            }
            candidates.add(new ConversationIntentCandidate(node, score));
        }
        candidates.sort(Comparator.comparingDouble(ConversationIntentCandidate::score).reversed());
        return candidates;
    }

    /**
     * 在模型输出不可用时提供一个最低限度的关键词兜底，避免链路直接失明。
     * @param question 用户问题。
     * @param leafNodes 叶子候选。
     * @return 兜底候选。
     */
    private List<ConversationIntentCandidate> fallbackCandidates(String question, List<ChatIntentNode> leafNodes) {
        String normalizedQuestion = normalizeText(question);
        return leafNodes.stream()
            .filter(node -> normalizedQuestion.contains(normalizeText(node.getName()))
                || normalizedQuestion.contains(normalizeText(node.getDescription())))
            .map(node -> new ConversationIntentCandidate(node, 0.60D))
            .sorted(Comparator.comparingDouble(ConversationIntentCandidate::score).reversed())
            .toList();
    }

    /**
     * 从叶子节点向上回溯，构造完整业务路径。
     * @param node 叶子节点。
     * @param nodeByCode 节点索引。
     * @return 完整路径。
     */
    private String resolveFullPath(ChatIntentNode node, Map<String, ChatIntentNode> nodeByCode) {
        java.util.LinkedList<String> segments = new java.util.LinkedList<>();
        ChatIntentNode current = node;
        while (current != null) {
            segments.addFirst(StrUtil.blankToDefault(current.getName(), current.getIntentCode()));
            current = StrUtil.isBlank(current.getParentCode()) ? null : nodeByCode.get(current.getParentCode());
        }
        return String.join(" > ", segments);
    }

    /**
     * 对 system / mcp 这类强模式意图优先做启发式命中，避免每次都走 LLM 分类。
     * @param question 用户问题。
     * @param leafNodes 叶子候选。
     * @param examplesByCode 示例索引。
     * @return 命中的启发式候选。
     */
    private List<ConversationIntentCandidate> heuristicCandidates(String question, List<ChatIntentNode> leafNodes, Map<String, List<String>> examplesByCode) {
        String normalizedQuestion = normalizeText(question);
        List<ConversationIntentCandidate> candidates = new ArrayList<>();
        for (ChatIntentNode node : leafNodes) {
            if (!"system".equalsIgnoreCase(node.getIntentType()) && !"mcp".equalsIgnoreCase(node.getIntentType())) {
                continue;
            }
            double score = resolveHeuristicScore(normalizedQuestion, node, examplesByCode.getOrDefault(node.getIntentCode(), List.of()));
            if (score > 0D) {
                candidates.add(new ConversationIntentCandidate(node, score));
            }
        }
        candidates.sort(Comparator.comparingDouble(ConversationIntentCandidate::score).reversed());
        return candidates;
    }

    /**
     * 基于节点示例与节点名做轻量启发式命中，避免把欢迎语、MCP 查询入口绑死在具体编码上。
     * @param normalizedQuestion 标准化后的用户问题。
     * @param node 当前叶子节点。
     * @param examples 当前节点示例。
     * @return 命中分数；未命中返回 0。
     */
    private double resolveHeuristicScore(String normalizedQuestion, ChatIntentNode node, List<String> examples) {
        for (String example : examples) {
            String normalizedExample = normalizeText(example);
            if (StrUtil.isBlank(normalizedExample)) {
                continue;
            }
            if (normalizedQuestion.equals(normalizedExample)) {
                return 0.96D;
            }
            if (normalizedQuestion.contains(normalizedExample) || normalizedExample.contains(normalizedQuestion)) {
                return 0.92D;
            }
        }
        String normalizedName = normalizeText(node.getName());
        if (StrUtil.isNotBlank(normalizedName) && normalizedQuestion.contains(normalizedName)) {
            return 0.82D;
        }
        return 0D;
    }

    /**
     * 统一标准化问句与示例文本，减少中英文标点和大小写差异带来的误判。
     * @param value 原始文本。
     * @return 标准化文本。
     */
    private String normalizeText(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Punct}\\s]+", "").toLowerCase(java.util.Locale.ROOT);
    }
}
