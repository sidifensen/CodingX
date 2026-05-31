package com.codingx.chat.interfaces.response;

import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 定义关键词映射管理端输出结构，避免直接暴露领域对象。
 * @param id 主键。
 * @param sourceTerm 原始词。
 * @param targetTerm 目标词。
 * @param matchType 匹配类型。
 * @param priority 优先级。
 * @param enabled 是否启用。
 * @param remark 备注。
 * @param createTime 创建时间。
 * @param updateTime 更新时间。
 */
@Builder
public record QueryTermMappingResponse(
    Long id, // 关键词映射主键，管理端编辑和删除时使用。
    String sourceTerm, // 原始查询词，来自管理端维护内容。
    String targetTerm, // 命中原始词后使用的目标词。
    Integer matchType, // 匹配类型，当前默认 1。
    Integer priority, // 匹配优先级，数值越大越优先。
    boolean enabled, // 是否启用该映射规则，true 表示参与会话查询词改写。
    String remark, // 管理备注，可为空。
    LocalDateTime createTime, // 规则创建时间。
    LocalDateTime updateTime // 规则最近更新时间。
) {
}

