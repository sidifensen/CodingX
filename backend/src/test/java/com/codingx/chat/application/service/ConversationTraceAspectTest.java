package com.codingx.chat.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatTraceNode;
import com.codingx.chat.domain.model.ChatTraceRun;
import java.time.LocalDateTime;
import org.aspectj.lang.Aspects;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

/**
 * 验证 Trace AOP 会在注解方法执行时自动创建并收口子节点。
 */
class ConversationTraceAspectTest {

    /**
     * 注解方法在存在 trace 上下文时应触发 startNode/finishNode。
     */
    @Test
    void aroundCreatesAndFinishesTraceNode() {
        ConversationTraceRecordService traceRecordService = Mockito.mock(ConversationTraceRecordService.class);
        ConversationTraceAspect aspect = new ConversationTraceAspect(traceRecordService);
        SampleTraceService target = new SampleTraceService();
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(aspect);
        SampleTraceService proxy = proxyFactory.getProxy();
        ChatTraceNode traceNode = ChatTraceNode.builder()
            .id(1L)
            .traceId("trace-1")
            .nodeId("node-1")
            .depth(1)
            .nodeType("UNIT")
            .nodeName("sample-trace")
            .startedAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .build();
        when(traceRecordService.startNode(any(), any(), any(), any(), any())).thenReturn(traceNode);
        ConversationTraceContext.bind(ChatTraceRun.builder().traceId("trace-1").traceName("chat-entry").build());

        proxy.run();

        verify(traceRecordService).startNode(any(), any(), any(), any(), any());
        verify(traceRecordService).finishNode(any(), any(), any(), any(Long.class));
        ConversationTraceContext.clear();
    }

    /**
     * 用于验证切面的最小样例服务。
     */
    static class SampleTraceService {

        @ConversationTraceNode(name = "sample-trace", type = "UNIT")
        void run() {
        }
    }
}
