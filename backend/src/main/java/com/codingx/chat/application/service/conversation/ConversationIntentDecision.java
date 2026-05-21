package com.codingx.chat.application.service;

/**
 * 表示一次意图分流的决策结果。
 */
public record ConversationIntentDecision(
    String intentCode,
    ConversationIntentAction action,
    String reply
) {
}
