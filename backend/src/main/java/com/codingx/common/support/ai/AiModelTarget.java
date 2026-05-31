package com.codingx.common.support.ai;

import com.codingx.config.AiProperties;

/**
 * 描述一次路由执行中的具体模型目标，封装候选定义与 provider 配置。
 * @param id 模型唯一标识。
 * @param candidate 模型候选配置。
 * @param provider provider 连接配置。
 */
public record AiModelTarget(
    String id, // 本次路由使用的模型候选唯一标识。
    AiProperties.ChatCandidate candidate, // 聊天候选配置，包含 provider、model、优先级和能力标记。
    AiProperties.Provider provider // provider 连接配置，包含 baseUrl、apiKey 和 endpoint 信息。
) {
}

