package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatIntentNode;
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

    /** Prompt 模板加载器，用于渲染最终返回给用户的澄清提示。 */
    private final PromptTemplateLoader promptTemplateLoader;
    /** 歧义检测器，用于判断当前候选是否需要先让用户选择系统。 */
    private final ConversationIntentAmbiguityDetector conversationIntentAmbiguityDetector;
    /** 意图路径解析器，用于把候选节点转换为用户可读的完整路径。 */
    private final ConversationIntentPathResolver conversationIntentPathResolver;

    /**
     * 判断当前候选是否需要澄清，并在需要时返回渲染后的提示。
     * @param question 用户问题。
     * @param candidates 意图候选。
     * @param allNodes 当前启用节点全集。
     * @return 澄清提示；无需澄清时返回 null。
     */
    @ConversationTraceNode(name = "intent-guidance", type = "INTENT")
    public String buildGuidancePrompt(String question, List<ConversationIntentCandidate> candidates, List<ChatIntentNode> allNodes) {
        ConversationIntentAmbiguityDetector.AmbiguityGroup group = conversationIntentAmbiguityDetector.detect(
            question,
            candidates,
            allNodes
        );
        if (group == null) {
            return null;
        }
        return promptTemplateLoader.render("guidance-prompt", Map.of(
            "topic_name", StrUtil.blankToDefault(group.topicName(), "当前主题"),
            "options", renderOptions(group)
        ));
    }

    /**
     * 渲染编号候选选项，保持用户可直接回复序号的简洁格式。
     * @param group 歧义候选组。
     * @return 编号选项文本。
     */
    private String renderOptions(ConversationIntentAmbiguityDetector.AmbiguityGroup group) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < group.rankedCandidates().size(); index++) {
            ConversationIntentCandidate candidate = group.rankedCandidates().get(index);
            builder.append(index + 1)
                .append(") ")
                .append(conversationIntentPathResolver.resolveFullPath(candidate.node(), group.nodeByCode()))
                .append("\n");
        }
        return builder.toString().trim();
    }
}
