package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.domain.model.ChatIntentNode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 按 ragent 的歧义引导流程检测候选意图是否需要先向用户澄清。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConversationIntentAmbiguityDetector {

    private static final double MIN_AMBIGUITY_CANDIDATE_SCORE = 0.35D;

    private final RuntimeSettingService runtimeSettingService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final AiPromptExecutionService aiPromptExecutionService;
    private final ConversationIntentPathResolver conversationIntentPathResolver;

    /**
     * 从意图候选中提取可展示的歧义组，未命中时返回 null。
     * @param question 用户问题。
     * @param candidates 意图候选。
     * @param allNodes 当前启用节点全集。
     * @return 歧义候选组。
     */
    public AmbiguityGroup detect(
        String question,
        List<ConversationIntentCandidate> candidates,
        List<ChatIntentNode> allNodes
    ) {
        if (!runtimeSettingService.chatIntentGuidanceEnabled()) {
            return null;
        }
        Map<String, ChatIntentNode> nodeByCode = conversationIntentPathResolver.indexByCode(allNodes);
        List<ConversationIntentCandidate> ranked = keepSameTopicCandidates(rankBestCandidatePerSystem(candidates, nodeByCode));
        if (ranked.size() < 2 || shouldSkipGuidance(question, ranked, nodeByCode)) {
            return null;
        }
        if (!confirmAmbiguity(question, ranked, nodeByCode)) {
            return null;
        }
        List<ConversationIntentCandidate> trimmed = trimRankedOptions(ranked);
        if (trimmed.size() < 2) {
            return null;
        }
        logAmbiguityTriggered(question, trimmed, nodeByCode);
        return new AmbiguityGroup(
            StrUtil.blankToDefault(trimmed.getFirst().node().getName(), "当前主题"),
            trimmed,
            nodeByCode
        );
    }

    /**
     * 每个系统只保留最高分候选，防止同一系统多个叶子节点占满澄清选项。
     */
    private List<ConversationIntentCandidate> rankBestCandidatePerSystem(
        List<ConversationIntentCandidate> candidates,
        Map<String, ChatIntentNode> nodeByCode
    ) {
        if (candidates == null) {
            return List.of();
        }
        Map<String, ConversationIntentCandidate> bestBySystem = new LinkedHashMap<>();
        for (ConversationIntentCandidate candidate : candidates) {
            if (candidate == null || candidate.node() == null || candidate.score() < MIN_AMBIGUITY_CANDIDATE_SCORE) {
                continue;
            }
            String systemIdentity = conversationIntentPathResolver.resolveSystemIdentity(candidate.node(), nodeByCode);
            if (StrUtil.isBlank(systemIdentity)) {
                continue;
            }
            bestBySystem.merge(
                systemIdentity,
                candidate,
                (left, right) -> left.score() >= right.score() ? left : right
            );
        }
        return bestBySystem.values().stream()
            .sorted(Comparator.comparingDouble(ConversationIntentCandidate::score).reversed())
            .toList();
    }

    /**
     * ragent 的歧义引导只针对“同名主题跨系统”场景；不同名称通常代表不同处理策略，应交给最高分意图继续执行。
     */
    private List<ConversationIntentCandidate> keepSameTopicCandidates(List<ConversationIntentCandidate> ranked) {
        if (ranked.isEmpty()) {
            return List.of();
        }
        String topicKey = normalizeTopicName(ranked.getFirst().node());
        if (StrUtil.isBlank(topicKey)) {
            return List.of();
        }
        return ranked.stream()
            .filter(candidate -> StrUtil.equals(topicKey, normalizeTopicName(candidate.node())))
            .toList();
    }

    /**
     * 归一化主题名，避免空格、标点和大小写差异影响同名主题判断。
     */
    private String normalizeTopicName(ChatIntentNode node) {
        return node == null ? "" : conversationIntentPathResolver.normalize(node.getName());
    }

    /**
     * 快速跳过明显不需要澄清的场景：最高分无效、分差足够大或问题已写明系统名。
     */
    private boolean shouldSkipGuidance(
        String question,
        List<ConversationIntentCandidate> ranked,
        Map<String, ChatIntentNode> nodeByCode
    ) {
        double top = ranked.getFirst().score();
        if (top <= 0) {
            return true;
        }
        double ratio = ranked.get(1).score() / top;
        double threshold = runtimeSettingService.chatIntentGuidanceAmbiguityScoreRatio();
        double margin = runtimeSettingService.chatIntentGuidanceAmbiguityMargin();
        if (ratio < threshold - margin) {
            log.debug("歧义引导跳过: ratio={} lowBound={}", ratio, threshold - margin);
            return true;
        }
        return conversationIntentPathResolver.questionContainsSystemName(question, ranked, nodeByCode);
    }

    /**
     * 根据分数阈值和边界 LLM 复核确认是否触发澄清。
     */
    private boolean confirmAmbiguity(
        String question,
        List<ConversationIntentCandidate> ranked,
        Map<String, ChatIntentNode> nodeByCode
    ) {
        double top = ranked.getFirst().score();
        double second = ranked.get(1).score();
        if (top <= 0) {
            return false;
        }
        double ratio = second / top;
        double threshold = runtimeSettingService.chatIntentGuidanceAmbiguityScoreRatio();
        double margin = runtimeSettingService.chatIntentGuidanceAmbiguityMargin();
        if (ratio >= threshold) {
            log.info("歧义引导直判: ratio={} threshold={}", ratio, threshold);
            return true;
        }
        if (ratio >= threshold - margin) {
            return confirmAmbiguityByLlm(question, ranked, nodeByCode);
        }
        return false;
    }

    /**
     * 边界分数交给 LLM 二次判断；调用失败时按 ragent 策略降级为触发澄清。
     */
    private boolean confirmAmbiguityByLlm(
        String question,
        List<ConversationIntentCandidate> ranked,
        Map<String, ChatIntentNode> nodeByCode
    ) {
        try {
            String prompt = promptTemplateLoader.render("guidance-ambiguity-check", Map.of(
                "question", StrUtil.blankToDefault(question, ""),
                "candidates", buildCandidateSummary(ranked, nodeByCode)
            ));
            String raw = aiPromptExecutionService.complete(prompt, StrUtil.blankToDefault(question, ""));
            JSONObject root = JSONUtil.parseObj(stripMarkdownCodeFence(raw));
            return Boolean.TRUE.equals(root.getBool("ambiguous"));
        } catch (Exception exception) {
            log.warn("歧义引导 LLM 二次确认失败，降级为触发澄清: question={}", StrUtil.maxLength(question, 120), exception);
            return true;
        }
    }

    /**
     * 兼容模型按 Markdown fenced code block 返回 JSON 的情况，保持与 ragent 二次确认器一致。
     */
    private String stripMarkdownCodeFence(String raw) {
        String cleaned = StrUtil.trim(raw);
        if (!StrUtil.startWith(cleaned, "```")) {
            return cleaned;
        }
        int firstLineEnd = cleaned.indexOf('\n');
        if (firstLineEnd >= 0) {
            cleaned = cleaned.substring(firstLineEnd + 1);
        }
        cleaned = StrUtil.trim(cleaned);
        if (StrUtil.endWith(cleaned, "```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return StrUtil.trim(cleaned);
    }

    /**
     * 构造 LLM 复核用候选摘要，包含 ID、路径和分数，避免传入完整节点对象。
     */
    private String buildCandidateSummary(List<ConversationIntentCandidate> ranked, Map<String, ChatIntentNode> nodeByCode) {
        StringBuilder builder = new StringBuilder();
        for (ConversationIntentCandidate candidate : ranked) {
            builder.append("- id=").append(candidate.node().getIntentCode())
                .append(", score=").append(candidate.score())
                .append(", path=").append(conversationIntentPathResolver.resolveFullPath(candidate.node(), nodeByCode))
                .append("\n");
        }
        return builder.toString().trim();
    }

    /**
     * 按系统配置裁剪候选数量，配置异常小于 2 时由调用方统一判定为不提示。
     */
    private List<ConversationIntentCandidate> trimRankedOptions(List<ConversationIntentCandidate> ranked) {
        int maxOptions = runtimeSettingService.chatIntentGuidanceMaxOptions();
        if (ranked.size() <= maxOptions) {
            return ranked;
        }
        return ranked.subList(0, Math.max(maxOptions, 0));
    }

    /**
     * 仅在最终确认触发澄清时打印 INFO 日志，便于和“候选接近但最终跳过”的调试日志区分。
     */
    private void logAmbiguityTriggered(
        String question,
        List<ConversationIntentCandidate> candidates,
        Map<String, ChatIntentNode> nodeByCode
    ) {
        log.info(
            "歧义引导触发: question={}, topic={}, 候选数={}, 候选={}",
            StrUtil.maxLength(question, 120),
            StrUtil.blankToDefault(candidates.getFirst().node().getName(), "当前主题"),
            candidates.size(),
            buildTriggeredCandidateSummary(candidates, nodeByCode)
        );
    }

    /**
     * 将触发澄清的候选压缩成单行摘要，避免完整提示词进入业务日志。
     */
    private String buildTriggeredCandidateSummary(
        List<ConversationIntentCandidate> candidates,
        Map<String, ChatIntentNode> nodeByCode
    ) {
        return candidates.stream()
            .map(candidate -> candidate.node().getIntentCode()
                + "@"
                + conversationIntentPathResolver.resolveSystemIdentity(candidate.node(), nodeByCode)
                + ":"
                + candidate.score())
            .toList()
            .toString();
    }

    /**
     * 歧义候选组，携带渲染提示所需的主题、候选和节点索引。
     */
    public record AmbiguityGroup(
        String topicName,
        List<ConversationIntentCandidate> rankedCandidates,
        Map<String, ChatIntentNode> nodeByCode
    ) {
    }
}
