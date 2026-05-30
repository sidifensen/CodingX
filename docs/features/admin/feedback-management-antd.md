# 管理端反馈管理 Ant Design 改造

## 功能用途

管理端反馈管理用于查看用户对助手消息的点赞/点踩反馈，并进入详情页排查关联会话、消息正文和引用来源。本次实现将反馈管理三页迁移为 Ant Design 组件，统一筛选、表格、详情、加载、空态和错误提示体验。

## 使用入口

- 反馈列表：`/feedbacks`
- 反馈详情：`/feedbacks/:feedbackId`
- 引用来源：`/feedbacks/:feedbackId/references`

## 核心流程

1. 列表页通过 `AdminChatApi.listFeedbacks` 按页加载反馈，并支持关键词和投票方向筛选。
2. 管理员点击列表中的“查看”进入反馈详情页，页面通过 `AdminChatApi.getFeedbackDetail` 展示反馈和关联消息。
3. 管理员在详情页点击“查看引用来源”，引用页通过 `AdminChatApi.listFeedbackReferences` 展示消息来源证据。
4. 三页统一使用 Ant Design 的表格、表单、按钮、卡片、详情、标签、加载和错误提示组件。
5. `AdminAntdProvider` 根据根节点 `.dark` 类切换 Ant Design 亮色/暗色算法，并映射现有主题变量。

## 关键文件

- `frontend/admin/src/components/AdminAntdProvider.tsx`：Ant Design 主题桥接
- `frontend/admin/src/pages/FeedbackPage.tsx`：反馈列表、筛选、分页和详情入口
- `frontend/admin/src/pages/FeedbackDetailPage.tsx`：反馈详情和关联消息内容
- `frontend/admin/src/pages/FeedbackReferencePage.tsx`：引用来源表格与外链
- `frontend/admin/src/api/adminChatApi.ts`：反馈列表、详情和引用来源 API 封装
- `frontend/admin/tests/pages/Feedback*.test.tsx`：反馈管理页面回归测试

## 关键数据结构

- `AdminChatMessageFeedback`：反馈列表项，包含反馈 ID、消息 ID、会话 ID、投票、原因、评论和时间。
- `AdminChatMessageFeedbackDetail`：反馈详情，在列表项基础上增加会话标题、消息角色和消息正文。
- `AdminChatMessageReference`：引用来源项，包含标题、站点、来源类型、摘要、链接和排序号。

## 测试与验证方式

- 定向测试：`cd frontend/admin && npm run test:run -- tests/pages/FeedbackPage.test.tsx tests/pages/FeedbackDetailPage.test.tsx tests/pages/FeedbackReferencePage.test.tsx`
- 管理端全量测试：`cd frontend/admin && npm run test:run`
- 管理端构建：`cd frontend/admin && npm run build`
- 浏览器验证：通过 CDP 打开 `http://localhost:5003/feedbacks`，验证亮色/暗色下 Ant Design 表格、筛选控件和滚动容器可读，并保存证据到 `logs/`。
