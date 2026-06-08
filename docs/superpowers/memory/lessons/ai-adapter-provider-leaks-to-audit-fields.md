---
type: lesson
title: ai-adapter-provider-leaks-to-audit-fields
summary: 防止把 OpenAI 兼容协议适配器名当作真实模型商写入聊天消息审计字段
tags:
  - chat
  - ai-routing
  - schema
last_verified_commit: 24f7301f148a5b0a73caa60b6b2d2e3be5d1dfd0
status: active
---

## Situation

聊天模型路由支持多个候选 provider，其中百炼和硅基流动都复用 `OpenAiCompatibleChatClient`。问题出现时，`AiModelDispatchService` 成功命中候选 `provider=bailian`、`model=qwen-plus-latest`，但对外 metadata 使用了 `AiProviderClient.provider()` 返回的内部客户端编码 `openai-compatible`，最终 `chat_message.provider` 也被写成了协议适配器名。这个问题不明显，因为模型调用本身是成功的，只有查看数据库审计字段或管理端配置对比时才会发现 provider 身份不一致。

## Why It Mattered

`chat_message.provider` 是聊天历史和后台审计里判断真实模型商的字段。如果它记录 `openai-compatible`，维护者无法区分同一协议适配器背后的百炼、硅基流动或后续兼容 provider，也会让历史统计、问题定位和候选池配置核对失真。更严重的是，未来如果继续按客户端编码做尝试记录或错误上下文，同一类 provider 的故障会被错误归因到适配器，而不是实际模型商。

## Rule

在 AI 路由中，`AiProviderClient.provider()` 只能用于内部客户端解析和适配器注册；对外 metadata、调度尝试记录、错误上下文和持久化审计字段必须使用 `AiModelTarget.candidate().getProvider()`。当修复这类泄漏时，还要考虑历史数据回填，并且只在模型名能唯一映射到 provider 时自动更新。

## When to Apply

当代码路径同时出现“候选池 provider”和“客户端 provider/协议适配器名”两种身份时应用这条规则，尤其是 `openai-compatible`、`stub`、聚合客户端、fallback 客户端或代理客户端参与路由时。检查点包括 `onMetadata(...)`、`getLastAttemptedProviders()`、异常消息、数据库字段、管理端统计和迁移脚本。

## When NOT to Apply

如果字段语义明确是内部适配器注册键、Spring Bean 选择键或协议类型指标，不应强行改成候选 provider。对于历史数据，如果同一个 `model` 在候选池中对应多个不同 provider，不能自动回填，必须保留原值或通过更强证据人工处理。
