package com.codingx.chat.application.service;

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

    /**
     * 节点仓储依赖。
     */
    private final ChatIntentNodeRepository chatIntentNodeRepository;

    /**
     * 示例仓储依赖。
     */
    private final ChatIntentExampleRepository chatIntentExampleRepository;

    /**
     * 意图解析器依赖。
     */
    private final ConversationIntentResolver conversationIntentResolver;

    /**
     * 对当前问题进行分流，返回后续动作决策。
     * @param question 当前问题。
     * @return 决策结果。
     */
    public ConversationIntentDecision route(String question) {
        if (question == null || question.length() < 8) {
            return new ConversationIntentDecision("clarify.ambiguity", ConversationIntentAction.CLARIFY, "请补充你指的是哪一部分");
        }
        List<ChatIntentNode> nodes = chatIntentNodeRepository.findEnabledNodes();
        List<ChatIntentExample> examples = new ArrayList<>();
        for (ChatIntentNode node : nodes) {
            examples.addAll(chatIntentExampleRepository.findByIntentCode(node.getIntentCode()));
        }
        String intentCode = conversationIntentResolver.resolveIntent(question, nodes, examples);
        if (intentCode.startsWith("search")) {
            return new ConversationIntentDecision(intentCode, ConversationIntentAction.SEARCH, null);
        }
        return new ConversationIntentDecision(intentCode, ConversationIntentAction.DIRECT, null);
    }
}
