---
type: contract
title: codex-local-tool-runtime-contract
summary: 约束本地工具在聊天和管理端中的可见性、执行目录、输出和不可用状态
tags:
  - tool
  - chat
  - local-runtime
owned_paths:
  - backend/src/main/java/com/codingx/tool
  - backend/src/main/java/com/codingx/chat/application/service/chat
related_docs:
  - docs/superpowers/memory/tool/codex-local-tool-runtime-module-card.md
entrypoints:
  - backend/src/main/java/com/codingx/tool/application/service/ChatToolExecutionService.java
  - backend/src/main/java/com/codingx/tool/application/service/CodexBuiltinChatToolExecutor.java
last_verified_commit: d8802bea
status: active
---

# Codex Local Tool Runtime Contract

## Scope

本契约覆盖 CodingX 后端中 Codex 风格工具的注册、展示、自动调用和本地文件执行语义。它不覆盖上游 Codex CLI 的完整 session、多代理、插件市场和 hosted web/image 工具实现。

## Producers And Consumers

- Producer：实现 `ChatToolExecutor` 的 Java 执行器、`tool` 表种子数据、管理端工具配置。
- Consumer：管理端工具页、用户态工具调试接口、聊天主流程中的模型工具调用循环。

## Interface Rules

- 工具执行输入统一以 JSON 字符串优先，普通文本作为兼容回退。
- 命令类工具必须返回 `exitCode`、`timedOut`、`durationMs`、`workingDirectory` 等可验证元数据。
- 文件编辑类工具必须返回是否已应用、实际工作目录和 diff 预览。
- 模型可见工具 schema 必须来自后端真实执行器能力，不能仅从数据库展示配置生成。
- 工具不可用时应返回明确错误或不可用状态，不能创建假 session、假 agent 或假插件安装结果。

## Workspace Rules

- 当前会话绑定本地 workspace 时，工具必须在该目录执行。
- 请求显式传入 repositoryPath 时，只有在其通过服务端路径校验后才可成为本次执行目录。
- 无本地 workspace 的云端会话不能默认获得本地文件写权限；若回退后端进程目录，输出必须清楚标记。

## Compatibility Notes

- 上游 OpenAI Codex 的核心工具规划位于 `codex-rs/core/src/tools/spec_plan.rs`：它按 shell、MCP resource、utility、collaboration、MCP runtime、dynamic、extension、hosted tools 分层注册。
- 本项目当前优先适配本地 Java 可执行子集：`shell_command`、`exec_command`、`write_stdin`、`apply_patch`、`update_plan`、`view_image`、`tool_search`、`test_sync_tool`。
- 依赖 Codex session 的多代理和插件工具在没有本地等价实现前应标记为 unsupported 或 disabled。
