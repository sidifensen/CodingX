package com.codingx.chat.application.service;

import java.util.List;

/**
 * Prompt 驱动的问题改写结果，供后续搜索、意图识别和多问题拆分复用。
 * @param rewrite 改写后的主问题，保留用户原意并补齐上下文。
 * @param shouldSplit true 表示模型建议拆分为多个子问题分别处理。
 * @param subQuestions 子问题列表，shouldSplit 为 false 时通常为空列表。
 */
public record ConversationRewriteResult(
    String rewrite, // 改写后的主问题，保留用户原意并补齐上下文。
    boolean shouldSplit, // true 表示模型建议拆分为多个子问题分别处理。
    List<String> subQuestions // 子问题列表，shouldSplit 为 false 时通常为空列表。
) {
}
