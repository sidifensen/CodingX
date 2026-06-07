package com.codingx.governance.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 长期记忆表数据对象，对应 governance_long_term_memory。
 */
@Data
@TableName("governance_long_term_memory")
public class GovernanceLongTermMemoryDO {

    @TableId("id") private Long id; // 长期记忆主键ID。
    @TableField("memory_scope") private String memoryScope; // 记忆范围。
    @TableField("user_id") private Long userId; // 用户 ID。
    @TableField("workspace_id") private Long workspaceId; // 工作空间 ID。
    @TableField("memory_key") private String memoryKey; // 记忆去重键。
    @TableField("content") private String content; // 记忆内容。
    @TableField("status") private String status; // 记忆状态。
    @TableField("source_type") private String sourceType; // 来源类型。
    @TableField("source_conversation_id") private Long sourceConversationId; // 来源会话 ID。
    @TableField("source_message_id") private Long sourceMessageId; // 来源消息 ID。
    @TableField("keyword_json") private String keywordJson; // 关键词 JSON。
    @TableField("confidence_score") private BigDecimal confidenceScore; // 置信分数。
    @TableField("last_used_at") private LocalDateTime lastUsedAt; // 最近使用时间。
    @TableField("created_at") private LocalDateTime createdAt; // 创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // 更新时间。
    @TableField("deleted") private Integer deleted; // 逻辑删除标记。
}
