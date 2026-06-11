package com.codingx.cli.backend;

import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后端 SSE 到 CLI 事件的映射测试，锁定不应进入 TUI transcript 的协议控制事件。
 */
class BackendChatEventMapperTest {

    @Test
    void queueAcceptedShouldStaySilentInTranscript() {
        BackendChatEventMapper mapper = new BackendChatEventMapper(null, "turn");

        List<AgentEvent> events = mapper.map(new SseEvent("queue-accepted", "{}"));

        assertTrue(events.isEmpty(), "queue-accepted 只是后端调度状态，不应渲染为“已开始执行”");
    }

    @Test
    void approvalEventShouldMapToApprovalRequested() {
        BackendChatEventMapper mapper = new BackendChatEventMapper(null, "turn");

        List<AgentEvent> events = mapper.map(new SseEvent(
            "approval",
            "{\"requestId\":\"approval-1\",\"command\":\"git clean -fd\",\"summary\":\"需要确认执行：git clean -fd\",\"riskLevel\":\"HIGH\"}"
        ));

        assertEquals(1, events.size());
        assertEquals(AgentEventType.APPROVAL_REQUESTED, events.getFirst().eventType());
        assertEquals("approval-1", events.getFirst().payloadText("requestId"));
        assertEquals("需要确认执行：git clean -fd", events.getFirst().payloadText("summary"));
    }
}
