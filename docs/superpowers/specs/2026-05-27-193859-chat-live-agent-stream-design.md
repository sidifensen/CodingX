# 聊天实时代理流式过程设计

## 背景

当前聊天链路已经支持 `message`、`thinking`、`tool-call`、`mcp-call`、`step`、`reference` 等 SSE 事件，并在前端通过 `timelineItems` 做消息内穿插展示。但工具模式下，后端在模型产生工具调用后会清空本轮正文缓冲，导致工具前模型已经输出的简短说明无法稳定保留；同时工具调用只在一轮模型流结束后执行，最终观感仍偏向“先拿完工具和数据，再统一回复”。

本次目标是让助手消息呈现为实时代理过程：模型可以先说一句话，随后展示深度思考、调用工具、展示工具结果，再继续说话或继续调用工具。所有片段都应按真实到达顺序保留在同一条助手消息中。

## 目标

- 保留工具调用前后模型真实输出的正文片段，禁止工具轮次清空已流出的用户可见内容。
- 深度思考模式下继续实时展示上游模型返回的真实 thinking / reasoning 增量。
- 工具调用与工具结果必须插入到助手消息时间线中，而不是堆到最终回答之前或之后。
- 工具结果回灌后，模型继续生成的正文和 thinking 应继续追加为新的时间线片段。
- 结束事件只能用于状态收口，不能用最终正文覆盖或丢弃已经按顺序到达的时间线片段。

## 非目标

- 不伪造模型思考内容。若 provider 不返回 `reasoning_content`，前端不补写假的深度思考。
- 不强制所有 provider 都必须在工具调用前输出一句话。若模型直接发起工具调用，则按真实工具事件展示。
- 不改变数据库消息主表结构。本次时间线仍属于前端运行时与本地快照展示数据。
- 不把工具原始输出直接当作最终回答。工具结果仍需回灌模型，由模型继续组织用户可读回答。

## 设计

### 后端流式语义

`ChatApplicationService.runAiToolAwareLoop()` 保留多轮工具调用循环，但调整正文与 thinking 的生命周期：

1. 每轮模型流中的 `onDelta` 继续实时发布 `message` SSE，并追加到最终正文缓冲。
2. 每轮模型流中的 `onThinkingDelta` 继续实时发布 `thinking` SSE，并追加到 thinking 缓冲。
3. 当本轮存在工具调用时，不再清空已发布正文；工具调用只作为后续过程事件插入。
4. 工具执行完成后，把工具结果作为证据加入下一轮模型上下文，让模型继续输出后续正文。
5. 最终保存的 assistant 正文使用所有用户可见 `message` delta 的累积结果，而不是只保存最后一轮模型正文。

为避免多轮 reasoning 时长混淆，thinking 的数据库字段可以继续记录整体 thinking 内容和累计耗时；前端运行态通过时间线保留不同阶段的显示顺序。

### 工具调用事件

现有 `tool-call` SSE 继续承载 `start`、`complete`、`error` 三类阶段。后端在收到完整模型工具调用后立即发布 `start`，执行完成后发布 `complete`。如果后续要做更细粒度展示，可在 parser 支持参数片段稳定解析后增加 `progress`，但本次不扩大协议。

`OpenAiStyleStreamParser` 当前在 `[DONE]` 或 flush 后派发完整工具调用，这是工具执行的真实边界。本次接受该边界：工具调用可以在模型一轮流结束后开始，但工具前正文、thinking、工具事件和工具后正文必须在前端同一条消息内按顺序显示。

### 前端时间线

`useChatWorkspace.ts` 继续作为 SSE 聚合入口，重点保证：

1. `message` 事件追加到最近的正文片段；若上一片段是过程节点，则创建新正文片段。
2. `thinking`、`tool-call`、`mcp-call`、`step`、`reference` 更新 `processCards` 后同步到 `timelineItems`。
3. `finish` 事件只负责补齐状态、标题、搜索状态和缺失正文，不得用 `payload.content` 重建时间线导致前序片段丢失。
4. 历史回放无 `timelineItems` 时继续使用 `processCards + content` 兜底。

### 模型行为提示

可在模型可见工具场景的系统上下文中增加简短约束：允许模型在需要工具前先用一句自然语言说明正在处理，但不得输出虚假的工具结果。该约束只影响模型生成倾向，不由服务端硬编码插入用户可见正文。

### 错误处理

- 工具执行失败时，保留失败前已经到达的正文和 thinking，并把工具错误作为过程节点展示。
- 若工具轮次超过上限，最终错误仍通过统一 `ApiResponse` / SSE error 文案展示，同时保留已到达的过程片段。
- 流结束后的历史回放不得覆盖更完整的本地流式正文，继续遵守 `stream-replay-stale-closure-overwrites-content` 记忆规则。

## 关键文件

- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：调整工具循环中的正文累积、工具回灌和最终保存逻辑。
- `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationService*Test.java` 或现有相关测试：补充工具前正文、工具后正文都保留的后端测试。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：确保 finish 收口不破坏已构建的 `timelineItems`。
- `frontend/user/tests/views/chat/useChatWorkspace.test.ts`：覆盖“正文 -> 工具 -> 正文”顺序。
- `frontend/user/src/views/ChatView.tsx` 与相关测试：验证时间线 DOM 顺序和视觉展示。
- `docs/features/chat/interleaved-process-timeline.md`：开发完成后更新当前真实实现说明。

## 测试策略

- 后端单测：模拟模型先输出正文、再返回工具调用、工具回灌后继续输出正文，断言最终保存内容和 SSE 事件都包含工具前后正文。
- 前端 Hook 测试：按 `message -> tool-call start -> tool-call complete -> message -> finish` 推送事件，断言 `timelineItems` 顺序不变。
- 前端渲染测试：断言消息体 DOM 顺序为正文片段、工具节点、后续正文片段。
- 构建验证：前后端都有改动时执行 `mvn compile`、`mvn test`、`npm run build`、`npm run test:run`。
- 浏览器验证：如消息展示样式或交互有变更，使用 CDP 打开聊天页并把截图保存到 `logs/`。

## 自检

- 无数据库结构变更。
- 无伪造模型 thinking 的需求。
- 工具前正文保留、工具后正文继续追加、finish 不覆盖时间线三条核心约束明确。
- 设计范围聚焦聊天流式过程，不包含无关 UI 改版。
