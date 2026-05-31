package com.codingx.chat.interfaces.request;

/**
 * 定义关键词映射创建请求字段。
 * @param sourceTerm 原始词。
 * @param targetTerm 目标词。
 * @param matchType 匹配类型。
 * @param priority 优先级。
 * @param enabled 是否启用。
 * @param remark 备注。
 */
public record QueryTermMappingCreateRequest(
    String sourceTerm, // 用户输入的原始查询词，不能为空；服务层会 trim 后保存。
    String targetTerm, // 命中原始词后替换或扩展使用的目标词，不能为空；服务层会 trim 后保存。
    Integer matchType, // 匹配类型，空值时服务层默认使用 1。
    Integer priority, // 规则优先级，数值越大越优先；空值时服务层默认使用 0。
    Boolean enabled, // 是否启用该映射规则，空值时按启用处理。
    String remark // 管理端维护备注，可为空；服务层会 trim 后保存空值为 null。
) {
}

