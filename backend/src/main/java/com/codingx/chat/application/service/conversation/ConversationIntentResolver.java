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
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 负责根据问题文本、意图树节点和示例数据执行基于 Prompt 的意图识别。
 */
@Service
@RequiredArgsConstructor
@Slf4j
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
        String prompt = promptTemplateLoader.render("intent-classify", Map.of(
            "intent_list", buildIntentList(leafNodes, nodeByCode, examplesByCode)
        ));
        try {
            String raw = aiPromptExecutionService.complete(prompt, question);
            List<ConversationIntentCandidate> candidates = parseCandidates(raw, nodeByCode);
            logIntentCandidates("模型", question, candidates);
            return candidates;
        } catch (Exception exception) {
            List<ConversationIntentCandidate> candidates = fallbackCandidates(question, leafNodes, nodeByCode, examplesByCode);
            log.warn(
                "意图识别失败，使用兜底候选: 问题={}, 候选数={}",
                StrUtil.maxLength(question, 120),
                candidates.size(),
                exception
            );
            return candidates;
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
            if ("mcp".equalsIgnoreCase(node.getIntentType()) && StrUtil.isNotBlank(node.getMcpToolId())) {
                builder.append("  toolId=").append(node.getMcpToolId()).append("\n");
            }
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
        String cleanedRaw = stripMarkdownCodeFence(raw);
        if (StrUtil.isBlank(cleanedRaw)) {
            return List.of();
        }
        if (!looksLikeJson(cleanedRaw)) {
            return List.of();
        }
        JSONArray array;
        Object parsed = JSONUtil.parse(cleanedRaw);
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
     * 清理模型常见的 markdown 代码块包裹，复用 ragent 的“先清洗再解析 JSON”思路。
     */
    private String stripMarkdownCodeFence(String raw) {
        String value = StrUtil.trim(raw);
        if (StrUtil.isBlank(value) || !value.startsWith("```")) {
            return value;
        }
        String[] lines = value.split("\\R", -1);
        if (lines.length < 2 || !StrUtil.trim(lines[0]).startsWith("```")) {
            return value;
        }
        int endFenceLine = -1;
        for (int index = lines.length - 1; index > 0; index--) {
            if (StrUtil.trim(lines[index]).startsWith("```")) {
                endFenceLine = index;
                break;
            }
        }
        if (endFenceLine <= 0) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 1; index < endFenceLine; index++) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(lines[index]);
        }
        return builder.toString().trim();
    }

    /**
     * 仅在返回值看起来像 JSON 时才交给 Hutool 解析，避免普通文本命中异常堆栈。
     */
    private boolean looksLikeJson(String raw) {
        return StrUtil.startWith(raw, "[") || StrUtil.startWith(raw, "{");
    }

    /**
     * 在模型输出不可用时提供配置化兜底，评分依据仅来自意图节点路径、描述、工具标识和示例。
     * @param question 用户问题。
     * @param leafNodes 叶子候选。
     * @param nodeByCode 节点索引。
     * @param examplesByCode 示例索引。
     * @return 按兜底相关度排序的候选。
     */
    private List<ConversationIntentCandidate> fallbackCandidates(
        String question,
        List<ChatIntentNode> leafNodes,
        Map<String, ChatIntentNode> nodeByCode,
        Map<String, List<String>> examplesByCode
    ) {
        String normalizedQuestion = normalizeText(question);
        if (StrUtil.isBlank(normalizedQuestion)) {
            return List.of();
        }
        return leafNodes.stream()
            .map(node -> new ConversationIntentCandidate(
                node,
                resolveConfiguredFallbackScore(
                    normalizedQuestion,
                    node,
                    nodeByCode,
                    examplesByCode.getOrDefault(node.getIntentCode(), List.of())
                )
            ))
            .filter(candidate -> candidate.score() > 0D)
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
     * 基于节点配置文本做轻量兜底命中，避免把搜索、天气等业务词写死在代码里。
     * @param normalizedQuestion 标准化后的用户问题。
     * @param node 当前叶子节点。
     * @param nodeByCode 节点索引。
     * @param examples 当前节点示例。
     * @return 命中分数；未命中返回 0。
     */
    private double resolveConfiguredFallbackScore(
        String normalizedQuestion,
        ChatIntentNode node,
        Map<String, ChatIntentNode> nodeByCode,
        List<String> examples
    ) {
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
        String configuredText = normalizeText(buildConfiguredFallbackText(node, nodeByCode, examples));
        if (StrUtil.isBlank(configuredText)) {
            return 0D;
        }
        String normalizedName = normalizeText(node.getName());
        if (StrUtil.isNotBlank(normalizedName) && normalizedQuestion.contains(normalizedName)) {
            return 0.82D;
        }
        int matchedUnits = countMatchedTextUnits(normalizedQuestion, configuredText);
        if (matchedUnits >= 3) {
            return 0.78D;
        }
        if (matchedUnits == 2) {
            return 0.68D;
        }
        if (matchedUnits == 1) {
            return 0.58D;
        }
        return configuredText.contains(normalizedQuestion) ? 0.60D : 0D;
    }

    /**
     * 汇总单个叶子节点的可配置语义文本，模拟 ragent 将 path/description/type/examples 交给分类器的方式。
     */
    private String buildConfiguredFallbackText(ChatIntentNode node, Map<String, ChatIntentNode> nodeByCode, List<String> examples) {
        return String.join(
            " ",
            StrUtil.blankToDefault(node.getIntentCode(), ""),
            resolveFullPath(node, nodeByCode),
            StrUtil.blankToDefault(node.getDescription(), ""),
            StrUtil.blankToDefault(node.getIntentType(), ""),
            StrUtil.blankToDefault(node.getMcpToolId(), ""),
            String.join(" ", examples)
        );
    }

    /**
     * 使用二元字符片段统计问题与配置文本的交集，兼容中文短句没有天然空格分词的场景。
     */
    private int countMatchedTextUnits(String normalizedQuestion, String configuredText) {
        Set<String> units = new LinkedHashSet<>();
        collectTextUnits(normalizedQuestion, units);
        int matched = 0;
        for (String unit : units) {
            if (configuredText.contains(unit)) {
                matched++;
            }
        }
        return matched;
    }

    /**
     * 抽取长度为 2 的滑动片段；过短文本保留原文，避免短指令完全失去兜底匹配能力。
     */
    private void collectTextUnits(String value, Set<String> units) {
        if (StrUtil.isBlank(value)) {
            return;
        }
        if (value.length() <= 2) {
            units.add(value);
            return;
        }
        for (int index = 0; index < value.length() - 1; index++) {
            units.add(value.substring(index, index + 2));
        }
    }

    /**
     * 统一标准化问句与示例文本，减少中英文标点和大小写差异带来的误判。
     * @param value 原始文本。
     * @return 标准化文本。
     */
    private String normalizeText(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Punct}\\s]+", "").toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 打印意图分类候选摘要，只保留首位候选和候选数量，避免暴露完整 Prompt 与示例数据。
     */
    private void logIntentCandidates(String source, String question, List<ConversationIntentCandidate> candidates) {
        ConversationIntentCandidate top = candidates.isEmpty() ? null : candidates.getFirst();
        log.info(
            "意图识别: 来源={}, 问题={}, 首选={}, 分数={}, 候选数={}",
            source,
            StrUtil.maxLength(question, 120),
            top == null ? null : top.node().getIntentCode(),
            top == null ? null : top.score(),
            candidates.size()
        );
    }
}
