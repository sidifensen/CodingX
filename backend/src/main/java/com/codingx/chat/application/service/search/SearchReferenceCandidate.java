package com.codingx.chat.application.service;

/**
 * 表示搜索 provider 返回的一条标准化来源候选。
 * @param title 来源标题，通常来自搜索结果标题，不能为空时才会进入候选集合。
 * @param url 来源 URL，用于前端跳转和站点名解析。
 * @param siteName 来源站点名，通常由 URL 主机名提取，可为空。
 * @param snippet 来源摘要，来自 provider 摘要字段或正文片段，可为空字符串。
 * @param score 来源相关度分数，provider 不返回时使用默认低分。
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
