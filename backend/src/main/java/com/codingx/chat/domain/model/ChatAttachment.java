package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示用户上传并用于聊天上下文的附件记录。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatAttachment {

    /** 附件主键。 */
    private Long id;

    /** 附件参与的执行 run 标识，上传后未进入消息执行前可为空。 */
    private Long runId;

    /** 附件归属会话标识，临时上传阶段可为空。 */
    private Long conversationId;

    /** 附件归属消息标识，发送成功绑定前可为空。 */
    private Long messageId;

    /** 上传用户标识，用于访问控制和发送消息时的归属校验。 */
    private Long uploadedBy;

    /** 附件展示类型，image 表示图片预览，file 表示普通文件。 */
    private String attachmentType;

    /** 用户上传时的原始文件名。 */
    private String fileName;

    /** 小写文件扩展名，用于白名单校验和摘要解析。 */
    private String fileExt;

    /** 文件媒体类型，下载预览时写入 HTTP Content-Type。 */
    private String mimeType;

    /** 文件大小，单位字节。 */
    private Long fileSize;

    /** 对象存储内部键，前端不可直接使用。 */
    private String storageKey;

    /** 前端预览或下载附件内容的接口地址。 */
    private String previewUrl;

    /** 文本/PDF 附件抽取出的模型上下文摘要，可为空。 */
    private String contentSummary;

    /** 附件处理状态，当前上传成功后为 UPLOADED。 */
    private String status;

    /** 附件创建时间。 */
    private LocalDateTime createdAt;

    /** 附件最近更新时间。 */
    private LocalDateTime updatedAt;

    /** 逻辑删除标记，1 表示已删除。 */
    private Integer deleted;
}
