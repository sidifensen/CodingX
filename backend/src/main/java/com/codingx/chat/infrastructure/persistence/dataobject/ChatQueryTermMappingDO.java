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
    @TableId("id") private Long id;
    @TableField("source_term") private String sourceTerm;
    @TableField("target_term") private String targetTerm;
    @TableField("mapping_type") private String mappingType;
    @TableField("enabled") private Integer enabled;
    @TableField("sort_no") private Integer sortNo;
    @TableField("created_at") private LocalDateTime createdAt;
    @TableField("updated_at") private LocalDateTime updatedAt;
    @TableField("deleted") private Integer deleted;
}
