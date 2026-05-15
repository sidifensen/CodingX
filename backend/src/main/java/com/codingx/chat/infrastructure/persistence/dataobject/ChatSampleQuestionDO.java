package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义示例问题表的数据对象映射。
 */
@Data
@TableName("chat_sample_question")
public class ChatSampleQuestionDO {
    @TableId("id") private Long id;
    @TableField("question_text") private String questionText;
    @TableField("category") private String category;
    @TableField("enabled") private Integer enabled;
    @TableField("sort_no") private Integer sortNo;
    @TableField("created_at") private LocalDateTime createdAt;
    @TableField("updated_at") private LocalDateTime updatedAt;
    @TableField("deleted") private Integer deleted;
}
