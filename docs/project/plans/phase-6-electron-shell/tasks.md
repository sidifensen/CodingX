# 阶段任务清单

更新时间：2026-05-17

## 任务列表

- [x] `done` 搭建 `desktop-shell` 模块骨架（owner: self）
  - 交付结果：新增 Electron 主进程、preload 与 IPC 最小能力接口
- [x] `done` 新增宿主识别与 capability 基础层（owner: self）
  - 交付结果：新增 `host` 类型、桥接与 Hook，支持 Web fallback / Desktop 注入
- [x] `done` 接入侧边栏宿主能力展示与本地仓库入口（owner: self）
  - 交付结果：Sidebar 展示宿主类型、执行目标、本地目录选择与绑定路径
- [x] `done` 完成本地目录选择、权限确认、路径绑定链路（owner: self）
  - 交付结果：实现 `pick -> request access -> bind` 顺序调用
- [x] `done` 补齐前端与桌面壳验证（owner: self）
  - 交付结果：`frontend/user` build+test 通过，`desktop-shell` build 通过

## 任务状态说明

- `todo`：未开始
- `doing`：进行中
- `done`：已完成
