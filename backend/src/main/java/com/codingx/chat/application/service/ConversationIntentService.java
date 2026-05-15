package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
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
        List<ChatIntentExample> examples = new ArrayList<>();
        for (ChatIntentNode node : nodes) {
            examples.addAll(chatIntentExampleRepository.findByIntentCode(node.getIntentCode()));
        }
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
            return new ConversationIntentDecision(topNode.getIntentCode(), ConversationIntentAction.DIRECT, buildSystemReply(topNode.getIntentCode()));
        }
        return new ConversationIntentDecision(topNode.getIntentCode(), ConversationIntentAction.SEARCH, null);
    }

    /**
     * 为系统意图提供确定性的直答短路，避免这类问题继续走模型自由生成。
     * @param intentCode 命中的系统意图编码。
     * @return 直答内容。
     */
    private String buildSystemReply(String intentCode) {
        if ("sys-welcome".equals(intentCode)) {
            return "你好，我是 CodingX 的知识助手。你可以问我项目结构、系统配置、IT 支持、业务流程或公开技术资料。";
        }
        if ("sys-about-bot".equals(intentCode)) {
            return "我是基于当前接入的大语言模型服务和搜索能力构建的 CodingX 知识助手，底层模型会由平台配置动态切换，我主要负责项目知识、系统说明、IT 支持和公开技术信息整理。";
        }
        return "你好，我是 CodingX 的知识助手，很高兴为你提供帮助。";
    }
}
