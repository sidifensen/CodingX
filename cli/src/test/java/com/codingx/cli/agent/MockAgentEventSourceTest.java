package com.codingx.cli.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地 mock 事件源测试，锁定终端 MVP 所需的最小事件序列。
 */
class MockAgentEventSourceTest {

    @Test
    void mockSourceShouldReturnOrderedTerminalDemoEvents() {
        MockAgentEventSource source = new MockAgentEventSource();

        List<AgentEvent> events = source.startTurn("分析这个项目", Path.of("D:/code/CodingX"));

        assertTrue(events.size() >= 6);
        assertEquals(1, events.getFirst().sequence());
        assertEquals(AgentEventType.SESSION_STARTED, events.getFirst().eventType());
        assertTrue(events.stream().anyMatch(event -> event.eventType() == AgentEventType.TOOL_STARTED));
        assertTrue(events.stream().anyMatch(event -> event.eventType() == AgentEventType.COMMAND_OUTPUT_DELTA));
        assertTrue(events.stream().anyMatch(event -> event.eventType() == AgentEventType.TURN_COMPLETED));
    }
}
