package com.codingx.chat.application.service;

import com.codingx.chat.domain.model.ChatIntentExample;
import com.codingx.chat.domain.model.ChatIntentNode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 负责根据问题文本、意图节点和示例数据做最小意图分流。
 */
@Service
public class ConversationIntentResolver {

    /**
     * 返回当前问题最匹配的意图编码。
     * @param question 当前问题。
     * @param nodes 启用中的意图节点。
     * @param examples 示例问题。
     * @return 命中的意图编码。
     */
    public String resolveIntent(String question, List<ChatIntentNode> nodes, List<ChatIntentExample> examples) {
        String normalized = question == null ? "" : question.toLowerCase();
        boolean searchLike = normalized.contains("搜索") || normalized.contains("search");
        if (searchLike) {
            return nodes.stream()
                .filter(node -> "search".equalsIgnoreCase(node.getIntentType()))
                .map(ChatIntentNode::getIntentCode)
                .findFirst()
                .orElseGet(() -> examples.stream().findFirst().map(ChatIntentExample::getIntentCode).orElse("search.web"));
        }
        return nodes.stream()
            .findFirst()
            .map(ChatIntentNode::getIntentCode)
            .orElse("chat.normal");
    }
}
