---
type: contract
title: admin-feedback-management-contract
summary: 管理端反馈管理 API 与三页前端消费契约
tags:
  - admin
  - feedback
owned_paths:
  - frontend/admin/src/api/adminChatApi.ts
  - frontend/admin/src/pages/FeedbackPage.tsx
  - frontend/admin/src/pages/FeedbackDetailPage.tsx
  - frontend/admin/src/pages/FeedbackReferencePage.tsx
related_docs:
  - docs/superpowers/memory/admin/feedback-management-module-card.md
entrypoints:
  - frontend/admin/src/api/adminChatApi.ts
last_verified_commit: df295563eedea6ac3e6b6e500b3487c9c7b12db7
status: active
---

# Admin Feedback Management Contract

## Scope

本契约覆盖管理端反馈列表、反馈详情和反馈引用来源三页的前端消费规则。它不覆盖用户端点赞/点踩提交逻辑，也不改变后端现有反馈查询接口。

## Producers And Consumers

- Producer: `GET /api/admin/chat/feedbacks`，返回反馈分页列表。
- Producer: `GET /api/admin/chat/feedbacks/{feedbackId}`，返回单条反馈详情与关联消息上下文。
- Producer: `GET /api/admin/chat/feedbacks/{feedbackId}/references`，返回反馈消息对应的引用来源列表。
- Consumer: `AdminChatApi.listFeedbacks(query)`，负责拼接分页、关键词和投票筛选参数。
- Consumer: `AdminChatApi.getFeedbackDetail(feedbackId)`，负责按反馈 ID 获取详情。
- Consumer: `AdminChatApi.listFeedbackReferences(feedbackId)`，负责按反馈 ID 获取引用来源。
- Consumer: 反馈三页只消费上述 API 方法返回的类型化结构。

## Interface Rules

- 列表路径：`GET /api/admin/chat/feedbacks`
- 列表查询参数：
  - `current`：页码，从 1 开始。
  - `size`：每页条数，当前管理端页面固定使用 10。
  - `keyword`：可选，匹配原因或评论等文本字段。
  - `vote`：可选，`1` 表示点赞，`-1` 表示点踩，未传表示全部。
- 列表返回分页结构：`records`、`total`、`size`、`current`、`pages`。
- 列表项字段：`id`、`messageId`、`conversationId`、`userId`、`vote`、`reason`、`comment`、`createdAt`、`updatedAt`。
- 详情路径：`GET /api/admin/chat/feedbacks/{feedbackId}`
- 详情返回字段在列表项基础上增加：`conversationTitle`、`messageRole`、`messageContent`。
- 引用路径：`GET /api/admin/chat/feedbacks/{feedbackId}/references`
- 引用列表项字段：`id`、`runId`、`messageId`、`conversationId`、`sourceType`、`title`、`url`、`siteName`、`snippet`、`rankNo`、`createdAt`。

## Invariants

- 页面不得把空字符串关键词传给后端；筛选提交前必须 trim。
- “全部”投票筛选不得传 `vote=0`，应省略 `vote` 参数。
- 详情和引用来源请求必须使用路由中的原始 `feedbackId` 字符串。
- 详情页和引用页在缺失字段时展示 `-`，避免把 `undefined` 或 `null` 暴露给管理员。
- 引用来源外链必须使用新窗口打开并设置 `rel="noreferrer"`。
- 前端 loading、empty、error 三类状态必须可被自动化测试稳定定位。

## Compatibility Notes

- 历史反馈记录可能没有 `reason`、`comment`、`conversationTitle` 或引用来源，页面应保持只读可用。
- 当前反馈管理没有写操作；未来新增处理状态时必须补充 API、权限、审计和功能文档。
- Ant Design 迁移只能改变前端呈现层，不应改变接口路径、字段名和分页行为。
