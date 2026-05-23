# Codex Local Tool Runtime Design

**Date:** 2026-05-23
**Status:** Approved

## Background

当前 `tool` 表已经种了大量 Codex 风格工具，但其中不少只是管理端可见配置或内存态占位。用户在 Electron 本地 workspace 对话时，真正需要的是模型能自主选择本地工具，并且后端在绑定的本地目录中真实读写文件、执行命令、应用补丁，再把工具结果回灌给模型完成回答。

OpenAI Codex 上游的工具规划按 shell、MCP resource、utility、collaboration、MCP runtime、dynamic、extension、hosted tools 分层注册。本项目先适配 Java 后端可真实执行的本地子集，并把不具备运行时依赖的工具标为不可用或不暴露给模型。

## Scope

本次实现覆盖：

- 真实工具清单：从 Java 执行器注册能力生成模型可见工具 schema，并与数据库配置合并展示。
- 本地执行目录：聊天会话优先使用绑定 workspace 或显式 `repositoryPath`，工具输出必须回显实际目录。
- 模型自动调用：支持 OpenAI 兼容响应中的 tool call，执行工具后继续请求模型，最多循环有限轮次。
- 可执行工具子集：`shell_command`、`exec_command`、`write_stdin`、`apply_patch`、`update_plan`、`view_image`、`tool_search`、`test_sync_tool`。
- 不可用工具：多代理、插件安装、权限申请、MCP resource 等没有真实 Java 后端的能力，不再返回假成功。
- 数据种子：更新工具描述和启用态，避免管理端把占位工具误展示为可用。

不覆盖：

- 真实 Codex 多代理 session。
- 真实插件市场安装。
- 完整 MCP connection manager。
- hosted web search/image generation 工具。

## Architecture

新增 `ChatToolSpecService` 负责把已注册且启用的本地工具转换为 OpenAI function tool schema。schema 不从数据库单独生成，避免出现“表里有但不可执行”的假工具。

新增 `AiToolCall`、`AiToolCallResult` 等模型层数据结构，扩展 `AiStreamHandler` 以接收 tool call 事件。`OpenAiStyleStreamParser` 负责解析 OpenAI 兼容 SSE 中的 `tool_calls` 增量和非流式完整响应形态。

`OpenAiCompatibleChatClient` 和 `DeepSeekOkHttpChatClient` 在请求体中带上 `tools`，并在流式响应中把 tool call 事件转交给上层。`RoutingAiChatClient` 继续保留旧接口，同时新增支持工具循环的接口。

`ChatApplicationService` 在普通模型调用前构造工具运行上下文：解析会话 workspace 或 `repositoryPath`，绑定 `ChatToolExecutionContext`，当模型发出 tool call 时调用 `ChatToolExecutionService`，保存/推送工具步骤，然后把工具结果作为系统证据追加到下一轮模型输入。

## Data Flow

1. Electron 前端绑定本地目录并创建/选择本地 workspace。
2. 用户在聊天中提问，并保持本地 workspace 上下文。
3. 后端构造 AI history，同时注入可用工具 schema。
4. 模型返回普通文本时，现有流式链路保持不变。
5. 模型返回 tool call 时，后端按工具名和 JSON 参数执行 Java 工具。
6. 工具输出保存为 execution step，并通过 SSE `mcp-call` 或 step 事件展示。
7. 后端把工具结果追加到模型输入，再请求模型整理最终回答。

## Error Handling

- 工具不存在、禁用或无执行器时返回中文错误，并写入失败 step。
- 工具执行异常通过 `BusinessException` 或统一异常链路返回，不泄露内部栈给前端。
- tool-call 循环设置最大轮次，超过后以中文提示终止，避免模型无限调用工具。
- 无本地 workspace 时，文件写入类工具不应静默落到错误目录；输出元数据必须包含实际目录。

## Testing

- 单元测试先覆盖工具 schema 只暴露真实启用执行器。
- 单元测试覆盖 `shell_command`、`apply_patch`、`exec_command` 在临时 workspace 中真实读写文件。
- 单元测试覆盖 OpenAI SSE tool call 解析。
- 应用服务测试覆盖模型发出 tool call 后，后端执行工具并把结果追加给第二轮模型。
- 数据迁移测试或文本校验覆盖工具种子不再把占位工具标为可用。

## Self Review

- 无 TBD/TODO。
- 范围聚焦在可落地 Java 本地工具和聊天自动调用闭环。
- 上游 Codex 不可直接移植的运行时能力已明确降级策略。
