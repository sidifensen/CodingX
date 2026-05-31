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
    @TableId("id") private Long id; // 示例问题主键。
    @TableField("question_text") private String questionText; // 欢迎区展示的问题文案。
    @TableField("category") private String category; // 问题分类标签。
    @TableField("enabled") private Integer enabled; // 启用状态，1 表示展示。
    @TableField("sort_no") private Integer sortNo; // 排序号，数值越小越靠前。
    @TableField("created_at") private LocalDateTime createdAt; // 创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // 最近更新时间。
    @TableField("deleted") private Integer deleted; // 逻辑删除标记，1 表示已删除。
}
