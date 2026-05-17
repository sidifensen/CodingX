# 管理端技能页新增编辑能力验收标准

## 文档信息
- 对应设计：`docs/superpowers/specs/2026-05-17-111000-admin-skills-create-edit-design.md`
- 验收目标：管理端技能页可完成新增与编辑技能

## 功能验收项

### AC-1 新增技能弹窗可用
- Given 管理端进入 `/skills`
- When 点击“创建新技能”
- Then 页面内出现“新增技能”弹窗
- And 填写技能编码与技能名称后点击“创建技能”会调用创建接口
- And 提交成功后弹窗关闭并刷新技能列表

### AC-2 编辑技能弹窗可用
- Given 技能列表已有技能卡片
- When 点击卡片“编辑”按钮
- Then 页面内出现“编辑技能”弹窗
- And 技能编码字段为只读
- And 修改技能名称后点击“保存修改”会调用更新接口
- And 提交成功后弹窗关闭并刷新技能列表

### AC-3 错误与交互约束
- 不使用浏览器原生 `alert/confirm/prompt`
- 表单最小必填校验：技能编码、技能名称
- 后端报错优先展示错误信息

## 验证清单

1. `cd frontend/admin && npm run test:run -- src/pages/Skills.test.tsx`
2. `cd frontend/admin && npm run test:run`
3. `cd frontend/admin && npm run build`
4. CDP 验证路径：`/skills` 页面点击新增与编辑，截图保存 `logs/skills-*.png`
