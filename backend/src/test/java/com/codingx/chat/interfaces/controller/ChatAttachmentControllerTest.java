package com.codingx.chat.interfaces.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.chat.application.service.ChatAttachmentService;
import com.codingx.chat.application.service.ChatLightweightViewService;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.interfaces.response.ChatAttachmentResponse;
import com.codingx.chat.interfaces.response.ChatAttachmentUploadCapabilitiesResponse;
import com.codingx.config.GlobalExceptionHandler;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证聊天附件上传与预览下载控制器契约。
 */
@ExtendWith(MockitoExtension.class)
class ChatAttachmentControllerTest {

    @Mock
    private ChatAttachmentService chatAttachmentService;

    @Mock
    private ChatLightweightViewService chatLightweightViewService;

    @InjectMocks
    private ChatAttachmentController chatAttachmentController;

    /**
     * 上传接口应返回附件元数据并透传会话主键。
     */
    @Test
    void uploadReturnsAttachmentPayload() throws Exception {
        ChatAttachment attachment = sampleAttachment();
        when(chatAttachmentService.upload(any(), eq(2001L))).thenReturn(attachment);
        when(chatLightweightViewService.toAttachmentResponse(attachment)).thenReturn(new ChatAttachmentResponse(
            3001L,
            2001L,
            null,
            "image",
            "demo.png",
            "png",
            "image/png",
            128L,
            "/api/chat/attachments/3001/content",
            null,
            "UPLOADED",
            LocalDateTime.of(2026, 5, 19, 15, 0, 0)
        ));

        mockMvc().perform(multipart("/api/chat/attachments/upload")
                .file(new MockMultipartFile("file", "demo.png", "image/png", new byte[] {1, 2, 3}))
                .param("conversationId", "2001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value("3001"))
            .andExpect(jsonPath("$.data.previewUrl").value("/api/chat/attachments/3001/content"))
            .andExpect(jsonPath("$.data.attachmentType").value("image"));

        verify(chatLightweightViewService).toAttachmentResponse(attachment);
    }

    /**
     * 预览接口应返回二进制内容并设置 inline 头。
     */
    @Test
    void contentReturnsBinaryBody() throws Exception {
        ChatAttachment attachment = sampleAttachment();
        when(chatAttachmentService.requireOwnedAttachment(3001L)).thenReturn(attachment);
        when(chatAttachmentService.downloadContent(attachment)).thenReturn(new byte[] {9, 8, 7});

        mockMvc().perform(get("/api/chat/attachments/3001/content"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andExpect(content().bytes(new byte[] {9, 8, 7}));

        verify(chatAttachmentService).requireOwnedAttachment(3001L);
    }

    /**
     * 上传能力接口应返回 10MB 上限，保证前端提示与后端真实限制一致。
     */
    @Test
    void capabilitiesReturnsTenMbLimit() throws Exception {
        when(chatLightweightViewService.toUploadCapabilitiesResponse()).thenReturn(
            new ChatAttachmentUploadCapabilitiesResponse(10L * 1024L * 1024L, 9L)
        );
        mockMvc().perform(get("/api/chat/attachments/upload-capabilities"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.maxFileSizeBytes").value(10L * 1024L * 1024L))
            .andExpect(jsonPath("$.data.maxFileCount").value(9));

        verify(chatLightweightViewService).toUploadCapabilitiesResponse();
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(chatAttachmentController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    private ChatAttachment sampleAttachment() {
        return ChatAttachment.builder()
            .id(3001L)
            .conversationId(2001L)
            .messageId(null)
            .uploadedBy(1002L)
            .attachmentType("image")
            .fileName("demo.png")
            .fileExt("png")
            .mimeType("image/png")
            .fileSize(128L)
            .storageKey("chat/attachments/demo.png")
            .previewUrl("/api/chat/attachments/3001/content")
            .status("UPLOADED")
            .createdAt(LocalDateTime.of(2026, 5, 19, 15, 0, 0))
            .updatedAt(LocalDateTime.of(2026, 5, 19, 15, 0, 0))
            .deleted(0)
            .build();
    }
}
