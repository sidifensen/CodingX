package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatTraceNode;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.chat.domain.repository.ChatTraceNodeRepository;
import com.codingx.chat.domain.repository.ChatTraceRunRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证 Trace 查询服务能按 traceId 返回根记录和节点集合。
 */
@ExtendWith(MockitoExtension.class)
class ConversationTraceQueryServiceTest {

    @Mock
    private ChatTraceRunRepository chatTraceRunRepository;

    @Mock
    private ChatTraceNodeRepository chatTraceNodeRepository;

    @InjectMocks
    private ConversationTraceQueryService conversationTraceQueryService;

    /**
     * 查询时应聚合根 run 与节点列表。
     */
    @Test
    void getTraceReturnsAggregatedView() {
        when(chatTraceRunRepository.findByTraceId("trace-1")).thenReturn(Optional.of(
            ChatTraceRun.builder().traceId("trace-1").traceName("chat-entry").build()
        ));
        when(chatTraceNodeRepository.findByTraceId("trace-1")).thenReturn(List.of(
            ChatTraceNode.builder().traceId("trace-1").nodeName("chat-entry").build()
        ));

        ConversationTraceView view = conversationTraceQueryService.getTrace("trace-1");

        assertEquals("trace-1", view.traceRun().getTraceId());
        assertEquals(1, view.nodes().size());
    }
}
