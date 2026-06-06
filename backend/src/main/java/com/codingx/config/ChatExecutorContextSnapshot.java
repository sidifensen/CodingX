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
 * @param governanceContext 工具治理上下文，供异步工具链路写权限审计。
 */
record ChatExecutorContextSnapshot(
    Long runId, // 聊天执行 runId，可为空；为空时异步线程会清理 ChatExecutionContext。
    ChatTraceRun traceRun, // 当前 Trace 根上下文，可为空；为空时异步线程不绑定 Trace。
    Deque<String> traceNodeStack, // Trace 节点栈快照，可为空；用于恢复异步节点父子关系。
    Path toolWorkingDirectory, // 工具工作目录，可为空；为空时异步线程不绑定本地工具目录。
    ChatToolExecutionContext.GovernanceContext governanceContext // 工具治理上下文，可为空。
) {

    /**
     * 从当前线程捕获上下文快照。
     * @return 上下文快照。
     */
    static ChatExecutorContextSnapshot capture() {
        // 步骤 1：从当前提交线程读取聊天执行上下文，缺失时用 null 表示需要清理目标线程状态。
        Optional<Long> runId = ChatExecutionContext.currentRunId();
        // 步骤 2：捕获 Trace 根对象和节点栈，保证异步任务继续挂在同一条过程链路下。
        ChatTraceRun traceRun = ConversationTraceContext.current();
        Deque<String> traceNodeStack = ConversationTraceContext.snapshotNodeStack();
        // 步骤 3：捕获工具工作目录和治理上下文，避免异步工具链路丢失会话归属。
        Optional<Path> toolWorkingDirectory = ChatToolExecutionContext.currentToolWorkingDirectory();
        ChatToolExecutionContext.GovernanceContext governanceContext =
            ChatToolExecutionContext.currentGovernanceContext().orElse(null);
        return new ChatExecutorContextSnapshot(
            runId.orElse(null),
            traceRun,
            traceNodeStack,
            toolWorkingDirectory.orElse(null),
            governanceContext
        );
    }

    /**
     * 在当前线程恢复上下文。
     */
    void apply() {
        if (runId != null) {
            // 步骤 1：有 runId 时恢复聊天执行上下文，供日志和状态回写读取当前运行编号。
            ChatExecutionContext.start(runId);
        } else {
            // 步骤 2：快照中没有 runId 时主动清理，避免线程池旧值串到当前任务。
            ChatExecutionContext.clear();
        }
        if (traceRun != null) {
            // 步骤 3：恢复 Trace 根对象和节点栈，确保异步节点继续保持父子层级。
            ConversationTraceContext.bind(traceRun, traceNodeStack);
        } else {
            // 步骤 4：没有 Trace 快照时清空目标线程 Trace，避免污染非追踪任务。
            ConversationTraceContext.clear();
        }
        if (toolWorkingDirectory != null) {
            // 步骤 5：恢复工具工作目录，供本地命令、文件读写等工具链路使用。
            ChatToolExecutionContext.bindToolWorkingDirectory(toolWorkingDirectory);
        } else {
            // 步骤 6：没有工具目录时仅清理目录，避免影响后续治理上下文恢复。
            ChatToolExecutionContext.bindToolWorkingDirectory(null);
        }
        if (governanceContext != null) {
            // 步骤 7：恢复权限审计所需用户、会话和运行归属。
            ChatToolExecutionContext.bindGovernanceContext(
                governanceContext.userId(),
                governanceContext.conversationId(),
                governanceContext.runId()
            );
        } else {
            // 步骤 8：没有治理上下文时主动清理，避免线程池旧值污染审计。
            ChatToolExecutionContext.bindGovernanceContext(null, null, null);
        }
    }

    /**
     * 清理当前线程上下文，避免线程池复用造成串线。
     */
    static void clearCurrent() {
        // 步骤 1：任务完成后统一清理三个 ThreadLocal 上下文，防止线程池复用造成跨会话串线。
        ChatExecutionContext.clear();
        ConversationTraceContext.clear();
        ChatToolExecutionContext.clear();
    }
}
