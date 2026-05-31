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
    @TableId("id") private Long id; // 聊天产物主键。
    @TableField("run_id") private Long runId; // 生成该产物的执行 run 标识。
    @TableField("message_id") private Long messageId; // 产物关联的助手消息主键。
    @TableField("conversation_id") private Long conversationId; // 产物所属会话主键。
    @TableField("artifact_type") private String artifactType; // 产物类型，例如 document、html 或 file。
    @TableField("name") private String name; // 产物展示名称。
    @TableField("mime_type") private String mimeType; // 产物媒体类型，用于下载或预览。
    @TableField("storage_path") private String storagePath; // 产物存储路径或对象键。
    @TableField("content_preview") private String contentPreview; // 产物内容预览，可为空。
    @TableField("metadata_json") private String metadataJson; // 产物扩展元数据 JSON。
    @TableField("created_at") private LocalDateTime createdAt; // 产物创建时间。
}
