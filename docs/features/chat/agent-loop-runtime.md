# Agent Loop 运行时

## 功能用途

聊天主链路支持模型发起本地工具调用后继续进入下一轮模型生成，形成“模型输出工具调用 -> 后端执行工具 -> 工具结果回灌 -> 模型继续决策”的自主任务循环。当前实现把轮次上限、重复调用识别和收口原因抽成独立协调器，避免循环控制散落在聊天服务内部。

## 使用入口

用户在聊天页或 CLI 发起本地代码类任务时，后端会把可见工具 schema 交给模型。模型返回 `tool_calls` 后，`ChatApplicationService` 执行工具并把工具结果作为系统证据追加到下一轮模型历史。

## 核心流程

1. `ChatApplicationService` 构造模型历史、技能/专家/搜索上下文和模型可见工具 schema 后进入工具感知循环。若本轮没有可见工具，则直接走普通流式回答，不进入工具循环。
2. 模型流式返回过程中，`buildStreamHandler` 收集助手正文、thinking 和 `tool_call`。当本轮包含工具调用时，助手正文先被延迟缓冲，避免“我现在执行”这类过程文案直接展示给用户。
3. `AgentLoopCoordinator` 根据当前轮次、轮次上限、工具调用数量和去重键判断是否继续。重复工具调用会复用上一次结果并标记 `duplicateSkipped`，轮次达到上限后返回中文错误。
4. 工具执行结果通过 `buildLocalToolEvidenceContext` 写成系统证据追加到下一轮模型历史。模型可基于真实工具输出继续调用工具或生成最终回答。
5. 当模型不再发起工具调用时，最终正文通过 SSE 增量发布并落库。工具执行失败、轮次上限或用户取消会进入统一收口，更新运行状态并发布错误或终止事件。

## 关键文件

- `backend/src/main/java/com/codingx/chat/application/service/agent/AgentLoopCoordinator.java`：集中判断工具循环是否继续、是否达到上限、是否重复调用。
- `backend/src/main/java/com/codingx/chat/application/service/agent/AgentLoopResult.java`：描述单次循环判断结果。
- `backend/src/main/java/com/codingx/chat/application/service/agent/AgentLoopCompletionReason.java`：定义循环收口原因。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：执行模型工具调用、回灌工具证据、发布过程事件。

## 测试与验证

- `mvn -Dtest=AgentLoopCoordinatorTest,ChatApplicationToolCallFlowTest test`
