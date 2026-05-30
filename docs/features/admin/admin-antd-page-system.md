# 管理端 Ant Design 页面体系

## 功能用途

统一管理端除工作台外的页面组件体系，优先使用 Ant Design 的表格、按钮、表单、弹窗、树、上传、提示和空状态组件，降低各页面重复维护自绘控件的成本。

## 使用入口

- 管理端各业务页面：用户、会话、工作空间、技能、专家、工具、MCP、Trace、反馈、意图树、关键词映射、系统配置、通知中心。
- 工作台页面保持原有实现，不纳入本次 Ant Design 改造范围。

## 核心流程

1. 页面入口继续负责数据加载和业务状态维护。
2. 列表型页面统一通过 `AdminDataTable` 渲染 Ant Design Table、中文分页摘要和无竖线表格。
3. 表格操作列统一通过 `AdminTableActions` 渲染带图标的 Ant Design Button。
4. 表单、确认、上传和预览交互优先使用 Ant Design Modal、Form、Input、Select、Switch、Upload、Alert。
5. 树状和分栏场景使用 Ant Design Tree、Button、Tag、Empty 等组件承载层级选择与状态展示。

## 关键文件

- `frontend/admin/src/components/AdminDataTable.tsx`：统一表格和操作列按钮组件。
- `frontend/admin/src/components/AdminAntdProvider.tsx`：管理端 Ant Design 主题覆盖、暗色模式与表格样式。
- `frontend/admin/src/pages/IntentTreePage.tsx`：意图树页面的 Tree、Modal、Form 改造。
- `frontend/admin/src/pages/Skills.tsx`：技能管理列表、资源预览、编辑、上传、删除交互改造。
- `frontend/admin/src/components/ToolIntentTreePanel.tsx`：工具意图树分栏与详情操作改造。

## Ant Design 组件使用

本次可复用的组件包括：Table、Button、Modal、Form、Input、Select、Switch、Tree、Tag、Alert、Empty、Spin、Tooltip、Space、Upload、Dropdown、Avatar、Card、Descriptions。

## 验证方式

- 管理端单页测试覆盖分页、表格操作、意图树、技能上传和 Trace 列表。
- 管理端构建用于验证 TypeScript 类型、组件导入和生产包编译。
- 浏览器验证覆盖暗色/亮色下表格、弹窗、树和上传控件可读性。
