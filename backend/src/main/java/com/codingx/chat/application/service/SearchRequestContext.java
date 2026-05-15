package com.codingx.chat.application.service;

/**
 * 描述一次搜索请求的统一上下文。
 * @param question 用户问题。
 */
public record SearchRequestContext(String question) {
}
