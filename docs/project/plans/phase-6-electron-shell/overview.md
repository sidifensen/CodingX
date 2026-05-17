# Phase 6：Electron 宿主与本地资源

更新时间：2026-05-17

## 一、概述

这一期聚焦 Electron 宿主与本地资源入口，不接入真实本地执行链路。目标是让现有 `frontend/user` 在桌面壳中运行，并具备本地仓库选择、权限确认与路径绑定能力。

## 二、目标

- 让同一套前端运行在 Electron 中
- 增加宿主识别与 capability 基础层
- 支持本地仓库选择与路径绑定
- 支持本地文件访问权限确认
- 暴露最小本地资源能力接口

## 三、已完成事项

- 搭建 Electron 主进程与窗口壳（`desktop-shell/src/main.ts`）
- 搭建 preload 安全桥接层（`desktop-shell/src/preload.ts`）
- 新增前端宿主能力层（`frontend/user/src/host/*`）
- 接入侧边栏宿主能力卡片与本地仓库目录入口（`frontend/user/src/components/Sidebar.tsx`）
- 完成目录选择、权限确认、路径绑定调用链路
- 暴露本地目录读取接口（`host:list-directory`）

## 四、明确不做

- 不实现本地命令执行
- 不实现本地任务执行器调度
- 不实现本地 MCP 实际调用桥

上述能力在 `Phase 7` 完成。
