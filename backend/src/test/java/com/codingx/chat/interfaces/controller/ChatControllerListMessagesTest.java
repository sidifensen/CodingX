package com.codingx.chat.interfaces.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.chat.application.service.ChatApplicationService;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatConversationViewService;
import com.codingx.chat.application.service.ChatReactionService;
import com.codingx.chat.application.service.ChatRuntimeGuardService;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.interfaces.response.ChatAttachmentResponse;
import com.codingx.chat.interfaces.response.ChatMessageResponse;
import com.codingx.chat.interfaces.response.CursorPageResponse;
import com.codingx.config.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
    private ChatConversationViewService chatConversationViewService;

    @Mock
    private ChatRuntimeGuardService chatRuntimeGuardService;

    @Mock
    private ChatReactionService chatReactionService;

    @InjectMocks
    private ChatController chatController;

    /**
     * 消息列表中的主键与会话 ID 应返回字符串。
     */
    @Test
    void listMessagesSerializesLongIdentifiersAsStrings() throws Exception {
        ChatMessage userMessage = ChatMessage.create(
            2055117513431715840L,
            2055114974648864768L,
            com.codingx.chat.domain.model.ChatMessageRole.USER,
            "@weather_query 历史问题",
            ChatMessageStatus.COMPLETED,
            null,
            null,
            null
        ).attachRun(2055117498822955008L);
        List<ChatMessage> messages = List.of(userMessage);
        List<ChatMessageResponse> responses = List.of(new ChatMessageResponse(
            2055117513431715840L,
            2055114974648864768L,
            2055117498822955008L,
            ChatMessageRole.USER,
            "@weather_query 历史问题",
            null,
            null,
            ChatMessageStatus.COMPLETED,
            null,
            null,
            null,
            0,
            null,
            null,
            List.of(new ChatAttachmentResponse(
                2055117513431715999L,
                2055114974648864768L,
                2055117513431715840L,
                "image",
                "demo.png",
                "png",
                "image/png",
                2048L,
                "/api/chat/attachments/2055117513431715999/content",
                null,
                "UPLOADED",
                java.time.LocalDateTime.now()
            )),
            List.of("weather_query"),
            null
        ));
        when(chatConversationApplicationService.listMessages(2055114974648864768L, 1002L)).thenReturn(messages);
        when(chatConversationViewService.toMessageResponses(messages, 1002L)).thenReturn(responses);

        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            mockMvc().perform(get("/api/chat/conversations/2055114974648864768/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("2055117513431715840"))
                .andExpect(jsonPath("$.data[0].conversationId").value("2055114974648864768"))
                .andExpect(jsonPath("$.data[0].skillCodes[0]").value("weather_query"))
                .andExpect(jsonPath("$.data[0].attachments[0].id").value("2055117513431715999"));
        }
    }

    /**
     * 传入 pageSize 时消息列表应返回 cursor 分页对象，供前端首屏只加载最近一页。
     */
    @Test
    void listMessagesReturnsCursorPageWhenPageSizePresent() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 8, 12, 30);
        ChatMessage userMessage = ChatMessage.create(
            2055117513431715840L,
            2055114974648864768L,
            ChatMessageRole.USER,
            "分页历史问题",
            ChatMessageStatus.COMPLETED,
            null,
            null,
            null
        );
        userMessage.restoreRuntimeState(null, null, null, createdAt, createdAt);
        List<ChatMessage> messages = List.of(userMessage);
        List<ChatMessageResponse> responses = List.of(new ChatMessageResponse(
            2055117513431715840L,
            2055114974648864768L,
            null,
            ChatMessageRole.USER,
            "分页历史问题",
            null,
            null,
            ChatMessageStatus.COMPLETED,
            null,
            null,
            null,
            0,
            createdAt,
            createdAt,
            List.of(),
            List.of(),
            null
        ));
        CursorPageResponse.Cursor nextCursor = new CursorPageResponse.Cursor(null, null, createdAt, 2055117513431715840L);
        when(chatConversationApplicationService.pageMessages(2055114974648864768L, 1002L, 20, null, null))
            .thenReturn(new CursorPageResponse<>(messages, true, nextCursor));
        when(chatConversationViewService.toMessageResponses(messages, 1002L)).thenReturn(responses);

        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            mockMvc().perform(get("/api/chat/conversations/2055114974648864768/messages")
                    .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("2055117513431715840"))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.nextCursor.cursorCreatedAt").value("2026-06-08T12:30:00"))
                .andExpect(jsonPath("$.data.nextCursor.cursorId").value("2055117513431715840"));
        }
    }

    /**
     * 延迟创建 MockMvc，确保 Mockito 注入完成。
     * @return 用于接口契约断言的 MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(chatController)
            .setMessageConverters(jacksonMessageConverter())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    /**
     * standalone MockMvc 需要显式加载项目同款 JSON 规则，保证 Long 与 LocalDateTime 序列化契约贴近生产环境。
     */
    private MappingJackson2HttpMessageConverter jacksonMessageConverter() {
        return new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
            .serializerByType(Long.class, ToStringSerializer.instance)
            .serializerByType(Long.TYPE, ToStringSerializer.instance)
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build());
    }
}
