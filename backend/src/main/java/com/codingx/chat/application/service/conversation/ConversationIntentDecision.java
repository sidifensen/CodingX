package com.codingx.chat.application.service;

/**
 * 意图分流决策结果，决定聊天链路继续模型生成、触发澄清、执行搜索或调用工具。
 *
 * @param intentCode 命中的意图编码，可为空；为空表示未能匹配到明确意图。
 * @param action 意图处理动作，决定聊天链路继续对话、澄清、搜索或调用工具。
 * @param reply 直接返回给用户的澄清或系统回复，可为空；需要继续模型生成时通常为空。
 */
public record ConversationIntentDecision(
    String intentCode, // 命中的意图编码，可为空；为空表示未能匹配到明确意图。
    ConversationIntentAction action, // 意图处理动作，决定聊天链路继续对话、澄清、搜索或调用工具。
    String reply // 直接返回给用户的澄清或系统回复，可为空；需要继续模型生成时通常为空。
) {
}
