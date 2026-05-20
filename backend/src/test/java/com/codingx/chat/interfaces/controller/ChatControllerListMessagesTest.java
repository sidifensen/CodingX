package com.codingx.chat.interfaces.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.chat.application.service.ChatApplicationService;
import com.codingx.chat.application.service.ChatAttachmentService;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatReactionService;
import com.codingx.chat.application.service.ChatRuntimeGuardService;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.config.GlobalExceptionHandler;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证消息列表接口的 ID 序列化契约，避免前端读取历史消息时发生 Long 精度丢失。
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerListMessagesTest {

    @Mock
    private ChatConversationApplicationService chatConversationApplicationService;

    @Mock
    private ChatApplicationService chatApplicationService;

    @Mock
    private ChatRuntimeGuardService chatRuntimeGuardService;

    @Mock
    private ChatAttachmentService chatAttachmentService;

    @Mock
    private ChatReactionService chatReactionService;

    @Mock
    private ChatSkillRepository chatSkillRepository;

    @Mock
    private ChatMcpRepository chatMcpRepository;

    @InjectMocks
    private ChatController chatController;

    /**
     * 消息列表中的主键与会话 ID 应返回字符串。
     */
    @Test
    void listMessagesSerializesLongIdentifiersAsStrings() throws Exception {
        ChatMessage assistantMessage = ChatMessage.create(
            2055117513431715840L,
            2055114974648864768L,
            com.codingx.chat.domain.model.ChatMessageRole.ASSISTANT,
            "历史回答",
            ChatMessageStatus.COMPLETED,
            null,
            null,
            null
        ).attachRun(2055117498822955008L);
        when(chatConversationApplicationService.listMessages(2055114974648864768L, 1002L)).thenReturn(List.of(assistantMessage));
        when(chatSkillRepository.findByTaskId(2055117498822955008L)).thenReturn(List.of(
            ChatSkill.builder()
                .id(7101L)
                .skillCode("sales_query")
                .displayName("销售查询")
                .build()
        ));
        when(chatAttachmentService.listByMessageId(2055117513431715840L)).thenReturn(List.of(
            ChatAttachment.builder()
                .id(2055117513431715999L)
                .messageId(2055117513431715840L)
                .conversationId(2055114974648864768L)
                .attachmentType("image")
                .fileName("demo.png")
                .fileExt("png")
                .mimeType("image/png")
                .fileSize(2048L)
                .previewUrl("/api/chat/attachments/2055117513431715999/content")
                .status("UPLOADED")
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .uploadedBy(1002L)
                .deleted(0)
                .build()
        ));

        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            mockMvc().perform(get("/api/chat/conversations/2055114974648864768/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("2055117513431715840"))
                .andExpect(jsonPath("$.data[0].conversationId").value("2055114974648864768"))
                .andExpect(jsonPath("$.data[0].skillCodes[0]").value("sales_query"))
                .andExpect(jsonPath("$.data[0].attachments[0].id").value("2055117513431715999"));
        }
    }

    /**
     * 延迟创建 MockMvc，确保 Mockito 注入完成。
     * @return 用于接口契约断言的 MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(chatController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
