package com.codingx.config;

import com.codingx.chat.application.service.ChatExecutionContext;
import com.codingx.chat.application.service.ConversationTraceContext;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.tool.application.service.ChatToolExecutionContext;
import java.nio.file.Path;
import java.util.Deque;
import java.util.Optional;

/**
 * 聊天执行线程上下文快照，负责在异步线程恢复 runId/trace/工具目录。
 * @param runId 聊天执行 runId。
 * @param traceRun 当前 trace 根上下文。
 * @param traceNodeStack 当前 trace 节点栈快照，确保异步节点保留父子层级。
 * @param toolWorkingDirectory 工具工作目录。
 */
record ChatExecutorContextSnapshot(
    Long runId,
    ChatTraceRun traceRun,
    Deque<String> traceNodeStack,
    Path toolWorkingDirectory
) {

    /**
     * 从当前线程捕获上下文快照。
     * @return 上下文快照。
     */
    static ChatExecutorContextSnapshot capture() {
        Optional<Long> runId = ChatExecutionContext.currentRunId();
        ChatTraceRun traceRun = ConversationTraceContext.current();
        Deque<String> traceNodeStack = ConversationTraceContext.snapshotNodeStack();
        Optional<Path> toolWorkingDirectory = ChatToolExecutionContext.currentToolWorkingDirectory();
        return new ChatExecutorContextSnapshot(runId.orElse(null), traceRun, traceNodeStack, toolWorkingDirectory.orElse(null));
    }

    /**
     * 在当前线程恢复上下文。
     */
    void apply() {
        if (runId != null) {
            ChatExecutionContext.start(runId);
        } else {
            ChatExecutionContext.clear();
        }
        if (traceRun != null) {
            ConversationTraceContext.bind(traceRun, traceNodeStack);
        } else {
            ConversationTraceContext.clear();
        }
        if (toolWorkingDirectory != null) {
            ChatToolExecutionContext.bindToolWorkingDirectory(toolWorkingDirectory);
        } else {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * 清理当前线程上下文，避免线程池复用造成串线。
     */
    static void clearCurrent() {
        ChatExecutionContext.clear();
        ConversationTraceContext.clear();
        ChatToolExecutionContext.clear();
    }
}
