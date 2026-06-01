---
type: module_card
title: chat-ai-routing-module-card
summary: 记录聊天 AI 模型路由、候选池和故障切换的职责边界
tags:
  - chat
  - ai-routing
owned_paths:
  - backend/src/main/java/com/codingx/common/support/ai
  - backend/src/main/java/com/codingx/config/*Ai*Properties.java
  - backend/src/main/resources/db/init.sql
  - backend/src/main/resources/db/migration/*ai*
  - docs/features/chat/ai-*.md
related_docs:
  - docs/features/chat/ai-model-failover.md
  - docs/features/chat/ai-routing-defaults.md
  - docs/superpowers/memory/chat/ai-routing-selection-contract.md
entrypoints:
  - backend/src/main/java/com/codingx/common/support/ai/AiModelSelector.java
  - backend/src/main/java/com/codingx/common/support/ai/AiModelDispatchService.java
  - backend/src/main/java/com/codingx/config/DynamicAiProperties.java
last_verified_commit: 27df80c6
status: active
---

# Chat AI Routing Module

## Responsibilities

- 将系统配置表和静态 `AiProperties` 聚合成聊天候选模型池、provider 连接信息和路由策略。
- 按附件能力、thinking 能力、显式首选模型、候选优先级和候选 ID 生成有序模型目标列表。
- 在调度层按候选顺序执行首包探测、失败标记、熔断跳过和自动切换。

## Entry Points

- `AiModelSelector.selectChatCandidates(...)`：聊天请求进入模型调度前的候选排序入口。
- `DynamicAiProperties.chatCandidates()`：从 `setting` 表读取 `ai.chat.candidates.<slot>.*` 并组装动态候选池。
- `AiModelDispatchService`：消费排序后的候选列表，负责 provider 调用和 fallback。

## Invariants

- 候选池是模型路由的真实来源；候选必须包含 `id`、`provider`、`model` 才能参与选择。
- provider 缺失时对应候选会被过滤，不能让不可调用目标进入调度层。
- 图片附件存在时优先使用 `supports_vision=true` 候选；没有视觉候选时回退原候选池。
- thinking 请求优先使用 `supports_thinking=true` 候选；没有 thinking 候选时回退普通候选，避免空路由。
- 数据库 `setting` 会覆盖 YAML 骨架，修改运行时路由键位时必须同步 `init.sql`、迁移脚本、测试和功能文档。

## Extension Points

- 新 provider 通过 `ai.providers.<provider>.*` 系统配置和 provider 客户端注册接入。
- 新候选通过 `ai.chat.candidates.<slot>.*` 增加，排序由 `priority` 和候选 ID 保持稳定。
- 新能力过滤应优先放在 `AiModelSelector`，保持 `Controller` 和聊天业务编排不理解 provider 细节。

## Common Pitfalls

- 只改 `application.yml` 不改数据库初始化与迁移会导致新旧环境路由行为分裂。
- 把候选 ID 指针当成真实模型名会绕开 provider、能力和 fallback 配置。
- 增加候选字段时遗漏管理端设置页测试，容易造成配置入口和后端解析规则不一致。
