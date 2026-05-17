# 阶段决策记录

更新时间：2026-05-17

## 决策 001

- 标题：Phase 6 采用独立 `desktop-shell` 模块承载 Electron 宿主
- 日期：2026-05-17
- 背景：需要快速落地桌面宿主能力，同时避免污染现有 `frontend/user` 工程脚本和构建流程
- 结论：新增 `desktop-shell` 目录，Electron 主进程与 preload 独立维护，前端仅消费桥接接口
- 影响范围：`desktop-shell/*`、`frontend/user/src/host/*`

## 决策 002

- 标题：前端宿主识别采用“注入优先 + Web fallback”
- 日期：2026-05-17
- 背景：同一套前端需要在 Web 与 Electron 下运行，且不能依赖运行时硬编码判断
- 结论：统一通过 `resolveHostBridge` 返回桥接对象；存在 `window.codingxHost` 时走桌面能力，否则回退到 cloud-only 能力
- 影响范围：`frontend/user/src/host/bridge.ts`、`frontend/user/src/host/useHostContext.ts`

## 决策 003

- 标题：本地资源入口放在 Sidebar 宿主能力卡片中
- 日期：2026-05-17
- 背景：用户需要感知当前宿主能力，并在统一导航区执行本地仓库绑定
- 结论：在侧边栏新增“宿主能力”卡片，展示宿主类型、执行目标和本地仓库绑定入口
- 影响范围：`frontend/user/src/components/Sidebar.tsx`
