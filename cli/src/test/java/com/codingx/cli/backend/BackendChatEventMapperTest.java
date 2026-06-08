package com.codingx.cli.backend;

import com.codingx.cli.agent.AgentEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
