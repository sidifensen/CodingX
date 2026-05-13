# CodingX 阶段文档规范

更新时间：2026-05-12

## 一、目的

统一阶段文档管理方式，避免文档散落。

强制规则：

- 每个阶段产生的文档，必须放在该阶段自己的文件夹里
- 禁止把阶段产物放到阶段目录外
- 阶段之间不共享同一个产物文件

## 二、目录规则

阶段目录统一在：

- `docs/project/plans/phase-*/`

每个阶段目录至少包含：

- `overview.md`：阶段概述、目标、要做的事情
- `tasks.md`：阶段任务清单
- `acceptance.md`：阶段验收标准
- `progress-log.md`：阶段执行日志
- `decisions.md`：阶段内决策记录

可选文件：

- `risks.md`：风险与阻塞
- `api.md`：接口清单
- `ui.md`：页面清单与交互规范
- `changelog.md`：阶段变更记录

## 三、命名规则

固定文件名优先，不用每天改文件名：

- `overview.md`
- `tasks.md`
- `acceptance.md`
- `progress-log.md`
- `decisions.md`

如果阶段内需要新增专题文档，命名建议：

- `topic-<name>.md`

示例：

- `topic-mcp-permission-model.md`

## 四、写作规则

### 1) overview.md

必须包含：

- 阶段概述
- 阶段目标
- 要做的事情
- 明确不做

### 2) tasks.md

必须包含：

- 任务分组
- 每个任务的交付结果
- 任务状态（todo / doing / done）
- 负责人（如果是单人可写 `owner: self`）

### 3) acceptance.md

必须包含：

- 验收项 ID
- 验收描述
- 验收方式
- 通过标准

### 4) progress-log.md

必须包含：

- 日期
- 当天完成内容
- 遇到问题
- 下一步计划

### 5) decisions.md

必须包含：

- 决策标题
- 决策日期
- 背景
- 结论
- 影响范围

## 五、落地规则

从现在开始，新增阶段产物按下面执行：

1. 先定位阶段目录
2. 再创建或更新该目录内文件
3. 不在 `docs/project/plans` 根目录新增阶段产物
4. 只允许 `roadmap-overview` 与 `phase-docs-spec` 放在根目录

## 六、当前阶段目录列表

- `docs/project/plans/phase-1-web-shell/`
- `docs/project/plans/phase-2-web-task-core/`
- `docs/project/plans/phase-3-web-workbench/`
- `docs/project/plans/phase-4-skill-productization/`
- `docs/project/plans/phase-5-mcp-productization/`
- `docs/project/plans/phase-6-electron-shell/`
- `docs/project/plans/phase-7-electron-local-runtime/`
- `docs/project/plans/phase-8-cloud-runtime-foundation/`
- `docs/project/plans/phase-9-cloud-unification/`
