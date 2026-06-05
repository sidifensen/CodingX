# Java CLI 基础终端

## 功能用途

提供 CodingX 独立 CLI 的第一版基础终端入口，让开发者可以先在本地通过 Java CLI 查看 AgentEvent 风格的终端输出。当前版本参考 MewCode 公开介绍中的 CLI Coding Agent、流式多轮、工具事件、权限审批和 Agent Loop 思路，但只实现最小终端骨架，不直接执行模型调用、工具调用或后端任务持久化。

## 使用入口

- `codingx login <serverUrl> <satoken>`：把后端地址和登录令牌保存到用户主目录 `.codingx/cli.yml`，避免 satoken 写进项目仓库。
- `codingx exec "<任务>"`：在当前目录启动一轮本地 mock Agent 任务并打印终端事件流。
- `codingx resume` / `codingx sessions`：预留给后端 AgentSession 接入后的会话恢复和会话列表能力。

## 核心流程

1. 用户运行 CLI 命令后，`CodingXCli` 使用用户主目录、当前工作目录、mock 事件源和终端渲染器创建 `CliCommandRunner`。命令分发器只负责解析协议层输入，不直接承载模型决策或工具执行细节。
2. 用户执行 `login` 时，`CliCommandRunner` 校验 `serverUrl` 和 `satoken` 参数数量；参数缺失时返回中文用法提示，参数完整时通过 `CliConfigStore` 使用 SnakeYAML 写入用户主目录配置。该流程不会读取或写入当前工作区文件，避免登录令牌进入 Git 仓库。
3. 用户执行 `exec` 时，命令分发器把 `exec` 后的全部参数拼成任务文本，并连同当前工作区路径传给 `MockAgentEventSource`。mock 事件源生成有序 `AgentEvent`，覆盖会话开始、任务开始、助手输出、工具开始、命令输出、工具完成和任务完成状态。
4. `TerminalRenderer` 按事件类型生成终端文本；助手正文直接输出，工具和命令事件加上 `[tool]`、`[cmd:stdout]` 等前缀，审批、错误、中断和完成事件保留独立语义。后续接入真实后端时，只需要替换 `AgentEventSource`，渲染语义可以继续复用。
5. 当前版本的 `resume` 和 `sessions` 返回占位提示，明确说明要等后端 AgentSession 接入后启用。这样 CLI 用户能先看到产品形态，后端控制器、数据库表和真实 Agent Runtime 可以在下一阶段按事件协议逐步演进。

## 关键文件

- `cli/src/main/java/com/codingx/cli/CodingXCli.java`：CLI 进程入口，负责组装命令运行所需依赖。
- `cli/src/main/java/com/codingx/cli/command/CliCommandRunner.java`：命令解析和流程编排，覆盖 `login`、`exec`、`resume`、`sessions`。
- `cli/src/main/java/com/codingx/cli/agent/AgentEvent.java`：Agent 事件信封，承载会话、轮次、序号、类型、载荷和创建时间。
- `cli/src/main/java/com/codingx/cli/agent/MockAgentEventSource.java`：MVP mock 事件流，用于先跑通终端体验。
- `cli/src/main/java/com/codingx/cli/render/TerminalRenderer.java`：终端文本渲染，负责把统一事件协议转成可读输出。
- `cli/src/main/java/com/codingx/cli/config/CliConfigStore.java`：用户级配置读写，令牌只保存在用户主目录。

## 关键数据结构

- `AgentEventType`：定义 CLI MVP 当前识别的事件类型，包括 `SESSION_STARTED`、`TURN_STARTED`、`ASSISTANT_DELTA`、`TOOL_STARTED`、`COMMAND_OUTPUT_DELTA`、`APPROVAL_REQUESTED`、`TURN_COMPLETED`、`ERROR` 等。
- `CliConfig`：保存后端地址、satoken、审批策略和最近会话标识；首次运行时默认后端地址为 `http://localhost:5001`，审批策略为 `conservative`。
- `AgentEventSource`：事件源抽象边界；当前实现是 `MockAgentEventSource`，下一阶段可新增 HTTP/SSE 客户端对接后端 Agent API。

## 测试与验证

- `cd cli && mvn test`
- `cd cli && mvn -q exec:java -Dexec.mainClass=com.codingx.cli.CodingXCli -Dexec.args="exec 分析这个项目"`
