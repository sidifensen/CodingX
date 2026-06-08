---
type: contract
title: chat-ai-routing-selection-contract
summary: 记录聊天模型候选选择的输入、输出和排序契约
tags:
  - chat
  - ai-routing
owned_paths:
  - backend/src/main/java/com/codingx/common/support/ai/AiModelSelector.java
  - backend/src/main/java/com/codingx/config/DynamicAiProperties.java
  - backend/src/test/java/com/codingx/support/ai/AiModelSelectorTest.java
related_docs:
  - docs/superpowers/memory/chat/ai-routing-module-card.md
  - docs/features/chat/ai-model-failover.md
entrypoints:
  - backend/src/main/java/com/codingx/common/support/ai/AiModelSelector.java
last_verified_commit: 126ec2c156825e96a5f858d1b3d7df5b5d543227
status: active
---

# Chat AI Routing Selection Contract

## Scope

本契约覆盖聊天请求进入 provider 调度前的候选选择，不覆盖 provider HTTP 调用、SSE 输出或前端展示。

## Producers And Consumers

- 生产者：`DynamicAiProperties` 从系统配置读取 provider 和候选池，`AiProperties` 提供静态兼容骨架。
- 消费者：`AiModelSelector` 输出 `AiModelTarget` 列表，`AiModelDispatchService` 按顺序调用并处理 fallback。

## Interface Rules

- 输入包含 `preferredModel`、`thinkingEnabled` 和可选附件列表。
- `preferredModel` 非空时拥有最高排序优先级，但仍必须匹配候选 ID 且通过 provider 装配。
- 动态候选池非空时覆盖静态候选池；动态 provider 配置存在时覆盖静态 provider 映射。
- 候选排序在能力过滤后执行；非空 `preferredModel` 会先提升匹配候选。
- 未指定 `preferredModel` 且 `thinkingEnabled=false` 时，选择器必须直接按候选池 `priority` 和候选 ID 排序；`supports_thinking` 不参与普通请求排序。
- `thinkingEnabled=true` 时，选择器优先保留 `supports_thinking=true` 候选；若没有 thinking 候选再回退普通候选池。

## Invariants

- 选择器只能输出 provider 可解析的候选目标。
- 能力过滤不能让请求因缺少专项候选而直接无候选；thinking 和视觉都必须保留明确 fallback 路径。
- 旧式单模型兼容只作为没有候选池时的安全回退，不应成为新功能的主要配置入口。

## Compatibility Notes

- 历史实现支持 `ai.chat.default_model` 和 `ai.chat.deep_thinking_model` 作为候选池内的首选排序指针；当前实现已通过迁移清理这两个 setting 键。
- 未指定 `preferredModel` 时，不保留独立默认模型指针；普通聊天直接按候选池 `priority` 排序，deep thinking 在 thinking 能力过滤后按 `priority` 排序。
