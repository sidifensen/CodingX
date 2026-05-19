package com.codingx.chat.interfaces.controller;

import com.codingx.chat.application.service.ChatAttachmentService;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.interfaces.response.ChatAttachmentResponse;
import com.codingx.common.model.ApiResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 提供聊天附件上传与预览下载接口。
 */
@RestController
@RequestMapping("/api/chat/attachments")
@RequiredArgsConstructor
public class ChatAttachmentController {

    private final ChatAttachmentService chatAttachmentService;

    /**
     * 上传单个附件，返回附件元数据。
     * @param file 上传文件。
     * @param conversationId 会话主键，可为空。
     * @return 附件信息。
     */
    @PostMapping("/upload")
    public ApiResponse<ChatAttachmentResponse> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "conversationId", required = false) Long conversationId
    ) {
        ChatAttachment attachment = chatAttachmentService.upload(file, conversationId);
        return ApiResponse.success(toResponse(attachment));
    }

    /**
     * 下载或预览附件内容，供前端图片预览弹窗与文件链接复用。
     * @param attachmentId 附件主键。
     * @return 文件二进制响应。
     */
    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<byte[]> content(@PathVariable Long attachmentId) {
        ChatAttachment attachment = chatAttachmentService.requireOwnedAttachment(attachmentId);
        byte[] bytes = chatAttachmentService.downloadContent(attachment);
        String mimeType = attachment.getMimeType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : attachment.getMimeType();
        String safeFileName = attachment.getFileName() == null ? "attachment.bin" : attachment.getFileName();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, mimeType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeFileName + "\"")
            .body(bytes);
    }

    /**
     * 返回允许上传扩展名列表，便于前端同步约束提示。
     * @return 允许类型描述。
     */
    @GetMapping("/upload-capabilities")
    public ApiResponse<Map<String, Object>> capabilities() {
        return ApiResponse.success(Map.of(
            "maxFileSizeBytes", 20L * 1024L * 1024L,
            "maxFileCount", 9L
        ));
    }

    private ChatAttachmentResponse toResponse(ChatAttachment attachment) {
        return new ChatAttachmentResponse(
            attachment.getId(),
            attachment.getConversationId(),
            attachment.getMessageId(),
            attachment.getAttachmentType(),
            attachment.getFileName(),
            attachment.getFileExt(),
            attachment.getMimeType(),
            attachment.getFileSize(),
            attachment.getPreviewUrl(),
            attachment.getContentSummary(),
            attachment.getStatus(),
            attachment.getCreatedAt()
        );
    }
}
