---
type: contract
title: electron-host-contract
summary: 约束桌面宿主 IPC、HostContext 和用户前端之间的本地能力消费协议
tags:
  - desktop
  - electron
  - host-context
owned_paths:
  - frontend/desktop/src/main.ts
  - frontend/desktop/src/preload.ts
  - frontend/desktop/src/types.ts
  - frontend/user/src/host
related_docs:
  - docs/superpowers/memory/desktop/electron-host-module-card.md
entrypoints:
  - frontend/desktop/src/preload.ts
  - frontend/user/src/host/bridge.ts
last_verified_commit: 34346584c7a8a7d2f626cabebe1ce2304afde6f7
status: active
---

# Electron Host Contract

## Scope

本契约覆盖 Electron 主进程、preload 桥接和用户前端宿主识别之间的交互。它不覆盖后端本地工具执行器的路径边界、命令安全策略或数据库权限配置。

## Producers And Consumers

- Producer：`frontend/desktop/src/main.ts` 注册 `host:*` IPC handler，并构造 `HostContext`。
- Bridge：`frontend/desktop/src/preload.ts` 把 IPC handler 包装成 `window.codingxHost` 方法。
- Consumer：`frontend/user/src/host` 读取 `window.codingxHost`，把宿主类型、窗口控制和本地仓库绑定能力提供给用户前端页面。

## Interface Rules

- `HostContext.hostType` 在 Electron 中固定为 `desktop`；Web 端应通过缺省 bridge 返回 `web`。
- `HostContext.executionTargets` 当前在桌面端返回 `['cloud', 'local']`，表示同一用户前端可发起云端和本地运行请求。
- `HostContext.capabilities` 是渲染层展示入口的能力开关，不等于最终执行授权。
- `HostContext.localResource.boundRepositoryPath` 为空时，用户前端不能假设已有本地仓库；绑定后必须把路径和可选工作空间上下文传回后端聊天请求。
- `HostContext.localResource.permissionGranted` 表示最近一次本地文件访问确认结果，不能跨目录、跨会话或跨后端工具调用复用为永久授权。

## IPC Rules

- `host:pick-repository-directory` 只返回用户选择的本地目录路径，不自动授予工具执行权限。
- `host:request-file-access` 必须展示本机确认弹窗，并只返回本次确认结果。
- `host:bind-repository-path` 只更新桌面宿主上下文，后端仍需通过 `ChatWorkspaceBindingService` 落库或解析工作空间。
- `host:list-directory` 返回目录名、完整路径和 `file/directory` 类型；后续扩展时不得把文件内容直接加入该接口返回。
- 窗口控制与菜单动作必须限定在 `DesktopMenuAction` 枚举内，未知动作应无副作用返回。

## Invariants

- preload 暴露的方法名、参数和返回值变更时，必须同步更新 `frontend/desktop/src/types.ts`、用户前端 host 类型和相关测试。
- 本地路径进入后端工具运行时后，必须再次按后端工作目录边界和工具白名单校验。
- 桌面端授权状态只能增强用户体验，不能削弱后端安全边界。

## Compatibility Notes

- 现有用户前端会同时运行在 Web 和 Electron；新增桌面能力时必须为 Web bridge 提供安全降级。
- 生产打包态可以不配置 `CODINGX_USER_URL`，因此用户前端不能依赖开发服务器特有行为。
