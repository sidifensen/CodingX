---
type: module_card
title: codingx-cli-tui
summary: 记录 CodingX Java CLI TUI 入口、事件渲染和交互边界
tags:
  - cli
  - tui
  - agent
owned_paths:
  - cli/src/main/java/com/codingx/cli
  - cli/src/test/java/com/codingx/cli
related_docs:
  - docs/features/agent/java-cli-terminal-mvp.md
entrypoints:
  - cli/src/main/java/com/codingx/cli/CodingXCli.java
  - cli/src/main/java/com/codingx/cli/command/CliCommandRunner.java
  - cli/src/main/java/com/codingx/cli/tui/CodingXTuiModel.java
last_verified_commit: 8eab0db9
status: active
---

# CodingX CLI TUI Module Card

## Responsibilities

- `CodingXCli` 是 Java CLI 进程入口，负责组装用户级配置、当前工作区、mock 事件源、事件渲染器和 TUI 启动器。
- `CliCommandRunner` 只做协议层命令分发；任务运行入口保持 TUI-only，`codingx` 和 `codingx tui` 启动全屏 TUI，`exec`、`resume`、`sessions` 返回 TUI-only 拒绝提示。
- `CodingXTuiLauncher` 通过 tui4j `Program` 启动 alt screen，真实运行会接管终端直到用户退出。
- `CodingXTuiModel` 维护 TUI 内存状态，包括事件窗口、任务输入框、当前工作区、运行状态、计划模式和当前模型名；它只编排输入、事件源和视图刷新，不直接承担 header、transcript、status bar 文案映射。
- `TuiHeaderRenderer`、`TuiTranscriptRenderer`、`TuiStatusBarRenderer` 分别维护截图风格顶部品牌区、对话流/工具状态和底部模式栏，避免 TUI 模型继续膨胀为大而全的展示类。

## Entry Points

- `cli/src/main/java/com/codingx/cli/CodingXCli.java`：进程入口和依赖组装。
- `cli/src/main/java/com/codingx/cli/command/CliCommandRunner.java`：命令分发与 TUI-only 入口约束。
- `cli/src/main/java/com/codingx/cli/tui/CodingXTuiModel.java`：TUI 状态模型和任务提交逻辑。
- `cli/src/main/java/com/codingx/cli/tui/TuiHeaderRenderer.java`：截图风格顶部品牌、模型、工作区和 mock MCP/tools 状态。
- `cli/src/main/java/com/codingx/cli/tui/TuiTranscriptRenderer.java`：把 `AgentEvent` 转成 TUI 对话流、工具状态、命令输出、完成和错误行。
- `cli/src/main/java/com/codingx/cli/tui/TuiStatusBarRenderer.java`：底部 `Plan on/off`、运行状态和模型名。
- `cli/src/main/java/com/codingx/cli/render/TerminalRenderer.java`：保留日志式事件渲染边界，当前不再作为截图风格 transcript 输出。

## Invariants

- CLI 任务执行只能从 TUI 输入框触发，不能重新引入 `codingx exec "<任务>"` 作为并列产品形态。
- `login` 是配置命令，不属于任务运行模式；它只能写用户主目录配置，不能写当前项目工作区。
- TUI 单测不能启动真实 `Program`，应直接测试模型或使用 fake `TuiLauncher`，避免接管测试终端。
- 当前事件源是 `MockAgentEventSource`；接入真实后端时应替换事件源边界，不应把后端调用规则写进命令分发器。
- `shift+tab` 目前只切换 TUI 本地 `Plan on/off` 文案，不代表真实规划策略已经接入；真实策略应从 Agent Runtime 或会话状态返回。
- MCP 连接数、工具数、工具耗时和综合耗时当前是截图风格 mock 文案，不能被后端或测试当作真实遥测数据读取。

## Extension Points

- TUI 视觉层可以拆出 header、transcript、input bar、status bar 等 focused renderer，保持 `CodingXTuiModel` 不膨胀。
- 真实 Agent Runtime 接入时优先新增 HTTP/SSE `AgentEventSource` 实现，并复用现有 `AgentEvent` 和 renderer 边界。
- 会话列表、恢复和模式切换应先进入 TUI 内部状态机，再考虑额外子命令；避免 CLI 再次分裂为非交互形态。
- 当真实 MCP 或工具调用接入后，应优先扩展 `AgentEvent` payload 与 `TuiTranscriptRenderer` 映射，不要把工具名、耗时和权限审批文案硬编码回 `CodingXTuiModel`。

## Common Pitfalls

- 不要用 `mvn exec:java` 无参数做自动验证；它会进入全屏 TUI 并阻塞自动化流程。
- 不要并行运行 `mvn test` 和 `mvn exec:java` 共享 `cli/target`，Maven 构建目录竞争可能导致误判。
- 仓库 `docs/` 被 `.gitignore` 覆盖，新建 superpowers 文档提交时需要显式 `git add -f`。
