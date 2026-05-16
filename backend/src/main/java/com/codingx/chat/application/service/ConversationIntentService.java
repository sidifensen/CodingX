package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.domain.model.ChatIntentExample;
import com.codingx.chat.domain.model.ChatIntentNode;
import com.codingx.chat.domain.repository.ChatIntentExampleRepository;
import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责加载意图配置并将问题分流到直答、搜索或澄清路径。
 */
@Service
@RequiredArgsConstructor
public class ConversationIntentService {

    private final ChatIntentNodeRepository chatIntentNodeRepository;
    private final ChatIntentExampleRepository chatIntentExampleRepository;
    private final ConversationIntentResolver conversationIntentResolver;
    private final ConversationIntentGuidanceService conversationIntentGuidanceService;

    /**
     * 对当前问题进行分流，返回后续动作决策。
     * @param question 当前问题。
     * @return 决策结果。
     */
    public ConversationIntentDecision route(String question) {
        if (StrUtil.isBlank(question)) {
            return new ConversationIntentDecision("clarify.ambiguity", ConversationIntentAction.CLARIFY, "请补充你的具体问题");
        }
        List<ChatIntentNode> nodes = chatIntentNodeRepository.findEnabledNodes();
        List<ChatIntentExample> examples = collectExamples(nodes);
        List<ConversationIntentCandidate> candidates = conversationIntentResolver.resolveCandidates(question, nodes, examples);
        String guidancePrompt = conversationIntentGuidanceService.buildGuidancePrompt(question, candidates, nodes);
        if (StrUtil.isNotBlank(guidancePrompt)) {
            return new ConversationIntentDecision("clarify.ambiguity", ConversationIntentAction.CLARIFY, guidancePrompt);
        }
        if (candidates.isEmpty()) {
            return new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null);
        }
        ChatIntentNode topNode = candidates.getFirst().node();
        if ("system".equalsIgnoreCase(topNode.getIntentType())) {
            return new ConversationIntentDecision(topNode.getIntentCode(), ConversationIntentAction.DIRECT, null);
        }
        if ("mcp".equalsIgnoreCase(topNode.getIntentType())) {
            return new ConversationIntentDecision(topNode.getIntentCode(), ConversationIntentAction.MCP, null);
        }
        return new ConversationIntentDecision(topNode.getIntentCode(), ConversationIntentAction.SEARCH, null);
    }

    /**
     * 兼容读取旧示例表与节点 JSON 示例，确保后台配置和导入种子都能参与识别。
     * @param nodes 启用中的节点集合。
     * @return 汇总后的示例列表。
     */
    private List<ChatIntentExample> collectExamples(List<ChatIntentNode> nodes) {
        List<ChatIntentExample> examples = new ArrayList<>();
        for (ChatIntentNode node : nodes) {
            examples.addAll(chatIntentExampleRepository.findByIntentCode(node.getIntentCode()));
            examples.addAll(parseNodeExamples(node));
        }
        return examples;
    }

    /**
     * 解析节点上的 JSON 示例文本，避免当前项目维护两套必须同步的人工作业数据。
     * @param node 当前节点。
     * @return 解析出的示例集合。
     */
    private List<ChatIntentExample> parseNodeExamples(ChatIntentNode node) {
        if (node == null || StrUtil.isBlank(node.getExamples())) {
            return List.of();
        }
        try {
            JSONArray exampleArray = JSONUtil.parseArray(node.getExamples());
            List<ChatIntentExample> examples = new ArrayList<>();
            int sortNo = 1;
            for (Object item : exampleArray) {
                String exampleText = StrUtil.trim(item == null ? null : item.toString());
                if (StrUtil.isBlank(exampleText)) {
                    continue;
                }
                examples.add(ChatIntentExample.builder()
                    .intentCode(node.getIntentCode())
                    .exampleText(exampleText)
                    .sortNo(sortNo++)
                    .build());
            }
            return examples;
        } catch (Exception exception) {
            return List.of();
        }
    }
}
