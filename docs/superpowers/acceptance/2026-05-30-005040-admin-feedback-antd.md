# Acceptance Criteria: 管理端反馈管理 Ant Design 改造

**Spec:** `docs/superpowers/specs/2026-05-30-005040-admin-feedback-antd-design.md`
**Date:** 2026-05-30
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 管理端应用必须接入 Ant Design 主组件库并通过 `ConfigProvider` 提供主题 token | Logic | 安装依赖后运行管理端构建 | `npm run build` 成功，页面根部存在 Ant Design 配置组件且无缺失模块错误 |
| AC-002 | 反馈列表页筛选区必须使用 Ant Design 表单控件并保持投票筛选参数语义 | Logic | `AdminChatApi.listFeedbacks` 被 mock，渲染 `/feedbacks` 页面 | 选择“仅点踩”并点击筛选后，最后一次调用参数包含 `vote: -1` |
| AC-003 | 反馈列表页必须使用 Ant Design 表格展示反馈记录和详情入口 | Logic | mock 返回一条反馈记录 | 页面显示反馈 ID、原因、评论、分页总数，且详情链接 href 为 `/feedbacks/{id}` |
| AC-004 | 反馈列表页加载中必须展示 Ant Design 表格加载状态 | Logic | `listFeedbacks` 返回未决 Promise | 页面存在表格加载语义或 loading 类，接口返回后展示真实行 |
| AC-005 | 反馈详情页必须使用 Ant Design 详情组件展示反馈和关联消息 | Logic | `getFeedbackDetail` mock 返回详情 | 页面显示标题、会话标题、消息正文、投票文案和“查看引用来源”链接 |
| AC-006 | 反馈引用来源页必须使用 Ant Design 表格展示来源并保留安全外链属性 | Logic | `listFeedbackReferences` mock 返回一条来源 | 页面显示来源标题、站点、摘要，外链 href 正确且带新窗口打开属性 |
| AC-007 | 三个反馈页面必须继续展示后端错误文案 | Logic | API mock reject `Error('后端错误')` | 页面出现 Ant Design 错误提示并显示“后端错误” |
| AC-008 | 反馈管理 Ant Design 控件在亮色和暗色主题下必须可读 | UI interaction | 管理端服务启动并可访问 `/feedbacks` | CDP 截图或计算样式显示关键容器背景非透明，文本和边框颜色与当前主题有可辨识对比 |
