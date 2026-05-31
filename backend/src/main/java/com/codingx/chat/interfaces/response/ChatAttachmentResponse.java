package com.codingx.chat.interfaces.response;

import java.time.LocalDateTime;

/**
 * 聊天附件响应结构，供消息列表和附件预览接口展示上传文件的基础元数据。
 *
 * @param id 附件主键，前端下载和预览时作为稳定标识。
 * @param conversationId 关联会话标识，未绑定会话的临时附件可为空。
 * @param messageId 关联消息标识，上传后未发送成功前可为空。
 * @param attachmentType 附件类型，image 表示图片预览，file 表示普通文件。
 * @param fileName 用户上传时的原始文件名。
 * @param fileExt 小写文件扩展名，用于前端图标和后端内容解析判断。
 * @param mimeType 浏览器或存储服务识别的媒体类型。
 * @param fileSize 文件大小，单位字节。
 * @param previewUrl 前端读取附件内容或缩略图的相对地址。
 * @param contentSummary 文本类附件提取出的内容摘要，可为空。
 * @param status 附件处理状态，通常为 UPLOADED。
 * @param createdAt 附件上传时间。
 */
public record ChatAttachmentResponse(
    Long id, // 附件主键，前端下载和预览时作为稳定标识。
    Long conversationId, // 关联会话标识，未绑定会话的临时附件可为空。
    Long messageId, // 关联消息标识，上传后未发送成功前可为空。
    String attachmentType, // 附件类型，image 表示图片预览，file 表示普通文件。
    String fileName, // 用户上传时的原始文件名。
    String fileExt, // 小写文件扩展名，用于前端图标和后端内容解析判断。
    String mimeType, // 浏览器或存储服务识别的媒体类型。
    Long fileSize, // 文件大小，单位字节。
    String previewUrl, // 前端读取附件内容或缩略图的相对地址。
    String contentSummary, // 文本类附件提取出的内容摘要，可为空。
    String status, // 附件处理状态，通常为 UPLOADED。
    LocalDateTime createdAt // 附件上传时间。
) {
}
