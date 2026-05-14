package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义意图树节点表的数据对象映射。
 */
@Data
@TableName("chat_intent_node")
public class ChatIntentNodeDO {
    @TableId("id") private Long id;
    @TableField("intent_code") private String intentCode;
    @TableField("parent_code") private String parentCode;
    @TableField("name") private String name;
    @TableField("description") private String description;
    @TableField("intent_type") private String intentType;
    @TableField("prompt_template") private String promptTemplate;
    @TableField("enabled") private Integer enabled;
    @TableField("sort_no") private Integer sortNo;
    @TableField("created_at") private LocalDateTime createdAt;
    @TableField("updated_at") private LocalDateTime updatedAt;
    @TableField("deleted") private Integer deleted;
}
