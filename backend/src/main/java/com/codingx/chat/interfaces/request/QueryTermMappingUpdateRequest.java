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
    String sourceTerm, // 更新后的原始查询词，不能为空；服务层会 trim 后覆盖旧值。
    String targetTerm, // 更新后的目标词，不能为空；服务层会 trim 后覆盖旧值。
    Integer matchType, // 更新后的匹配类型，空值时服务层默认使用 1。
    Integer priority, // 更新后的规则优先级，空值时服务层默认使用 0。
    Boolean enabled, // 更新后的启用状态，空值时按启用处理。
    String remark // 更新后的管理备注，可为空；服务层会 trim 后保存空值为 null。
) {
}

