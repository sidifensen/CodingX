package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义查询词映射表的数据对象映射。
 */
@Data
@TableName("chat_query_term_mapping")
public class ChatQueryTermMappingDO {
    @TableId("id") private Long id; // 查询词映射主键。
    @TableField("source_term") private String sourceTerm; // 用户原始查询词或待匹配片段。
    @TableField("target_term") private String targetTerm; // 改写后的目标查询词。
    @TableField("match_type") private Integer matchType; // 匹配方式，区分精确、包含等策略。
    @TableField("priority") private Integer priority; // 匹配优先级，数值越大越优先。
    @TableField("enabled") private Integer enabled; // 启用状态，1 表示参与查询改写。
    @TableField("remark") private String remark; // 管理端备注说明，可为空。
    @TableField("created_at") private LocalDateTime createdAt; // 映射创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // 映射最近更新时间。
    @TableField("deleted") private Integer deleted; // 逻辑删除标记，1 表示已删除。
}
