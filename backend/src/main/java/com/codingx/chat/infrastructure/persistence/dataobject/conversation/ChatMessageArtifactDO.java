package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义聊天产物表的数据对象映射。
 */
@Data
@TableName("chat_message_artifact")
public class ChatMessageArtifactDO {
    @TableId("id") private Long id;
    @TableField("run_id") private Long runId;
    @TableField("message_id") private Long messageId;
    @TableField("conversation_id") private Long conversationId;
    @TableField("artifact_type") private String artifactType;
    @TableField("name") private String name;
    @TableField("mime_type") private String mimeType;
    @TableField("storage_path") private String storagePath;
    @TableField("content_preview") private String contentPreview;
    @TableField("metadata_json") private String metadataJson;
    @TableField("created_at") private LocalDateTime createdAt;
}
