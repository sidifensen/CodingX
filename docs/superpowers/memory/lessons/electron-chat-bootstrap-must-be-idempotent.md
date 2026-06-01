---
type: lesson
title: electron-chat-bootstrap-must-be-idempotent
summary: Electron 开发态的聊天首屏初始化必须按认证态和工作区上下文做幂等保护
tags:
  - chat
  - electron
  - frontend
last_verified_commit: a94b186b0c4414e31299db89e10b89fd10a7b965
status: active
---

## Situation

桌面端聊天页复用用户前端的 `useChatWorkspace`，但 Electron 开发态会同时经历本地 token 恢复、`/api/auth/me` 鉴权状态切换，以及 React StrictMode 的 effect 重放。
Web 端直接刷新时通常只表现为一次首屏初始化；桌面端则更容易在同一个登录会话和同一个工作区上下文下重复请求会话列表、示例题、专家、技能和 MCP。

如果第二轮初始化晚于第一轮 URL 会话恢复，就会把已经恢复好的主区状态重新清空或覆盖，用户感知是“桌面端卡死”，但根因不是 Electron 主进程阻塞。

## Why It Mattered

这个问题横跨认证恢复、工作区分区、URL 会话恢复和 Electron 宿主上下文。只在某一个请求上做防抖会漏掉切换目录、切换运行目标或 URL 会话变化的正常重载场景。

同样地，只判断 `isAuthenticated` 布尔值会把“同一个 token 从未校验切到已校验”误认为新的初始化上下文。

## Rule

聊天工作区首屏 bootstrap 必须按稳定 key 做幂等保护。
这个 key 至少应包含登录 token、当前分区、运行目标、工作空间路径、工作空间 ID、URL 会话、Electron 宿主类型、宿主绑定目录、宿主工作区 ID 和执行目标签名。

同一个 key 已在执行或已完成时，应跳过后续 bootstrap。不同 key 必须允许重新加载，确保用户切换本地目录、运行目标或显式 URL 会话时不会被旧门闩拦住。

## When to Apply

当排查“桌面端卡住、Web 端正常”、首屏重复请求、刷新后 URL 会话被清空、或 StrictMode 下 hook 初始化异常时，先检查 bootstrap 是否对同一个认证和工作区上下文重复执行。

## When Not to Apply

用户主动切换工作区目录、运行目标、登录账号或 URL 会话时，不应复用旧 key 阻止重新初始化。
