package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.domain.model.ChatIntentExample;
import com.codingx.chat.domain.model.ChatIntentNode;
import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import com.codingx.mcp.application.executor.WeatherQuestionParser;
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
    private final ConversationIntentResolver conversationIntentResolver;
    private final ConversationIntentGuidanceService conversationIntentGuidanceService;
    private final WeatherQuestionParser weatherQuestionParser;

    /**
     * 对当前问题进行分流，返回后续动作决策。
     * @param question 当前问题。
     * @return 决策结果。
     */
    public ConversationIntentDecision route(String question) {
        return route(question, true);
    }

    /**
     * 对当前问题进行分流，并结合是否允许 MCP 做路由约束。
     * @param question 当前问题。
     * @param mcpEnabled 当前消息是否允许 MCP。
     * @return 决策结果。
     */
    public ConversationIntentDecision route(String question, boolean mcpEnabled) {
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
            if (!mcpEnabled) {
                return new ConversationIntentDecision(topNode.getIntentCode(), ConversationIntentAction.MCP_DISABLED, "当前消息未连接 MCP");
            }
            if (weatherMcpMissingCity(topNode, question)) {
                return new ConversationIntentDecision(topNode.getIntentCode(), ConversationIntentAction.CLARIFY, "请明确你想查询哪个城市的天气，例如：上海今天天气怎么样。");
            }
            return new ConversationIntentDecision(topNode.getIntentCode(), ConversationIntentAction.MCP, null);
        }
        if ("search".equalsIgnoreCase(topNode.getIntentType())) {
            return new ConversationIntentDecision(topNode.getIntentCode(), ConversationIntentAction.SEARCH, null);
        }
        // 非法或未知类型回退为 DIRECT，避免把错误配置误导到联网搜索链路。
        return new ConversationIntentDecision(topNode.getIntentCode(), ConversationIntentAction.DIRECT, null);
    }

    /**
     * 天气工具需要城市槽位；缺少城市时在路由层澄清，避免工具错误结果进入模型上下文。
     */
    private boolean weatherMcpMissingCity(ChatIntentNode node, String question) {
        return node != null
            && StrUtil.equals("weather_query", node.getMcpToolId())
            && !weatherQuestionParser.hasCity(question);
    }

    /**
     * 从节点 JSON 示例中汇总识别样本，避免维护已下线的独立示例表。
     * @param nodes 启用中的节点集合。
     * @return 汇总后的示例列表。
     */
    private List<ChatIntentExample> collectExamples(List<ChatIntentNode> nodes) {
        List<ChatIntentExample> examples = new ArrayList<>();
        for (ChatIntentNode node : nodes) {
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
