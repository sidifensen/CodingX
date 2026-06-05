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
last_verified_commit: 0aed2a06
status: active
---

# CodingX CLI TUI Module Card

## Responsibilities

- `CodingXCli` 是 Java CLI 进程入口，负责组装用户级配置、当前工作区、mock 事件源、事件渲染器和 TUI 启动器。
- `CliCommandRunner` 只做协议层命令分发；任务运行入口保持 TUI-only，`codingx` 和 `codingx tui` 启动全屏 TUI，`exec`、`resume`、`sessions` 返回 TUI-only 拒绝提示。
- `CodingXTuiLauncher` 通过 tui4j `Program` 启动 alt screen，真实运行会接管终端直到用户退出。
- `CodingXTuiModel` 维护 TUI 内存状态，包括事件窗口、任务输入框、当前工作区、运行状态，以及把用户任务交给 `AgentEventSource` 后追加渲染结果。

## Entry Points

- `cli/src/main/java/com/codingx/cli/CodingXCli.java`：进程入口和依赖组装。
- `cli/src/main/java/com/codingx/cli/command/CliCommandRunner.java`：命令分发与 TUI-only 入口约束。
- `cli/src/main/java/com/codingx/cli/tui/CodingXTuiModel.java`：TUI 状态模型和任务提交逻辑。
- `cli/src/main/java/com/codingx/cli/render/TerminalRenderer.java`：把 `AgentEvent` 转成可读终端文本。

## Invariants

- CLI 任务执行只能从 TUI 输入框触发，不能重新引入 `codingx exec "<任务>"` 作为并列产品形态。
- `login` 是配置命令，不属于任务运行模式；它只能写用户主目录配置，不能写当前项目工作区。
- TUI 单测不能启动真实 `Program`，应直接测试模型或使用 fake `TuiLauncher`，避免接管测试终端。
- 当前事件源是 `MockAgentEventSource`；接入真实后端时应替换事件源边界，不应把后端调用规则写进命令分发器。

## Extension Points

- TUI 视觉层可以拆出 header、transcript、input bar、status bar 等 focused renderer，保持 `CodingXTuiModel` 不膨胀。
- 真实 Agent Runtime 接入时优先新增 HTTP/SSE `AgentEventSource` 实现，并复用现有 `AgentEvent` 和 renderer 边界。
- 会话列表、恢复和模式切换应先进入 TUI 内部状态机，再考虑额外子命令；避免 CLI 再次分裂为非交互形态。

## Common Pitfalls

- 不要用 `mvn exec:java` 无参数做自动验证；它会进入全屏 TUI 并阻塞自动化流程。
- 不要并行运行 `mvn test` 和 `mvn exec:java` 共享 `cli/target`，Maven 构建目录竞争可能导致误判。
- 仓库 `docs/` 被 `.gitignore` 覆盖，新建 superpowers 文档提交时需要显式 `git add -f`。
