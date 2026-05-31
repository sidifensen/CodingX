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
    @TableId("id") private Long id; // 附件主键。
    @TableField("run_id") private Long runId; // 附件参与的执行 run 标识，可为空。
    @TableField("conversation_id") private Long conversationId; // 附件归属会话标识，上传时未绑定会话可为空。
    @TableField("message_id") private Long messageId; // 附件归属消息标识，发送完成前可为空。
    @TableField("uploaded_by") private Long uploadedBy; // 上传用户标识。
    @TableField("attachment_type") private String attachmentType; // 附件类型，image 表示图片，file 表示普通文件。
    @TableField("file_name") private String fileName; // 原始文件名。
    @TableField("file_ext") private String fileExt; // 小写文件扩展名。
    @TableField("mime_type") private String mimeType; // 文件媒体类型。
    @TableField("file_size") private Long fileSize; // 文件大小，单位字节。
    @TableField("storage_key") private String storageKey; // 对象存储中的内部键。
    @TableField("preview_url") private String previewUrl; // 前端预览或下载地址。
    @TableField("content_summary") private String contentSummary; // 文本附件内容摘要，可为空。
    @TableField("status") private String status; // 附件处理状态。
    @TableField("created_at") private LocalDateTime createdAt; // 附件创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // 附件最近更新时间。
    @TableField("deleted") private Integer deleted; // 逻辑删除标记，1 表示已删除。
}
