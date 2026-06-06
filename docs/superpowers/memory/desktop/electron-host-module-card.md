---
type: module_card
title: electron-host-module-card
summary: 记录 CodingX Desktop 宿主承载用户前端和暴露本地能力桥接的稳定边界
tags:
  - desktop
  - electron
owned_paths:
  - frontend/desktop/src/main.ts
  - frontend/desktop/src/preload.ts
  - frontend/desktop/src/types.ts
related_docs:
  - docs/superpowers/memory/desktop/electron-host-contract.md
entrypoints:
  - frontend/desktop/src/main.ts
  - frontend/desktop/src/preload.ts
last_verified_commit: 34346584c7a8a7d2f626cabebe1ce2304afde6f7
status: active
---

# Electron Host Module Card

## Responsibilities

- `frontend/desktop/src/main.ts` 是 Electron 主进程入口，负责加载环境变量、创建无边框窗口、连接用户前端页面，并注册本地资源与窗口控制 IPC。
- `frontend/desktop/src/preload.ts` 通过 `contextBridge` 暴露 `window.codingxHost`，渲染层只能调用受控宿主 API，不能直接访问 Node 或 Electron 主进程。
- `frontend/desktop/src/types.ts` 定义宿主能力、窗口状态、本地目录项和菜单动作类型，是用户前端识别 Web / Desktop 能力差异的契约来源。

## Entry Points

- 开发态通过 `frontend/desktop/package.json` 的 `npm run start` 编译 TypeScript 后启动 Electron，并默认加载 `http://localhost:5002`。
- 打包态优先读取 `CODINGX_USER_URL`，未配置时加载 `process.resourcesPath/user-dist/index.html`。
- 渲染层通过 `window.codingxHost.getContext()` 获取当前宿主类型、可用执行目标、本地能力开关和绑定仓库信息。

## Invariants

- 主窗口必须保持 `contextIsolation: true`、`nodeIntegration: false`、`sandbox: true`，本地能力只能经 preload 白名单暴露。
- `hostState` 只保存当前进程内本地仓库绑定、工作空间标识和文件访问授权状态；当前实现不是跨重启持久化权限。
- `host:request-file-access` 只负责展示本机授权确认并返回布尔值，真实工具执行边界仍由后端本地工具运行时再次校验。
- `host:list-directory` 当前直接读取传入目录，后续若接入策略中心，必须先校验已授权目录和策略结果后再读取。

## Extension Points

- 本地权限策略可扩展在主进程 IPC 入口前置校验，并把策略摘要回写到 `HostContext.localResource`。
- 审计、Hook 或项目画像入口可通过新增受控 IPC 方法暴露给用户前端，但必须同步更新 `types.ts` 和 preload 类型。
- 管理端策略配置应通过后端接口持久化，桌面端只消费策略快照或执行本地确认，不在本机保存全局治理配置。

## Common Pitfalls

- 不要在渲染层直接引入 Node API 或绕过 `window.codingxHost`；这会破坏 Electron 安全边界。
- 不要把 `permissionGranted` 当作后端工具执行授权；它只是桌面端本地目录访问确认状态。
- 不要在主进程里把未校验的用户输入路径直接用于写入、删除或命令执行；当前目录列表能力也应在后续权限中心中收敛。
