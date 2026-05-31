package com.codingx.chat.interfaces.controller;

import com.codingx.chat.application.service.ChatAttachmentService;
import com.codingx.chat.application.service.ChatLightweightViewService;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.interfaces.response.ChatAttachmentResponse;
import com.codingx.chat.interfaces.response.ChatAttachmentUploadCapabilitiesResponse;
import com.codingx.common.model.ApiResponse;
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

    /**
     * 附件应用服务，负责上传校验、对象存储写入、权限校验和文件下载。
     */
    private final ChatAttachmentService chatAttachmentService;

    /**
     * 轻量视图服务，负责附件元数据和上传能力响应投影。
     */
    private final ChatLightweightViewService chatLightweightViewService;

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
        // 步骤 1：上传、大小校验、类型校验和对象存储写入统一交给应用服务。
        ChatAttachment attachment = chatAttachmentService.upload(file, conversationId);
        // 步骤 2：附件响应字段由视图服务生成，Controller 不再拆解领域对象。
        return ApiResponse.success(chatLightweightViewService.toAttachmentResponse(attachment));
    }

    /**
     * 下载或预览附件内容，供前端图片预览弹窗与文件链接复用。
     * @param attachmentId 附件主键。
     * @return 文件二进制响应。
     */
    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<byte[]> content(@PathVariable Long attachmentId) {
        // 步骤 1：应用服务读取附件并校验当前登录用户拥有访问权限。
        ChatAttachment attachment = chatAttachmentService.requireOwnedAttachment(attachmentId);
        // 步骤 2：应用服务从对象存储下载二进制内容，异常统一转换为业务错误。
        byte[] bytes = chatAttachmentService.downloadContent(attachment);
        // 步骤 3：Controller 只做 HTTP 协议适配，补齐 Content-Type 和 inline 文件名头。
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
    public ApiResponse<ChatAttachmentUploadCapabilitiesResponse> capabilities() {
        // 步骤 1：上传能力由视图服务读取运行时配置并生成响应，Controller 不直接拼装 Map。
        return ApiResponse.success(chatLightweightViewService.toUploadCapabilitiesResponse());
    }
}
