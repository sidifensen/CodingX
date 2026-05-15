package com.codingx.chat.application.service;

import com.codingx.chat.domain.model.ChatIntentNode;

/**
 * 表示一次意图识别后的候选结果，包含命中节点与得分。
 * @param node 命中的意图节点。
 * @param score 匹配分数。
 */
public record ConversationIntentCandidate(
    ChatIntentNode node,
    double score
) {
}
