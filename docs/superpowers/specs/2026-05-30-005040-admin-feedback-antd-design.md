# 管理端反馈管理 Ant Design 改造设计

## 1. 背景

管理端反馈管理已经具备 `/feedbacks` 列表、`/feedbacks/:feedbackId` 详情和 `/feedbacks/:feedbackId/references` 引用来源三页，接口也已集中在 `AdminChatApi`。当前页面仍使用原生 `input/select/button/table` 与 Tailwind 手写表格样式，和用户要求的 Ant Design 组件化管理端体验不一致。

本次改造只处理管理端反馈管理 UI。后端接口、路由路径、分页字段和反馈业务语义保持不变。

## 2. 目标

1. 为管理端新增 `antd` 主组件库依赖。
2. 反馈列表、详情、引用来源三页全部使用 Ant Design 组件承载主要 UI。
3. 在管理端根部接入 Ant Design `ConfigProvider`，用现有 CSS 主题变量映射亮色和暗色 token。
4. 保留现有 API、路由、筛选、分页、错误、空态和加载行为。
5. 补充测试和浏览器证据，确保 Ant Design 迁移后亮色和暗色主题可读。

## 3. 非目标

1. 不修改后端接口和数据库结构。
2. 不新增反馈处理状态、删除、导出或统计能力。
3. 不改造管理端其他页面的 UI 组件库。
4. 不引入浏览器原生弹窗。

## 4. 设计方案

### 4.1 Ant Design 接入

新增 `antd` 依赖后，在管理端应用根部提供主题桥接组件：

- 监听 `document.documentElement` 的 `dark` class 变化。
- 使用 `ConfigProvider` 的 `theme.algorithm` 在亮色和暗色算法间切换。
- 通过 `token` 映射项目现有 CSS 变量：背景、文本、边框、主色、错误色、圆角。
- 设置 `componentSize="middle"`，让表单、按钮和表格密度适合管理端排查场景。

该桥接只负责 Ant Design token，不改变现有 Tailwind 主题与 `Layout` 主题切换逻辑。

### 4.2 反馈列表页

`FeedbackPage` 改为 Ant Design 组件：

- 顶部使用 `Typography` 展示标题与说明。
- 筛选区使用 `Form`、`Input`、`Select`、`Button`、`Space`。
- 错误态使用 `Alert`。
- 列表使用 `Table<AdminChatMessageFeedback>`，列包含反馈 ID、消息 ID、会话 ID、投票、原因、评论、创建时间、操作。
- 投票状态使用 `Tag`：点赞为成功语义，点踩为错误语义。
- 分页使用 Ant Design `Table.pagination`，保持每页 10 条。
- 加载、空态由 `Table` 的 `loading` 与 `locale.emptyText` 承载。

筛选行为保持原规则：关键词 trim 后传入；全部投票不传 `vote`；点击刷新复用当前页和当前筛选条件。

### 4.3 反馈详情页

`FeedbackDetailPage` 改为 Ant Design 组件：

- 顶部使用 `Typography`、`Button`、`Space` 承载标题、返回列表、查看引用来源。
- 加载态使用 `Skeleton` 或 `Spin`。
- 错误态使用 `Alert`。
- 详情字段使用 `Descriptions`，展示会话 ID、会话标题、消息 ID、消息角色、反馈投票、反馈原因、反馈评论。
- 关联消息正文使用 `Card`、`Typography.Paragraph` 展示，保留换行。

历史空字段统一显示 `-`，避免展示 `undefined` 或 `null`。

### 4.4 反馈引用来源页

`FeedbackReferencePage` 改为 Ant Design 组件：

- 顶部使用 `Typography`、`Button`、`Space`。
- 错误态使用 `Alert`。
- 引用来源使用 `Table<AdminChatMessageReference>`，列包含序号、来源标题、站点、来源类型、摘要、链接。
- 外链使用 Ant Design `Button` + `Typography.Link` 或 `a`，保持 `target="_blank"` 和 `rel="noreferrer"`。
- 空态使用 `Empty`。

### 4.5 测试策略

先修改现有三页测试，让它们断言 Ant Design 组件语义：

- 列表页筛选仍能向 `AdminChatApi.listFeedbacks` 传入 `vote=-1`。
- 列表页渲染数据行、详情链接和分页文本。
- 列表页加载时出现 Ant Design 表格加载态。
- 详情页渲染 `Descriptions` 字段和引用来源链接。
- 引用页渲染表格行、外链、返回详情链接。

测试先运行失败，再实现页面迁移，最后转绿。

## 5. 风险与控制

1. 风险：Ant Design 暗色主题未随现有主题切换更新。
   - 控制：主题桥接监听根节点 class 变化，并用浏览器验证亮色和暗色计算样式。
2. 风险：Ant Design Table DOM 与旧测试定位方式不同。
   - 控制：测试改为用户可见文本、role、链接 href 和 API 调用断言，不依赖内部类名。
3. 风险：新增依赖影响构建体积或 lock 文件。
   - 控制：只安装 `antd`，不额外引入图标库；使用现有 Material Symbols 文本图标或按钮文本即可。

## 6. 验证

1. 定向测试：`cd frontend/admin && npm run test:run -- tests/pages/FeedbackPage.test.tsx tests/pages/FeedbackDetailPage.test.tsx tests/pages/FeedbackReferencePage.test.tsx`
2. 管理端全量测试：`cd frontend/admin && npm run test:run`
3. 管理端构建：`cd frontend/admin && npm run build`
4. 浏览器验证：通过 CDP 打开 `http://localhost:5003/feedbacks`，验证亮色和暗色下列表、筛选、表格、详情、引用来源可读，并保存截图到 `logs/`。
