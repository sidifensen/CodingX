---
type: memory_update_report
title: chat-thinking-routing-gate-report
summary: 记录普通聊天请求关闭深度思考时的模型候选分桶与 thinking 事件门控契约更新
tags:
  - chat
  - ai-routing
source_commit: 126ec2c156825e96a5f858d1b3d7df5b5d543227
status: complete
---

# Memory Update Report

## Updated Memory

- `docs/superpowers/memory/chat/ai-routing-selection-contract.md`
  - 将旧的“普通聊天完全按 priority 排序”修正为“普通请求先排序非 thinking 候选，再将 thinking 候选作为 fallback”。
  - 明确 deep thinking 请求仍优先 `supports_thinking=true` 候选，缺少专项候选时回退普通候选池。
- `docs/superpowers/memory/chat/ai-routing-module-card.md`
  - 增加普通/思考请求分桶职责。
  - 增加调度层在首包缓冲前过滤 thinking 的约束，确保关闭深度思考时 reasoning 不算可见首包。

## Rejected Candidates

- 未新增 lesson 文档；本次知识属于 AI 路由模块契约更新，更新现有 contract/module card 比新增独立 lesson 更集中。

## Verification Anchor

- 代码修复提交：`126ec2c156825e96a5f858d1b3d7df5b5d543227`
