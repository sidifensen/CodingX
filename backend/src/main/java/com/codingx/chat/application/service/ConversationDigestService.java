package com.codingx.chat.application.service;

import org.springframework.stereotype.Service;

/**
 * 负责判断何时应该触发会话摘要压缩。
 */
@Service
public class ConversationDigestService {

    private final int summarizeThreshold;

    /**
     * 使用默认阈值创建摘要服务。
     */
    public ConversationDigestService() {
        this(12);
    }

    /**
     * 使用指定阈值创建摘要服务，便于测试与后续配置化。
     * @param summarizeThreshold 摘要阈值。
     */
    public ConversationDigestService(int summarizeThreshold) {
        this.summarizeThreshold = Math.max(1, summarizeThreshold);
    }

    /**
     * 判断当前消息数量是否已经达到摘要触发阈值。
     * @param messages 会话消息集合。
     * @return 是否应触发摘要。
     */
    public boolean shouldSummarize(java.util.List<?> messages) {
        return messages.size() >= summarizeThreshold;
    }
}
