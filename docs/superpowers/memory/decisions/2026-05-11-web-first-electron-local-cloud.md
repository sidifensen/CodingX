---
type: decision
title: web-first-electron-local-cloud
summary: 平台采用一套 React 前端，同时运行在 Web 与 Electron 桌面宿主中，并通过能力层支持云端与本地双执行环境。
tags:
  - product
  - frontend
  - desktop
  - runtime
owned_paths:
  - CodingX/docs/superpowers/specs/2026-05-11-super-agent-platform-design.md
related_docs:
  - CodingX/docs/superpowers/memory/index.md
  - CodingX/docs/superpowers/specs/2026-05-11-super-agent-platform-design.md
status: accepted
---

# 背景

产品目标已经从“本地开发 Agent”扩展为“通用任务型 CodingX 平台”。当前参考产品优先级已经明确：WorkBuddy 为最高优先级参考，其次是 Codex、Trae Solo 与千问。其中，Trae Solo 不只是后台任务体验参考，也作为云端环境与任务级 workspace 模型参考。

用户同时希望具备两类能力：

- 类似 Web 端智能体平台的任务入口、工作台、多任务后台运行
- 类似桌面端产品的本地文件、命令、浏览器、软件和内网访问能力
- 类似 WorkBuddy 的技能、MCP、专家与团队式组织能力
- 平台级的内置工具管理能力

同时，平台除了用户端 Web 与桌面端，还需要一个只使用 Web 的管理端，用于管理智能体、团队、技能、内置工具、MCP 与平台治理能力。

同时，用户明确希望用户端桌面和用户端网页尽量共用同一套页面，不做两套割裂前端。

# 决策

平台采用以下产品形态：

- Web-first
- 一套 React 前端
- Electron 作为桌面端宿主
- 云端执行与本地执行并存
- 保留代码开发 / 日常办公两种模式切换
- 显式支持技能、MCP、预设智能体与团队模式
- 显式支持内置工具系统
- 单独提供管理端 Web

两种宿主的职责划分如下：

- Web 宿主提供云端执行能力
- Electron 宿主在保留云端执行能力的基础上，额外提供本地执行能力

本地执行不能作为“桌面端专属页面逻辑”散落在前端中，而要作为一种正式执行目标，通过能力层向前端暴露。

# 备选方案

- 开发者终端工具优先
  - 适合开发者，但不适合作为广义任务平台主入口
- Desktop-only
  - 本地能力强，但分发、协作和平台化能力较弱
- Web 与桌面分别维护独立前端
  - 短期灵活，长期会产生严重产品漂移和开发成本

# 取舍

- Electron 相比 Tauri 更重，但本地集成与 JavaScript 生态更成熟
- 共享前端可以统一产品体验，但前提是必须建立清晰的能力抽象层
- Web-first 能提高平台普适性，但同时要求 Runtime 与宿主能力彻底解耦

# 重新评估信号

- 桌面端出现 Electron 无法满足的性能或安全约束
- 产品重新收缩为以开发者为绝对主场的工具
- 共享前端因为能力分支过多而明显失控
