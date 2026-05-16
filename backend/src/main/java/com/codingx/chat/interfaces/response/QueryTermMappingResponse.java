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
    Long id,
    String sourceTerm,
    String targetTerm,
    Integer matchType,
    Integer priority,
    boolean enabled,
    String remark,
    LocalDateTime createTime,
    LocalDateTime updateTime
) {
}

