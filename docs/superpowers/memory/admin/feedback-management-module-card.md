---
type: module_card
title: admin-feedback-management
summary: 管理端反馈管理用于排查用户点赞/点踩反馈、关联消息与引用来源
tags:
  - admin
  - feedback
owned_paths:
  - frontend/admin/src/pages/FeedbackPage.tsx
  - frontend/admin/src/pages/FeedbackDetailPage.tsx
  - frontend/admin/src/pages/FeedbackReferencePage.tsx
  - frontend/admin/src/api/adminChatApi.ts
related_docs:
  - docs/superpowers/memory/admin/feedback-management-contract.md
entrypoints:
  - frontend/admin/src/App.tsx
  - frontend/admin/src/components/Layout.tsx
  - frontend/admin/src/pages/FeedbackPage.tsx
last_verified_commit: df295563eedea6ac3e6b6e500b3487c9c7b12db7
status: active
---

# Admin Feedback Management Module Card

## Responsibilities

- 管理端反馈管理面向运营和排查场景，展示 `chat_message_feedback` 中的点赞/点踩记录。
- 反馈详情页负责把单条反馈与会话标题、消息角色、消息正文聚合展示，便于判断反馈上下文。
- 反馈引用页负责展示该反馈对应消息的 `chat_message_reference` 来源列表，辅助追溯回答依据。
- 前端页面只消费 `AdminChatApi` 集中封装后的接口结果，不在页面内重复解析 `fetch` 响应。

## Entry Points

- `frontend/admin/src/components/Layout.tsx` 提供侧边栏“反馈管理”入口。
- `frontend/admin/src/App.tsx` 承载 `/feedbacks`、`/feedbacks/:feedbackId`、`/feedbacks/:feedbackId/references` 三个路由。
- `frontend/admin/src/api/adminChatApi.ts` 封装反馈列表、详情、引用来源三个只读接口。
- `frontend/admin/src/pages/FeedbackPage.tsx` 渲染反馈列表、关键词筛选、投票筛选和分页。
- `frontend/admin/src/pages/FeedbackDetailPage.tsx` 渲染反馈详情和引用页跳转。
- `frontend/admin/src/pages/FeedbackReferencePage.tsx` 渲染引用来源列表和外链入口。

## Invariants

- 反馈管理是只读排查页面，不能隐式修改反馈、消息或引用来源数据。
- `vote = 1` 展示为点赞，`vote = -1` 展示为点踩，未知值必须降级为短横线或中性展示。
- 路由中的 `feedbackId` 必须以字符串形式传递给 API，避免数据库长整型 ID 在浏览器侧丢精度。
- 前端错误提示必须使用 `AdminChatApi` 抛出的后端错误文案，不在页面内改写错误语义。
- 页面必须支持亮色和暗色主题，表格、筛选控件、弹层、滚动条和空态在暗色下都要可读。

## Extension Points

- 若未来增加反馈处理状态、分派、备注或导出能力，应先补写接口契约和权限规则。
- 若需要反馈统计大盘，应优先在 Dashboard 聚合接口扩展指标，不让分页列表页面自行拼统计。
- 若更多管理端页面迁移到 Ant Design，应抽取共享主题桥接和表格空态策略，避免每页重复 token 配置。

## Common Pitfalls

- 不要把反馈管理写成写接口页面；当前模块没有审核、关闭、删除等业务规则。
- 不要绕过 `AdminChatApi` 手写 `response.json()`，否则会破坏统一认证失效与错误文案处理。
- 不要用浏览器原生弹窗承载交互；如未来新增确认或输入流程，必须使用项目内弹窗或 Ant Design 弹层。
- 不要只验证浅色主题；反馈列表和引用表格都是横向滚动密集区域，暗色滚动条和浮层 token 需要同步验证。
