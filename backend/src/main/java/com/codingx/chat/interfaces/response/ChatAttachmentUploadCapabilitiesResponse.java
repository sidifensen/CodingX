package com.codingx.chat.interfaces.response;

/**
 * 定义聊天附件上传能力响应结构。
 * @param maxFileSizeBytes 单个附件最大字节数，来源于运行时配置。
 * @param maxFileCount 单条消息最多可携带的附件数量。
 */
public record ChatAttachmentUploadCapabilitiesResponse(
    Long maxFileSizeBytes, // 单个附件最大字节数，来源于运行时配置。
    Long maxFileCount // 单条消息最多可携带的附件数量。
) {
}
