package com.codingx.chat.interfaces.request;

/**
 * 定义关键词映射更新请求字段。
 * @param sourceTerm 原始词。
 * @param targetTerm 目标词。
 * @param matchType 匹配类型。
 * @param priority 优先级。
 * @param enabled 是否启用。
 * @param remark 备注。
 */
public record QueryTermMappingUpdateRequest(
    String sourceTerm,
    String targetTerm,
    Integer matchType,
    Integer priority,
    Boolean enabled,
    String remark
) {
}

