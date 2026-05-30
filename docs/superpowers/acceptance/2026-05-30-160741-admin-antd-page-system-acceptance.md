# 管理端 Ant Design 页面体系验收标准

## 必须满足

- 反馈、MCP、用户、会话、专家、工具、工作空间、Trace、技能、意图树、关键词映射、系统配置、通知中心页面使用 Ant Design 控件承载主要交互。
- 通用表格组件统一输出 Ant Design Table，禁用竖向分隔线，表格单元格有稳定内间距。
- 所有表格操作列按钮必须包含图标和可访问名称。
- 页面顶部新增、刷新、上传、查询等动作按钮必须包含 Ant Design 图标。
- 意图树层级展示必须使用 Ant Design Tree，级联栏和节点详情操作必须使用 Ant Design Button/Tag。
- 技能上传不得使用浏览器原生弹窗，上传、覆盖确认、删除确认均在页面内完成。
- 删除旧 `Pagination` 与 `DataTableCard` 后，生产代码不得引用它们。

## 验证命令

- `npm run test:run`
- `npm run build`
- 使用浏览器访问 `http://localhost:5003` 验证表格、弹窗、树与暗色模式视觉表现。
