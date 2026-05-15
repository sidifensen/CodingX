package com.codingx.chat.application.service;

/**
 * 表示搜索 provider 返回的一条标准化来源候选。
 */
public record SearchReferenceCandidate(
    String title,
    String url,
    String siteName,
    String snippet,
    Double score
) {

    /**
     * 兼容旧构造方式，默认给一个最低权重分数。
     * @param title 标题。
     * @param url 链接。
     * @param siteName 站点名。
     * @param snippet 摘要。
     */
    public SearchReferenceCandidate(String title, String url, String siteName, String snippet) {
        this(title, url, siteName, snippet, 0D);
    }
}
