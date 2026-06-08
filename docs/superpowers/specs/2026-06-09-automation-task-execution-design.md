# Automation Task Execution Design

## Background

Automation tasks can currently be created manually or from chat, and the scheduler can claim due tasks. The claimed task only advances `lastRunAt`, `lastRunStatus` and `nextRunAt`; it does not execute the task prompt. Users therefore see "running" scheduled tasks, but no generated result appears when the task is due.

## Design

Due automation tasks should become real background chat runs. `AutomationTaskScheduler` keeps its timer role, delegates due claiming to `AutomationTaskService`, then passes the claimed snapshots to a new `AutomationTaskExecutionService`. The execution service resolves the delivery conversation, builds a `SendChatMessageCommand` from the task prompt, and submits it through `ChatStreamExecutionService.dispatch(runId, command, userId)`. This reuses the existing chat model routing, search decision, SSE events, execution run records, and task-completion unread reminder instead of creating a parallel AI executor.

For chat-created tasks, `sourceConversationId` is the delivery target. For manually created tasks without `sourceConversationId`, the execution service creates a new chat conversation titled `自动化任务：<任务名>` under the task owner and optional workspace, saves it, writes the conversation ID back to `automation_task.source_conversation_id`, and then dispatches the run there. Later runs reuse that same conversation. The prompt is prefixed with a short automation context that includes the task name and asks the assistant to execute the scheduled requirement directly; prompts such as "推送新闻" already contain freshness/search wording, so the existing chat search router can perform web search when enabled.

If a task cannot be delivered because the source conversation is missing or belongs to another user, the execution service must not execute the prompt. It records a failed delivery status on the task, logs the error, and lets the next normal schedule calculation remain intact. If the target conversation exists but another run is currently active, `ChatStreamExecutionService` handles the existing queue/rejection behavior and emits the same user-visible state as normal chat background runs.

## Test Strategy

Unit tests cover scheduler-to-executor delegation and execution-service command construction. A chat-source task should dispatch to the existing source conversation with the task owner. A manual task without a source conversation should create one delivery conversation, persist it on the task, and dispatch to that new conversation. Existing automation scheduling tests remain responsible for claiming and next-run advancement.
