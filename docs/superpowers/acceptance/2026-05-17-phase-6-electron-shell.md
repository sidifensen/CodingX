# Acceptance Criteria: Phase 6 Electron 宿主与本地资源

**Spec:** `docs/superpowers/specs/2026-05-17-210254-phase-6-electron-shell-design.md`
**Date:** 2026-05-17
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 在无桌面注入对象时，前端宿主桥接返回 Web fallback 上下文 | Logic | 浏览器环境中 `window.codingxHost` 未定义 | `resolveHostBridge().getContext()` 返回 `hostType=web`、`executionTargets=[cloud]`、`localFolderPicker=false` |
| AC-002 | 在存在桌面注入对象时，前端宿主桥接优先使用桌面桥接对象 | Logic | `window.codingxHost` 注入实现了桥接接口 | `resolveHostBridge()` 返回注入对象本身，且 `getContext()` 返回 `hostType=desktop` |
| AC-003 | 桌面宿主能力上下文下，侧边栏展示本地仓库入口并可触发选择操作 | UI interaction | Sidebar 接收 `hostType=desktop` 且 `localFolderPicker=true` | 页面出现“选择本地仓库目录”按钮，点击后触发 `onPickRepositoryDirectory` 一次 |
| AC-004 | Web 宿主能力上下文下，侧边栏不展示本地仓库入口 | UI interaction | Sidebar 接收 `hostType=web` 且 `localFolderPicker=false` | 页面不出现“选择本地仓库目录”按钮 |
| AC-005 | Electron preload 必须只通过 `contextBridge` 暴露白名单 API，不允许直接暴露 Node 全量能力 | Logic | `desktop-shell/src/preload.ts` 可编译 | `window.codingxHost` 仅包含 `getContext/pickRepositoryDirectory/bindRepositoryPath/requestFileAccess/listDirectory` 五个方法 |
| AC-006 | 选择本地目录后必须先执行权限确认，再执行路径绑定 | Logic | 桌面桥接可用，用户触发目录选择 | 调用顺序为：`pickRepositoryDirectory -> requestFileAccess -> bindRepositoryPath`；任一环节失败不进入下一步 |
| AC-007 | 前端改动后构建与测试通过 | Logic | Node 依赖安装完成 | `frontend/user` 执行 `npm run build` 和 `npm run test:run` 成功退出 |
| AC-008 | desktop-shell 模块可完成类型检查与构建 | Logic | `desktop-shell` 依赖安装完成 | `desktop-shell` 执行 `npm run build` 成功退出 |
