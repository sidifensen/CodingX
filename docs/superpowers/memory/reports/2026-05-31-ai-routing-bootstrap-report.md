---
type: decision
title: ai-routing-bootstrap-report
summary: 为聊天 AI 路由子系统补充最小仓库记忆基线
tags:
  - chat
  - ai-routing
owned_paths:
  - docs/superpowers/memory/chat/ai-routing-module-card.md
  - docs/superpowers/memory/chat/ai-routing-selection-contract.md
related_docs:
  - docs/features/chat/ai-model-failover.md
  - docs/features/chat/ai-routing-defaults.md
last_verified_commit: 27df80c6
status: active
---

# AI Routing Bootstrap Report

## Scope

本次只为聊天 AI 模型路由补充最小记忆基线，覆盖候选池来源、选择器排序契约和常见维护风险。

## Evidence

- `AiModelSelector` 负责候选能力过滤、首选模型排序和 provider 装配。
- `DynamicAiProperties` 负责从系统配置表读取动态 provider、endpoint 和候选池。
- `docs/features/chat/ai-model-failover.md` 与 `docs/features/chat/ai-routing-defaults.md` 已记录当前候选池默认顺序和故障切换行为。

## Created Docs

- `docs/superpowers/memory/chat/ai-routing-module-card.md`
- `docs/superpowers/memory/chat/ai-routing-selection-contract.md`

## Gaps

- provider 客户端注册和 `AiModelDispatchService` 的首包探测细节仍可后续单独沉淀。
- 管理端系统配置页与后端运行时设置键的完整映射仍未形成独立契约。
