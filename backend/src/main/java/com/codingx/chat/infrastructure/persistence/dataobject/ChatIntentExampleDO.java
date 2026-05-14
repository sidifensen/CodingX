package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义意图示例表的数据对象映射。
 */
@Data
@TableName("chat_intent_example")
public class ChatIntentExampleDO {
    @TableId("id") private Long id;
    @TableField("intent_code") private String intentCode;
    @TableField("example_text") private String exampleText;
    @TableField("sort_no") private Integer sortNo;
    @TableField("created_at") private LocalDateTime createdAt;
}
