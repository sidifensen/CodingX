package com.codingx.chat.application.service;

/**
 * 表示搜索 provider 返回的一条标准化来源候选。
 */
public record SearchReferenceCandidate(
    String title,
    String url,
    String siteName,
    String snippet
) {
}
