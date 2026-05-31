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

    /** 映射规则主键，数据库生成，新建规则时为空。 */
    private Long id;
    /** 原始查询词，来自用户问题或模型改写后的检索词。 */
    private String sourceTerm;
    /** 目标查询词，命中规则后用于替换或补充检索表达。 */
    private String targetTerm;
    /**
     * 匹配类型：1-精确匹配，2-前缀匹配，3-正则匹配，4-整词匹配。
     */
    private Integer matchType;
    /**
     * 优先级，数值越小优先级越高。
     */
    private Integer priority;
    /** 启用状态，1 表示规则生效，0 表示仅保留不参与匹配。 */
    private Integer enabled;
    /**
     * 管理端备注信息。
     */
    private String remark;
    /** 规则创建时间，由仓储写入时生成。 */
    private LocalDateTime createdAt;
    /** 规则更新时间，管理端修改规则时同步更新。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记，0 表示有效，1 表示已删除。 */
    private Integer deleted;
}
