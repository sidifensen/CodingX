---
type: module_card
title: codex-local-tool-runtime
summary: 记录 CodingX 后端工具执行器从配置展示走向本地可执行工具的关键边界
tags:
  - tool
  - chat
  - local-runtime
owned_paths:
  - backend/src/main/java/com/codingx/tool
  - backend/src/main/java/com/codingx/chat/application/service/chat
  - backend/src/main/resources/db/migration/*tool*
related_docs:
  - docs/superpowers/memory/tool/codex-local-tool-runtime-contract.md
entrypoints:
  - backend/src/main/java/com/codingx/tool/application/service/CodexBuiltinChatToolExecutor.java
  - backend/src/main/java/com/codingx/tool/application/service/ChatToolRegistry.java
  - backend/src/main/java/com/codingx/tool/application/service/ChatToolExecutionContext.java
last_verified_commit: d8802bea
status: active
---

# Codex Local Tool Runtime Module Card

## Responsibilities

- `ChatToolRegistry` 负责按工具编码聚合 Java 执行器，防止同一工具编码被多个执行器重复注册。
- `CodexBuiltinChatToolExecutor` 是当前 Codex 风格内置工具的主要执行入口，已包含命令执行、`apply_patch`、计划状态、图片读取、MCP 资源占位、子代理占位等能力。
- `ChatToolExecutionContext` 通过线程上下文传递本次工具执行目录。聊天链路应优先绑定当前会话的本地 workspace；没有本地 workspace 时只能回退到后端进程目录。
- `tool` 数据表保存管理端可见配置；它不是执行器来源的唯一事实来源，实际可执行能力必须以已注册的 Java 执行器为准。

## Entry Points

- 用户调试入口：`POST /api/chat/tools/{toolCode}/invoke`，经 `ChatToolUserService` 做白名单、启用态和高风险确认校验。
- 管理端入口：`AdminChatToolService` 提供列表、健康状态、探测和手动调用。
- 聊天主流程：`ChatApplicationService` 目前只会按 MCP 意图短路调用 `ChatMcpExecutionService`，尚未把 Codex 工具作为模型可自主选择的工具集合传给模型层。

## Invariants

- 所有本地文件写入必须绑定到明确的工作目录，并在结果元数据中回显实际 `workingDirectory`。
- `apply_patch` 必须真实修改文件；执行后应返回可检查的 diff 预览，不能只返回“模拟成功”。
- 对上游 Codex 中依赖真实 Codex session、插件市场、MCP connection manager 或多代理线程的工具，如果本项目没有可执行后端，必须明确返回不支持或不可用，不能用内存态假成功替代。
- 后端新增/修改文件必须有能解释业务意图和边界条件的必要注释。

## Extension Points

- 新增本地工具时优先实现 `ChatToolExecutor`，再把工具编码纳入注册和种子数据。
- 需要暴露给模型自主调用时，应补充工具 schema，并让模型 tool-call 循环走同一个执行服务，避免聊天自动调用和手动调试两套实现分叉。
- 需要接入真实 MCP 资源时，应新增 MCP connection manager 适配层，再替换当前资源列表占位实现。

## Common Pitfalls

- 只往 `tool` 表插入记录会让管理端“看起来有工具”，但模型并不会自动调用，后端也不一定有执行器。
- 子代理、插件安装、权限申请这些 Codex 上游工具依赖 Codex 会话运行时；没有等价运行时时不应返回完成态。
- 命令执行如果没有绑定会话 workspace，会在后端启动目录运行，容易造成“看似执行了但没有改到用户项目”的问题。
