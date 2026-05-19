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
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.chat.infrastructure.stream.ChatSseRegistry;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.skill.domain.repository.ChatSkillRepository;
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
     * 技能仓储依赖。
     */
    @Mock
    private ChatSkillRepository chatSkillRepository;

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
        when(chatSkillRepository.findAllEnabled()).thenReturn(List.of(
            ChatSkill.builder().id(2L).skillCode("ticket_query").displayName("工单查询").category("工单").enabled(1).sortNo(1).build()
        ));
        when(chatConversationApplicationService.listMessages(1L, 1001L)).thenReturn(List.of(
            ChatMessage.userMessage(1L, "历史消息")
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
                    && map.get("skillCodes").equals(List.of("ticket_query"))
                    && map.get("attachmentIds").equals(List.of())
                    && map.containsKey("taskId"))
            );
            verify(chatStreamExecutionService).dispatch(
                argThat(command -> command.conversationId().equals(1L)
                    && command.content().equals("你好")
                    && command.deepThinking()
                    && command.mcpCodes().equals(List.of("sales_query"))
                    && command.skillCodes().equals(List.of("ticket_query"))
                    && command.attachmentIds().isEmpty()),
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
        when(chatSkillRepository.findAllEnabled()).thenReturn(List.of(
            ChatSkill.builder().id(2L).skillCode("ticket_query").displayName("工单查询").category("工单").enabled(1).sortNo(1).build()
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
                    && map.get("skillCodes").equals(List.of("ticket_query"))
                    && map.get("attachmentIds").equals(List.of())
                    && map.containsKey("taskId"))
            );
            verify(chatStreamExecutionService).dispatch(
                argThat(command -> command.conversationId().equals(2001L)
                    && command.content().equals("新的问题")
                    && !command.deepThinking()
                    && command.mcpCodes().equals(List.of("sales_query"))
                    && command.skillCodes().equals(List.of("ticket_query"))
                    && command.attachmentIds().isEmpty()),
                eq(1001L)
            );
        }
    }

    /**
     * 显式传入 skillCodes 时，应优先使用前端指定列表，不回退默认启用项。
     */
    @Test
    void streamChatUsesExplicitSkillCodesWhenProvided() {
        SseEmitter emitter = new SseEmitter(0L);
        whenRegisterReturns(emitter);
        when(chatConversationApplicationService.listMessages(3001L, 1001L)).thenReturn(List.of(
            ChatMessage.userMessage(3001L, "历史消息")
        ));
        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1001L);

            SseEmitter actual = chatStreamController.streamChat(
                "查询销售",
                3001L,
                false,
                "sales_query",
                "ticket_query,sales_query"
            );

            assertEquals(emitter, actual);
            verify(chatSseRegistry).publish(
                eq(3001L),
                eq("meta"),
                argThat(payload -> payload instanceof java.util.Map<?, ?> map
                    && map.get("mcpCodes").equals(List.of("sales_query"))
                    && map.get("skillCodes").equals(List.of("ticket_query", "sales_query")))
            );
            verify(chatStreamExecutionService).dispatch(
                argThat(command -> command.conversationId().equals(3001L)
                    && command.content().equals("查询销售")
                    && !command.deepThinking()
                    && command.mcpCodes().equals(List.of("sales_query"))
                    && command.skillCodes().equals(List.of("ticket_query", "sales_query"))
                    && command.attachmentIds().isEmpty()),
                eq(1001L)
            );
        }
    }

    /**
     * 兼容订阅接口也必须校验 owner，避免越权监听他人会话。
     */
    @Test
    void streamChecksConversationOwnershipBeforeRegister() {
        SseEmitter emitter = new SseEmitter(0L);
        whenRegisterReturns(emitter);
        when(chatConversationApplicationService.listMessages(4001L, 1001L)).thenReturn(List.of(
            ChatMessage.userMessage(4001L, "历史消息")
        ));

        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1001L);

            SseEmitter actual = chatStreamController.stream(4001L);

            assertEquals(emitter, actual);
            verify(chatConversationApplicationService).listMessages(4001L, 1001L);
            verify(chatSseRegistry).register(4001L);
        }
    }

    /**
     * 显式传入结构化 messages 时，应优先解析技能命令与文本内容，避免技能命令被当作普通文本。
     */
    @Test
    void streamChatParsesStructuredMessagesForSkillAndText() {
        SseEmitter emitter = new SseEmitter(0L);
        whenRegisterReturns(emitter);
        when(chatConversationApplicationService.listMessages(5001L, 1001L)).thenReturn(List.of(
            ChatMessage.userMessage(5001L, "历史消息")
        ));
        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1001L);

            SseEmitter actual = chatStreamController.streamChat(
                "@sales_query 这段文本不应生效",
                5001L,
                false,
                null,
                null,
                """
                [
                  {
                    "type":"slash_command",
                    "data":{
                      "id":"^/agent-browser/SKILL.md",
                      "command":"agent-browser",
                      "command_type":"skill",
                      "parameters":{"argCount":0,"hasArgumentsVar":false,"parameterValues":{}}
                    }
                  },
                  {
                    "type":"text",
                    "data":{"content":"请分析最近订单趋势"}
                  }
                ]
                """
            );

            assertEquals(emitter, actual);
            verify(chatSseRegistry).publish(
                eq(5001L),
                eq("meta"),
                argThat(payload -> payload instanceof java.util.Map<?, ?> map
                    && map.get("skillCodes").equals(List.of("agent-browser")))
            );
            verify(chatStreamExecutionService).dispatch(
                argThat(command -> command.conversationId().equals(5001L)
                    && command.content().equals("请分析最近订单趋势")
                    && command.skillCodes().equals(List.of("agent-browser"))
                    && command.attachmentIds().isEmpty()),
                eq(1001L)
            );
        }
    }

    /**
     * 显式传入 repositoryPath 时，应透传到消息命令供后续工具执行使用。
     */
    @Test
    void streamChatPassesRepositoryPathToCommand() {
        SseEmitter emitter = new SseEmitter(0L);
        whenRegisterReturns(emitter);
        when(chatConversationApplicationService.listMessages(6001L, 1001L)).thenReturn(List.of(
            ChatMessage.userMessage(6001L, "历史消息")
        ));
        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1001L);

            SseEmitter actual = chatStreamController.streamChat(
                "请分析代码",
                6001L,
                false,
                "code_search",
                "agent-browser",
                "D:/code/codingx",
                null
            );

            assertEquals(emitter, actual);
            verify(chatStreamExecutionService).dispatch(
                argThat(command -> command.conversationId().equals(6001L)
                    && command.content().equals("请分析代码")
                    && command.repositoryPath().equals("D:/code/codingx")
                    && command.attachmentIds().isEmpty()),
                eq(1001L)
            );
        }
    }

    /**
     * 显式传入附件主键时，应透传至消息命令并同步回写到 meta 事件，便于前端确认当前发送上下文。
     */
    @Test
    void streamChatPassesAttachmentIdsToCommand() {
        SseEmitter emitter = new SseEmitter(0L);
        whenRegisterReturns(emitter);
        when(chatConversationApplicationService.listMessages(7001L, 1001L)).thenReturn(List.of(
            ChatMessage.userMessage(7001L, "历史消息")
        ));
        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1001L);

            SseEmitter actual = chatStreamController.streamChat(
                "请结合图片分析",
                7001L,
                false,
                null,
                null,
                null,
                null,
                "9001,9002,9002,invalid"
            );

            assertEquals(emitter, actual);
            verify(chatSseRegistry).publish(
                eq(7001L),
                eq("meta"),
                argThat(payload -> payload instanceof java.util.Map<?, ?> map
                    && map.get("attachmentIds").equals(List.of(9001L, 9002L)))
            );
            verify(chatStreamExecutionService).dispatch(
                argThat(command -> command.conversationId().equals(7001L)
                    && command.content().equals("请结合图片分析")
                    && command.attachmentIds().equals(List.of(9001L, 9002L))),
                eq(1001L)
            );
        }
    }

    /**
     * @param emitter 预期返回的 emitter。
     */
    private void whenRegisterReturns(SseEmitter emitter) {
        doAnswer(invocation -> emitter).when(chatSseRegistry).register(any());
    }
}

