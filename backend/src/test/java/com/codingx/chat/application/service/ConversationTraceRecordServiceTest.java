package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.codingx.chat.domain.model.ChatTraceNode;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.chat.domain.repository.ChatTraceNodeRepository;
import com.codingx.chat.domain.repository.ChatTraceRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证 Trace 记录服务会保存根链路和节点链路。
 */
@ExtendWith(MockitoExtension.class)
class ConversationTraceRecordServiceTest {

    @Mock
    private ChatTraceRunRepository chatTraceRunRepository;

    @Mock
    private ChatTraceNodeRepository chatTraceNodeRepository;

    @InjectMocks
    private ConversationTraceRecordService conversationTraceRecordService;

    /**
     * 开启一条链路时应同时写根 run 与入口节点。
     */
    @Test
    void startTracePersistsRunAndEntryNode() {
        conversationTraceRecordService.startTrace("chat-entry", 1001L, 2001L);

        ArgumentCaptor<ChatTraceRun> runCaptor = ArgumentCaptor.forClass(ChatTraceRun.class);
        ArgumentCaptor<ChatTraceNode> nodeCaptor = ArgumentCaptor.forClass(ChatTraceNode.class);
        verify(chatTraceRunRepository).save(runCaptor.capture());
        verify(chatTraceNodeRepository).save(nodeCaptor.capture());
        assertEquals("chat-entry", runCaptor.getValue().getTraceName());
        assertEquals("chat-entry", nodeCaptor.getValue().getNodeName());
    }
}
