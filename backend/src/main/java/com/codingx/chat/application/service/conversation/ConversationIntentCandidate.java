package com.codingx.chat.application.service;

import com.codingx.chat.domain.model.ChatIntentNode;

/**
 * 意图识别候选结果，包含命中的意图树节点和匹配分数。
 * @param node 命中的意图节点，必须来自当前启用的意图树。
 * @param score 匹配分数，数值越高表示越接近用户问题。
 */
public record ConversationIntentCandidate(
    ChatIntentNode node, // 命中的意图节点，必须来自当前启用的意图树。
    double score // 匹配分数，数值越高表示越接近用户问题。
) {
}
