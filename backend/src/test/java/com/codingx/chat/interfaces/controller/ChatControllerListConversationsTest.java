package com.codingx.chat.interfaces.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.chat.application.service.ChatApplicationService;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatReactionService;
import com.codingx.chat.application.service.ChatRuntimeGuardService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.repository.ChatSkillRepository;
import com.codingx.config.GlobalExceptionHandler;
import java.time.LocalDateTime;
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
 * 验证会话列表接口会返回前端真实回放所需的 lastRunId 字段。
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerListConversationsTest {

    @Mock
    private ChatConversationApplicationService chatConversationApplicationService;

    @Mock
    private ChatApplicationService chatApplicationService;

    @Mock
    private ChatRuntimeGuardService chatRuntimeGuardService;

    @Mock
    private ChatReactionService chatReactionService;

    @Mock
    private ChatSkillRepository chatSkillRepository;

    @InjectMocks
    private ChatController chatController;

    /**
     * 会话列表应包含 lastRunId，供前端判断右栏是否已有回放数据。
     */
    @Test
    void listConversationsReturnsLastRunId() throws Exception {
        ChatConversation conversation = ChatConversation.create(2001L, "Default Demo Conversation", 1002L, ChatConversationStatus.ACTIVE);
        conversation.restoreRuntimeState(LocalDateTime.of(2026, 5, 15, 0, 36, 58), 2054964195115945984L);
        when(chatConversationApplicationService.listConversations(1002L)).thenReturn(List.of(conversation));

        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            mockMvc().perform(get("/api/chat/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value("2001"))
                .andExpect(jsonPath("$.data[0].lastRunId").value("2054964195115945984"));
        }
    }

    /**
     * 会话列表中的超大 Long ID 应序列化为字符串，避免浏览器 Number 精度丢失后无法正确切换历史会话。
     */
    @Test
    void listConversationsSerializesLongIdentifiersAsStrings() throws Exception {
        ChatConversation conversation = ChatConversation.create(2055114974648864768L, "历史会话", 1002L, ChatConversationStatus.ACTIVE);
        conversation.restoreRuntimeState(LocalDateTime.of(2026, 5, 15, 10, 36, 58), 2055114974682419200L);
        when(chatConversationApplicationService.listConversations(1002L)).thenReturn(List.of(conversation));

        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            mockMvc().perform(get("/api/chat/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("2055114974648864768"))
                .andExpect(jsonPath("$.data[0].lastRunId").value("2055114974682419200"));
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
