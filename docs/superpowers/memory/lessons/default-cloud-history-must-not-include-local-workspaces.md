---
type: lesson
title: default-cloud-history-must-not-include-local-workspaces
summary: 默认云端历史查询必须和本地工作空间历史分开，否则桌面端本地会话会污染 Web 历史
tags:
  - chat
  - workspace
  - backend
last_verified_commit: caee79f17a1380c5d5c1915b4f3a1a12a60ab703
status: active
---

## Situation

聊天历史列表最初把 `workspaceId == null` 当成“用户全部会话”来处理，这在早期可以勉强工作，因为会话主要都落在默认云端空间。

当桌面端引入本地工作空间后，`workspaceId` 开始稳定承载本地目录会话。此时如果 Web 端历史仍然使用“空 workspaceId 代表全部会话”的语义，就会把本地工作空间新建的会话一起读出来，导致云端历史和本地历史互相串线。

## Why It Mattered

这个问题不是展示层的排序问题，而是后端查询语义错了。前端只能做临时过滤，不能把混入历史列表的本地会话从源头上真正隔离掉。

更隐蔽的是，旧数据里还存在 `workspace_id IS NULL` 的会话。如果后端简单把 `workspaceId == null` 改成只查默认云端工作空间，老历史会短暂“消失”。

## Rule

默认云端历史查询必须同时满足两个条件：

1. 展示范围只包含默认云端工作空间的会话
2. 兼容历史遗留的 `workspace_id IS NULL` 会话，但只把它们视作旧云端历史，不要把本地工作空间会话并进来

如果调用方真的想查本地工作空间，必须显式传 `workspaceId`。

## When to Apply

当接口语义是“用户默认历史”“Web 端会话列表”或“云端历史入口”时，必须使用默认云端语义。

典型信号包括：

- `GET /api/chat/conversations` 未传 `workspaceId`
- Web 端历史页刷新
- 需要把本地工作空间与云端历史分组展示

## When NOT to Apply

如果调用方明确在查某个本地工作空间，或者已经传入 `workspaceId`，就不要套用默认云端语义。

另外，创建会话时的 `workspaceId == null` 仍然表示“自动归入默认云端空间”，这和列表查询语义是不同的，不能混用。
