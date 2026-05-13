# AGENTS Instructions

更新时间：2026-05-12

## Scope

本文件适用于 `D:\code\CodingX` 整个项目目录。

## Hard Rules

1. 阶段文档必须按阶段目录归档。  
每个阶段产物必须放在 `docs/project/plans/phase-*/` 对应目录中，不得放在其他目录。

2. `docs/project/plans` 根目录禁止新增阶段产物文件。  
根目录仅允许：
- 路线图总览文档
- 阶段文档规范文档

3. 每个阶段目录必须包含以下标准文件：
- `overview.md`
- `tasks.md`
- `acceptance.md`
- `progress-log.md`
- `decisions.md`

4. 新增阶段专题文档时，必须放在对应阶段目录内。  
命名建议使用：`topic-<name>.md`。

5. 更新阶段内容时，优先更新对应阶段目录文件，不要跨阶段混写在同一文件。

6. 前端技术栈固定为：
- `React`
- `Vite+`（https://viteplus.dev/）
- `Tailwind CSS`

7. 后端技术栈固定为：
- `Spring Boot`
- `Sa-Token`
- `Lombok`
- `Hutool`
- `OkHttp`

8. 分期顺序固定为：
- `Phase 1-3: Web`
- `Phase 4-5: Skill / MCP`
- `Phase 6-7: Electron`
- `Phase 8-9: 云端环境`

## Reference

详细规范见：

- `docs/project/plans/phase-docs-spec.md`
