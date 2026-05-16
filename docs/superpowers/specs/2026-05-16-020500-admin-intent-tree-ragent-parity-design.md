# 管理端意图树 ragent 对齐设计

**日期:** 2026-05-16
**状态:** Approved

## 背景

当前 CodingX 管理端意图树页面只提供简化表单和表格，无法达到参考项目 `D:\code\ragent` 的树形配置台效果。用户已选择后端、前端和数据库结构一起向 ragent 对齐，同时要求页面保持 CodingX 当前管理端风格和暗色模式能力。

## 目标

- 管理端页面改为 ragent 风格的左侧树结构、右侧节点详情、顶部操作和弹窗编辑。
- 后端节点契约补齐 ragent 关键字段：`level`、`kind`、`examples`、`collectionName`、`topK`、`promptSnippet`、`sortOrder`。
- 保留 CodingX 运行时现有 `intentType` 语义，避免破坏聊天分流。
- 数据库迁移、基线结构和初始化数据保持一致，并补齐中文注释。

## 后端设计

`ChatIntentNode` 在保留现有字段的基础上新增扩展配置字段。`kind` 与 `intentType` 双写：管理端发送 `kind` 时，后端派生 `intentType`；旧接口只发送 `intentType` 时，后端派生 `kind`。映射规则为 `0 -> kb`、`1 -> system`、`2 -> mcp`。

`AdminChatIntentService` 负责：

- 查询平铺列表，兼容现有 Dashboard 统计和旧页面调用。
- 查询完整树，按 `parentCode` 递归组装 `children`。
- 创建节点时校验 `intentCode` 唯一，补默认 `level=0`、`kind=0`、`enabled=1`、`sortOrder=sortNo=0`。
- 更新节点时禁止修改 `intentCode`，支持清空父节点和可选字段。
- 删除节点使用逻辑删除；若目标节点存在未删除子节点，返回中文业务错误，要求先处理子节点。

## 前端设计

`frontend/admin/src/pages/IntentTreePage.tsx` 改为单页配置台：

- 顶部展示标题、说明、刷新和新建根节点按钮。
- 左侧卡片展示树结构，支持展开收起、选中高亮和节点层级/类型徽标。
- 右侧卡片展示当前节点详情、父节点、排序、Collection、TopK、描述和示例问题。
- 弹窗表单支持创建与编辑，包含基础信息、描述与示例、Prompt 配置、高级设置四组字段。
- 删除确认使用自定义浮层，不使用浏览器原生弹窗。
- 所有样式使用当前管理端 Tailwind 设计令牌，浅色和暗色下关键容器背景均非透明。

## 数据库设计

新增迁移脚本扩展 `chat_intent_node`：

- `kb_id VARCHAR(128)`
- `level SMALLINT NOT NULL DEFAULT 0`
- `examples TEXT`
- `collection_name VARCHAR(128)`
- `top_k INTEGER`
- `kind SMALLINT NOT NULL DEFAULT 0`
- `prompt_snippet TEXT`
- `sort_order INTEGER NOT NULL DEFAULT 0`

同时更新 `schema.sql`、`init.sql`，为新增列补中文短语注释。历史数据按 `intentType` 和 `parentCode` 回填 `kind`、`level`、`sortOrder`。

## 测试策略

- 后端先写服务单元测试，锁定树形组装、重复编码校验、`kind/intentType` 映射和删除保护。
- 后端写控制器测试，锁定 `/tree`、`POST`、`PUT`、`DELETE` 的 `ApiResponse` 契约。
- 前端先写页面测试，使用 mock API 验证树形渲染、节点详情、打开弹窗和提交创建。
- 最后执行后端 `mvn compile`、`mvn test`，前端 `npm run build`、`npm run test:run`，并用 CDP 验证页面视觉和暗色模式关键背景。

## 非目标

- 本次不新增独立知识库管理模块。
- 本次不实现 ragent 的批量启用、批量停用和批量删除列表页。
- 本次不改变聊天运行时对 `chat_intent_example` 表的读取策略。
