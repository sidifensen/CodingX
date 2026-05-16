# 技能管理与斜杠技能选择对接增值接口验收标准

## 文档信息
- 对应设计：`docs/superpowers/specs/2026-05-17-100500-skill-integration-and-slash-command-design.md`
- 验收目标：管理端技能页、用户端技能库、聊天斜杠技能选择、后端 stream 参数兼容均完成真实接口对接

## 功能验收项

### AC-1 管理端技能页使用真实接口
- Given 管理端登录成功并进入 `/skills`
- When 页面初始化
- Then 必须调用 `/api/admin/chat/skills`（通过 `AdminChatApi.listSkills`）
- And 页面卡片内容来自接口返回（`skillCode/displayName/category/sourceType/enabled/sortNo`）
- And 不再依赖 `mockSkills`

### AC-2 用户端技能库使用真实接口
- Given 用户端登录成功并进入技能库视图
- When 页面初始化
- Then 必须调用 `/api/chat/skills`
- And “已安装”数量与真实接口结果一致
- And 技能列表名称、描述、分类来自接口返回

### AC-3 聊天输入 `/` 可见技能候选
- Given 聊天页可用技能列表不为空
- When 输入框输入 `/` 或 `/关键字`
- Then 命令面板必须出现技能候选项
- And 候选可按 `displayName/skillCode` 过滤
- And 通过回车或点击可选中技能
- And 选中结果写入 `selectedSkillCodes`

### AC-4 聊天输入 `/` 保持 MCP 兼容
- Given 聊天页 MCP 列表可用
- When 输入框输入 `/`
- Then 命令面板同时可展示 MCP 候选
- And 选中 MCP 仍写入 `selectedMcpCodes`
- And 原有上下键高亮与回车选择行为不回退

### AC-5 后端 stream 显式技能参数优先
- Given 请求 `/api/chat/stream` 时显式传入 `skillCodes`
- When 控制器解析参数
- Then 派发命令中的绑定列表必须优先使用显式 `skillCodes`
- And 未传 `skillCodes` 时保持旧逻辑回退（使用默认启用项）

## 非功能验收项

### NFR-1 错误处理
- 前端 API 解析必须继续走统一 `ApiResponseParser`
- 页面错误提示优先使用后端 `ApiResponse.message`

### NFR-2 代码注释
- 所有新增或修改文件必须补充必要注释，说明业务意图与关键约束

### NFR-3 回归稳定性
- 前端相关测试与构建通过
- 后端相关测试与编译通过

## 验证清单

1. 前端管理端：`cd frontend/admin && npm run test:run -- Skills.test.tsx`
2. 前端用户端：`cd frontend/user && npm run test:run -- ChatView.test.tsx SkillsView.test.tsx chatApi.test.ts`
3. 前端构建：`cd frontend/admin && npm run build`、`cd frontend/user && npm run build`
4. 后端测试：`cd backend && mvn test -Dtest=ChatStreamControllerTest,ChatSkillControllerTest,AdminChatSkillControllerTest`
5. 后端编译：`cd backend && mvn compile`
