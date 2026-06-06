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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 负责加载意图配置并将问题分流到直答、搜索或澄清路径。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationIntentService {

    /** 意图节点仓储，用于加载启用意图树和节点示例数据。 */
    private final ChatIntentNodeRepository chatIntentNodeRepository;
    /** 意图分类器，用于根据问题和示例生成候选意图分数。 */
    private final ConversationIntentResolver conversationIntentResolver;
    /** 歧义引导服务，用于在跨系统同名主题时生成澄清提示。 */
    private final ConversationIntentGuidanceService conversationIntentGuidanceService;
    /** 天气问题解析器，用于 MCP 天气意图下判断是否缺少城市参数。 */
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
        // 步骤 1：空问题直接进入澄清分支，避免后续模型或工具链路收到无效输入。
        if (StrUtil.isBlank(question)) {
            ConversationIntentDecision decision = new ConversationIntentDecision("clarify.ambiguity", ConversationIntentAction.CLARIFY, "请补充你的具体问题");
            logIntentDecision(question, mcpEnabled, decision, List.of());
            return decision;
        }
        // 步骤 2：加载启用意图节点和示例，先由分类器给出候选，再由歧义服务判断是否需要追问。
        List<ChatIntentNode> nodes = chatIntentNodeRepository.findEnabledNodes();
        List<ChatIntentExample> examples = collectExamples(nodes);
        List<ConversationIntentCandidate> candidates = conversationIntentResolver.resolveCandidates(question, nodes, examples);
        String guidancePrompt = conversationIntentGuidanceService.buildGuidancePrompt(question, candidates, nodes);
        if (StrUtil.isNotBlank(guidancePrompt)) {
            ConversationIntentDecision decision = new ConversationIntentDecision("clarify.ambiguity", ConversationIntentAction.CLARIFY, guidancePrompt);
            logIntentDecision(question, mcpEnabled, decision, candidates);
            return decision;
        }
        // 步骤 3：没有候选时回退普通直答，由后续模型生成回答而不是强行搜索或调用工具。
        if (candidates.isEmpty()) {
            ConversationIntentDecision decision = new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null);
            logIntentDecision(question, mcpEnabled, decision, candidates);
            return decision;
        }
        // 步骤 4：按首选节点类型映射后续动作，并在 MCP 未启用或天气缺城市时提前收口。
        ChatIntentNode topNode = candidates.getFirst().node();
        ConversationIntentDecision decision;
        if ("system".equalsIgnoreCase(topNode.getIntentType())) {
            decision = new ConversationIntentDecision(topNode.getIntentCode(), ConversationIntentAction.DIRECT, null);
        } else if ("mcp".equalsIgnoreCase(topNode.getIntentType())) {
            if (!mcpEnabled) {
                decision = new ConversationIntentDecision(topNode.getIntentCode(), ConversationIntentAction.MCP_DISABLED, "当前消息未连接 MCP");
            } else if (weatherMcpMissingCity(topNode, question)) {
                decision = new ConversationIntentDecision(topNode.getIntentCode(), ConversationIntentAction.CLARIFY, "请明确你想查询哪个城市的天气，例如：上海今天天气怎么样。");
            } else {
                decision = new ConversationIntentDecision(topNode.getIntentCode(), ConversationIntentAction.MCP, null);
            }
        } else if ("search".equalsIgnoreCase(topNode.getIntentType())) {
            decision = new ConversationIntentDecision(topNode.getIntentCode(), ConversationIntentAction.SEARCH, null);
        } else {
            // 非法或未知类型回退为 DIRECT，避免把错误配置误导到联网搜索链路。
            decision = new ConversationIntentDecision(topNode.getIntentCode(), ConversationIntentAction.DIRECT, null);
        }
        // 步骤 5：最终决策统一打印日志，方便排查本轮进入直答、搜索、MCP 或澄清分支。
        logIntentDecision(question, mcpEnabled, decision, candidates);
        return decision;
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

    /**
     * 打印最终意图路由结果，便于从日志快速判断本轮进入直答、搜索、MCP 或澄清分支。
     */
    private void logIntentDecision(
        String question,
        boolean mcpEnabled,
        ConversationIntentDecision decision,
        List<ConversationIntentCandidate> candidates
    ) {
        ConversationIntentCandidate top = candidates.isEmpty() ? null : candidates.getFirst();
        log.info(
            "意图决策: 问题={}, 动作={}, 意图={}, 首选={}, 分数={}, 候选={}, MCP={}",
            StrUtil.maxLength(question, 120),
            decision.action(),
            decision.intentCode(),
            top == null ? null : top.node().getIntentCode(),
            top == null ? null : top.score(),
            candidates.size(),
            mcpEnabled
        );
    }
}
