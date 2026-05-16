package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示查询词归一化映射规则。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatQueryTermMapping {

    private Long id;
    private String sourceTerm;
    private String targetTerm;
    /**
     * 匹配类型：1-精确匹配，2-前缀匹配，3-正则匹配，4-整词匹配。
     */
    private Integer matchType;
    /**
     * 优先级，数值越小优先级越高。
     */
    private Integer priority;
    private Integer enabled;
    /**
     * 管理端备注信息。
     */
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
