package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示聊天执行过程中生成的文件产物记录。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMessageArtifact {

    /** 产物主键，数据库生成，新建产物时为空。 */
    private Long id;
    /** 产物所属执行运行 ID，用于追踪产物来自哪次模型或工具执行。 */
    private Long runId;
    /** 产物关联的助手消息 ID，生成消息落库前可为空。 */
    private Long messageId;
    /** 产物所属会话 ID，用于按会话查询生成文件。 */
    private Long conversationId;
    /** 产物类型，如 file、html、markdown，用于前端选择预览方式。 */
    private String artifactType;
    /** 产物展示名称，通常来自工具生成文件名或服务端默认命名。 */
    private String name;
    /** 产物 MIME 类型，可为空，前端下载或预览时作为内容类型参考。 */
    private String mimeType;
    /** 产物存储路径，指向后端持久化文件位置。 */
    private String storagePath;
    /** 内容预览片段，避免列表页读取完整文件。 */
    private String contentPreview;
    /** 扩展元数据 JSON，保存大小、来源工具等非固定字段。 */
    private String metadataJson;
    /** 产物创建时间，由仓储写入时生成。 */
    private LocalDateTime createdAt;
}
