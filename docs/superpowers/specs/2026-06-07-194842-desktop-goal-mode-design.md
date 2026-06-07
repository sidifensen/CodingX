# Desktop Goal Mode Design

## Goal

为桌面端聊天工作台补齐“目标模式”，让用户能像 CLI 一样在普通模式和目标模式之间切换。目标模式开启后，本轮聊天请求应携带已有 `planMode` 信号，引导模型在大型编码任务或用户明确要求目标时优先使用 `get_goal`、`create_goal`、`update_goal` 维护目标进度，并在聊天页右侧显示一个悬浮目标进度窗。

## Problem

当前后端已经支持 `planMode` 请求参数，也已经内置 Codex 目标工具，但桌面端没有显式入口。用户在软件端发起大型项目任务时，界面无法提示模型进入目标跟进语境，也没有把已有执行步骤转成目标进度的轻量视图。后端 `planMode` 系统提示仍强调 CLI Plan mode，容易让桌面端目标模式语义不清。

## Scope

本次只复用现有聊天流接口和执行步骤数据，不新增数据库表、目标持久化接口或 Electron 主进程能力。目标进度窗展示当前会话内的实时执行步骤和流式状态；真正的目标生命周期仍由 Codex 内置工具完成。桌面端目标模式状态先作为聊天工作区本地状态处理，切换后影响后续发送的流请求，不追溯修改已发送请求。

## Design

### User Entry

聊天输入工具栏新增“目标模式”按钮，和现有“深度思考”按钮保持相邻、紧凑、可扫描。用户点击后切换 `goalModeEnabled` 状态；开启态使用实心前景色表达当前请求会进入目标模式，关闭态使用普通描边样式。按钮使用 lucide 图标并提供 `aria-label="切换目标模式"`，保证测试和辅助技术能稳定定位。

### Request Flow

`useChatWorkspace` 新增 `goalModeEnabled` 和 `setGoalModeEnabled`，并暴露到 `ChatWorkspaceController`。`submitMessage` 构建 SSE URL 时把该状态传入 `buildStreamRequestUrl`。`buildStreamRequestUrl` 新增可选 `goalModeEnabled` 参数；当它为 true 时写入 `planMode=true`，为 false 或缺省时不写该参数，避免普通模式改变现有后端行为。

### Floating Progress Panel

`ChatView` 根据 `goalModeEnabled`、`isStreaming` 和 `executionSteps` 计算是否展示目标进度窗。展示条件为：目标模式开启，或当前已有执行步骤，或正在流式生成。进度百分比优先按 `executionSteps` 中完成状态占比计算；没有步骤但正在流式时显示进行中占位；出现 `ERROR` 或 `CANCELLED` 时在状态文案中明确失败或取消。面板固定在聊天区域右侧，使用项目主题令牌 `bg-surface`、`bg-surface-container`、`border-border`、`text-foreground`、`text-muted`，确保亮色和暗色都不透明、可读。

### Backend Goal Prompt

`ChatApplicationService.buildPlanModeContext` 改为“规划/目标模式”通用提示，不再只写 CLI。提示应说明该模式可来自 CLI 或桌面端；当任务是大型、多步骤、代码开发、调试修复，或用户明确要求创建/跟进目标时，模型应先用 `get_goal` 检查活动目标，再按需调用 `create_goal`，每完成关键步骤后用 `update_goal` 标记完成或阻塞。提示仍保留规划模式的安全边界：如果用户只是要求方案，不应主动执行写入类动作；如果用户要求落地执行，则按目标流程推进。

### Documentation

新增功能文档 `docs/features/chat/desktop-goal-mode.md` 并更新 `docs/features/index.md`。文档记录当前真实实现：入口、请求参数、进度来源、后端提示词边界和验证命令。

## Alternatives

1. 新增独立 `goalMode` 后端参数。语义更直观，但需要扩展 controller、request、command 和兼容旧调用，当前已有 `planMode` 能承载目标模式，因此不采用。
2. 新建目标进度后端 API。可展示真实目标工具状态，但需要新增状态存储或线程目标查询链路，超出本次桌面入口和调试目标。
3. 只改提示词不改 UI。实现最快，但用户仍无法在桌面端显式切换，也看不到右侧目标进度窗，不满足需求。

## Testing

- 前端逻辑测试：验证 `buildStreamRequestUrl` 在目标模式开启时追加 `planMode=true`，关闭时不追加。
- 前端交互测试：验证聊天页渲染目标模式按钮、点击会调用 `setGoalModeEnabled(true)`，目标模式/执行步骤存在时出现右侧进度窗，普通空闲状态不出现。
- 后端单元测试：验证规划/目标模式系统提示包含桌面端、`get_goal`、`create_goal`、`update_goal` 和大型任务目标创建约束。
- 浏览器验证：启动后端和用户前端，通过 CDP 打开聊天页，检查目标模式按钮与悬浮窗在亮色/暗色主题下背景非透明、文本可读，并保存截图到 `logs/`。
