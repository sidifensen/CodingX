package com.codingx.chat.application.service;

import java.util.List;

/**
 * 表示一次 Prompt 驱动问题改写的完整结果。
 * @param rewrite 改写后的主问题。
 * @param shouldSplit 是否建议拆分子问题。
 * @param subQuestions 子问题列表。
 */
public record ConversationRewriteResult(
    String rewrite,
    boolean shouldSplit,
    List<String> subQuestions
) {
}
