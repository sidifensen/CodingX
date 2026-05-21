package com.codingx.chat.application.service;

/**
 * 描述一次搜索请求的统一上下文。
 * @param question 用户问题。
 * @param timeoutMs 单次搜索超时窗口毫秒。
 */
public record SearchRequestContext(String question, long timeoutMs) {

    /**
     * 兼容旧调用方，仅提供问题时使用默认超时窗口。
     * @param question 用户问题。
     */
    public SearchRequestContext(String question) {
        this(question, 15_000L);
    }
}
