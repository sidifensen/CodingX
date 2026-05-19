package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义聊天附件表数据对象映射。
 */
@Data
@TableName("chat_attachment")
public class ChatAttachmentDO {
    @TableId("id") private Long id;
    @TableField("run_id") private Long runId;
    @TableField("conversation_id") private Long conversationId;
    @TableField("message_id") private Long messageId;
    @TableField("uploaded_by") private Long uploadedBy;
    @TableField("attachment_type") private String attachmentType;
    @TableField("file_name") private String fileName;
    @TableField("file_ext") private String fileExt;
    @TableField("mime_type") private String mimeType;
    @TableField("file_size") private Long fileSize;
    @TableField("storage_key") private String storageKey;
    @TableField("preview_url") private String previewUrl;
    @TableField("content_summary") private String contentSummary;
    @TableField("status") private String status;
    @TableField("created_at") private LocalDateTime createdAt;
    @TableField("updated_at") private LocalDateTime updatedAt;
    @TableField("deleted") private Integer deleted;
}
