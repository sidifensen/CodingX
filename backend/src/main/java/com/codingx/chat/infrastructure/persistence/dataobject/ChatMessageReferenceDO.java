package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义参考来源表的数据对象映射。
 */
@Data
@TableName("chat_message_reference")
public class ChatMessageReferenceDO {
    @TableId("id") private Long id;
    @TableField("run_id") private Long runId;
    @TableField("message_id") private Long messageId;
    @TableField("conversation_id") private Long conversationId;
    @TableField("source_type") private String sourceType;
    @TableField("title") private String title;
    @TableField("url") private String url;
    @TableField("site_name") private String siteName;
    @TableField("snippet") private String snippet;
    @TableField("rank_no") private Integer rankNo;
    @TableField("metadata_json") private String metadataJson;
    @TableField("created_at") private LocalDateTime createdAt;
}
