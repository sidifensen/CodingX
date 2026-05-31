package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatSampleQuestion;
import com.codingx.chat.interfaces.response.ChatAttachmentResponse;
import com.codingx.chat.interfaces.response.ChatAttachmentUploadCapabilitiesResponse;
import com.codingx.chat.interfaces.response.ChatSampleQuestionResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证聊天轻量视图服务会统一投影示例问题、附件和上传能力响应。
 */
@ExtendWith(MockitoExtension.class)
class ChatLightweightViewServiceTest {

    @Mock
    private RuntimeSettingService runtimeSettingService;

    @InjectMocks
    private ChatLightweightViewService chatLightweightViewService;

    /**
     * 示例问题响应应保留欢迎区展示需要的最小字段。
     */
    @Test
    void toSampleQuestionResponsesProjectsEnabledQuestions() {
        List<ChatSampleQuestion> questions = List.of(
            ChatSampleQuestion.builder()
                .id(6001L)
                .questionText("请介绍一下 OA 系统的主要功能")
                .category("业务系统")
                .build()
        );

        List<ChatSampleQuestionResponse> responses = chatLightweightViewService.toSampleQuestionResponses(questions);

        assertEquals(6001L, responses.getFirst().id());
        assertEquals("请介绍一下 OA 系统的主要功能", responses.getFirst().questionText());
        assertEquals("业务系统", responses.getFirst().category());
    }

    /**
     * 附件响应应保留上传后前端立即渲染附件条目所需的字段。
     */
    @Test
    void toAttachmentResponseProjectsAttachmentMetadata() {
        ChatAttachment attachment = ChatAttachment.builder()
            .id(3001L)
            .conversationId(2001L)
            .messageId(4001L)
            .attachmentType("image")
            .fileName("demo.png")
            .fileExt("png")
            .mimeType("image/png")
            .fileSize(128L)
            .previewUrl("/api/chat/attachments/3001/content")
            .contentSummary(null)
            .status("UPLOADED")
            .createdAt(LocalDateTime.of(2026, 5, 19, 15, 0, 0))
            .build();

        ChatAttachmentResponse response = chatLightweightViewService.toAttachmentResponse(attachment);

        assertEquals(3001L, response.id());
        assertEquals(2001L, response.conversationId());
        assertEquals(4001L, response.messageId());
        assertEquals("image", response.attachmentType());
        assertEquals("/api/chat/attachments/3001/content", response.previewUrl());
    }

    /**
     * 上传能力响应应读取运行时大小限制并返回固定附件数量上限。
     */
    @Test
    void toUploadCapabilitiesResponseReadsRuntimeLimit() {
        when(runtimeSettingService.chatAttachmentMaxFileSizeBytes()).thenReturn(10L * 1024L * 1024L);

        ChatAttachmentUploadCapabilitiesResponse response = chatLightweightViewService.toUploadCapabilitiesResponse();

        assertEquals(10L * 1024L * 1024L, response.maxFileSizeBytes());
        assertEquals(9L, response.maxFileCount());
    }
}
