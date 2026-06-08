# 聊天桌面目标模式

## 功能用途

桌面端聊天工作台提供“目标模式”入口，用户可以在普通对话和目标跟进之间切换。开启后，前端把本轮聊天流请求标记为 `planMode=true`，后端系统提示会引导模型在大型、多步骤、代码开发、调试修复，或用户明确要求创建/跟进目标时，优先使用 `get_goal`、`create_goal`、`update_goal` 维护目标进度。

## 使用入口

用户在聊天输入区底部工具栏点击“目标模式”按钮即可切换。按钮与“深度思考”相邻，开启后使用实心前景色提示后续请求会进入目标模式；关闭后恢复普通描边状态。

聊天页右侧只在目标模式开启时显示悬浮目标进度窗。该窗口不是新的后端目标查询接口，而是基于当前会话已有的 `executionSteps` 和目标模式流式状态生成轻量进度视图，普通聊天生成不会误显示目标进度。

## 核心流程

1. 用户点击“目标模式”按钮后，`ChatView` 调用 `setGoalModeEnabled` 更新 `useChatWorkspace` 中的本地状态。该状态只影响后续发送，不回溯修改已发出的流请求。
2. 用户发送消息时，`submitMessage` 把 `goalModeEnabled` 传给 `buildStreamRequestUrl`。该函数仅在目标模式为 true 时追加 `planMode=true`，普通模式或缺省调用不携带该参数，保持既有聊天语义。
3. 后端 `ChatStreamRequestApplicationService` 继续按已有 `planMode` 字段构造 `SendChatMessageCommand`。`ChatApplicationService.buildPlanModeContext` 从 `resources/prompt/plan-mode-goal-context.st` 读取 CLI 与桌面端通用的“规划/目标模式”提示词，要求模型先判断用户是要方案还是要执行。
4. 当用户明确要求执行、创建目标、写目标、跟进进度，或任务明显需要多步落地时，系统提示要求模型先调用 `get_goal` 检查当前线程目标；没有活动目标时调用 `create_goal`，后续每完成关键步骤后调用 `update_goal` 记录完成或阻塞原因。
5. 前端 `GoalProgressPanel` 从当前会话 `executionSteps` 统计完成比例。步骤包含 `ERROR` 或 `CANCELLED` 时优先显示异常或取消；没有步骤但正在生成时显示等待模型拆解目标，避免目标窗空白。

## 关键文件

- `frontend/user/src/views/chat/types.ts`：扩展 `ChatWorkspaceController`，暴露 `goalModeEnabled` 与 `setGoalModeEnabled`。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：维护目标模式状态，并在发送、重新生成、编辑重发路径中透传到聊天流 URL。
- `frontend/user/src/views/ChatView.tsx`：渲染目标模式按钮和右侧悬浮目标进度窗。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：按 `planMode` 加载规划/目标模式系统提示，引导目标工具调用。
- `backend/src/main/resources/prompt/plan-mode-goal-context.st`：维护 CLI 与桌面端通用的规划/目标模式提示词正文。
- `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationServiceTest.java`：验证目标模式提示包含桌面端和目标工具约束，并确认提示词资源化。
- `frontend/user/tests/views/chat/useChatWorkspace.test.ts`：验证目标模式请求参数。
- `frontend/user/tests/views/ChatView.test.tsx`：验证目标模式按钮和进度窗展示。

## 关键数据结构

`goalModeEnabled` 是前端工作区本地布尔状态，默认 false。开启时只通过聊天流 query 参数影响后端，不新增数据库字段。

`planMode=true` 是复用的后端请求参数。后端收到后注入规划/目标模式提示词；是否真正创建目标由模型根据用户意图和当前活动目标状态，通过 Codex 目标工具执行。

`executionSteps` 是进度窗的数据来源。完成度按 `COMPLETED` 步骤数除以总步骤数计算；`RUNNING`、`PENDING` 或流式生成会显示进行中；`ERROR`、`FAILED`、`CANCELLED`、`CANCELED` 会显示对应终态。

## 测试与验证

- `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.test.ts -t "目标模式"`
- `cd frontend/user && npm run test:run -- tests/views/ChatView.test.tsx -t "目标模式"`
- `cd backend && mvn -Dtest=ChatApplicationServiceTest#planModeShouldInjectDesktopGoalToolGuidance test`
- `cd backend && mvn -Dtest=ChatApplicationServiceTest#planModePromptShouldBeStoredAsPromptResource test`
- 前端完整验证：`cd frontend/user && npm run build`、`cd frontend/user && npm run test:run`
- 后端完整验证：`cd backend && mvn compile`、`cd backend && mvn test`
- 浏览器验证：启动后端与用户前端后，打开 `http://localhost:5002`，验证目标模式按钮和 `goal-progress-panel` 在亮色/暗色下背景不透明、文本可读，并把截图或计算样式证据保存到 `logs/`。
