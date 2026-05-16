package com.codingx.chat.interfaces.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.chat.application.command.CreateConversationCommand;
import com.codingx.chat.application.service.ChatConversationApplicationService;
import com.codingx.chat.application.service.ChatStreamExecutionService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.infrastructure.stream.ChatSseRegistry;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 验证单次 SSE 聊天入口的最小控制器契约。
 */
@ExtendWith(MockitoExtension.class)
class ChatStreamControllerTest {

    /**
     * 聊天流派发服务依赖。
     */
    @Mock
    private ChatStreamExecutionService chatStreamExecutionService;

    /**
     * 会话应用服务依赖。
     */
    @Mock
    private ChatConversationApplicationService chatConversationApplicationService;

    /**
     * SSE 注册中心依赖。
     */
    @Mock
    private ChatSseRegistry chatSseRegistry;

    /**
     * 技能仓储依赖。
     */
    @Mock
    private ChatMcpRepository chatMcpRepository;

    /**
     * 被测控制器。
     */
    @InjectMocks
    private ChatStreamController chatStreamController;

    /**
     * 单次 SSE 入口应先注册 emitter，再触发应用服务发送消息。
     */
    @Test
    void streamChatRegistersEmitterAndDispatchesMessage() {
        SseEmitter emitter = new SseEmitter(0L);
        whenRegisterReturns(emitter);
        when(chatMcpRepository.findAllEnabled()).thenReturn(List.of(
            ChatMcp.builder().id(1L).mcpCode("sales_query").displayName("销售查询").category("销售").enabled(1).sortNo(1).build()
        ));
        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1001L);

            SseEmitter actual = chatStreamController.streamChat("你好", 1L, true);

            assertEquals(emitter, actual);
            verify(chatSseRegistry).register(1L);
            verify(chatSseRegistry).publish(
                eq(1L),
                eq("meta"),
                argThat(payload -> payload instanceof java.util.Map<?, ?> map
                    && map.get("conversationId").equals(1L)
                    && map.get("deepThinking").equals(true)
                    && map.get("mcpCodes").equals(List.of("sales_query"))
                    && map.containsKey("taskId"))
            );
            verify(chatStreamExecutionService).dispatch(
                argThat(command -> command.conversationId().equals(1L)
                    && command.content().equals("你好")
                    && command.deepThinking()
                    && command.mcpCodes().equals(List.of("sales_query"))),
                eq(1001L)
            );
        }
    }

    /**
     * 空 conversationId 时应创建新的会话流入口。
     */
    @Test
    void streamChatSupportsNewConversationBootstrap() {
        SseEmitter emitter = new SseEmitter(0L);
        whenRegisterReturns(emitter);
        when(chatMcpRepository.findAllEnabled()).thenReturn(List.of(
            ChatMcp.builder().id(1L).mcpCode("sales_query").displayName("销售查询").category("销售").enabled(1).sortNo(1).build()
        ));
        ChatConversation conversation = ChatConversation.create(2001L, "New Conversation", 1001L, ChatConversationStatus.ACTIVE);
        when(chatConversationApplicationService.createConversation(any(CreateConversationCommand.class), any(Long.class))).thenReturn(conversation);
        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1001L);

            SseEmitter actual = chatStreamController.streamChat("新的问题", null, null);

            assertNotNull(actual);
            verify(chatConversationApplicationService).createConversation(new CreateConversationCommand(null), 1001L);
            verify(chatSseRegistry).register(2001L);
            verify(chatSseRegistry).publish(
                eq(2001L),
                eq("meta"),
                argThat(payload -> payload instanceof java.util.Map<?, ?> map
                    && map.get("conversationId").equals(2001L)
                    && map.get("deepThinking").equals(false)
                    && map.get("mcpCodes").equals(List.of("sales_query"))
                    && map.containsKey("taskId"))
            );
            verify(chatStreamExecutionService).dispatch(
                argThat(command -> command.conversationId().equals(2001L)
                    && command.content().equals("新的问题")
                    && !command.deepThinking()
                    && command.mcpCodes().equals(List.of("sales_query"))),
                eq(1001L)
            );
        }
    }

    /**
     * 设置 register 方法的统一返回值。
     * @param emitter 预期返回的 emitter。
     */
    private void whenRegisterReturns(SseEmitter emitter) {
        doAnswer(invocation -> emitter).when(chatSseRegistry).register(any());
    }
}
