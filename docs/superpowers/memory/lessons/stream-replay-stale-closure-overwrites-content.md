---
type: lesson
title: stream-replay-stale-closure-overwrites-content
summary: 避免流式收敛回放读取旧闭包，把刚生成完的 assistant 正文覆盖成空
tags:
  - chat
  - state
last_verified_commit: 6576a5b0d243648431c1165c9c38bbfefa0fe8c2
status: active
---

## Situation

聊天工作区里，用户发送消息后会先创建乐观 assistant 消息并开始 SSE 流式增量。等 `finish` 事件到达时，前端会先刷新会话列表，再调用 `selectConversation()` 或 `restoreWorkspaceSnapshot()` 重新回放当前会话。

这次出问题的点在于，回放路径读取的是旧闭包里的 `messages`，不是流式过程中最新的消息态。由于刚创建的历史记录里 assistant 正文仍可能是空，最终会把刚生成完的正文覆盖掉，表现成“先显示一会儿，然后突然消失，最后又整段回来”。

## Why It Mattered

用户看到的不是单纯的慢，而是同一次回答里内容状态反复跳变。更糟的是，空的历史回放还可能被写进本地快照，后续刷新或重进会话时又重复一次同样的闪烁。

## Rule

流式会话收敛到历史回放时，不能把旧闭包里的消息列表当作最新真相。要么读最新引用，要么在回放合并时显式告诉它这是“流结束后的收敛路径”，只补面板字段，不允许空历史正文覆盖更完整的本地正文。

## When to Apply

当同一条 assistant 消息正在流式生成、刚完成生成，或者刚完成后马上要做会话回放时，必须遵守这条规则。

典型信号包括：

- `finish` 后马上调用 `selectConversation()`
- 需要把流式期间采集到的 `mcpCalls`、`searchProgress`、`processCards` 回填到历史消息
- 历史接口返回的 assistant 正文为空，但内存中的流式消息已经有更完整内容

## When NOT to Apply

普通历史会话浏览不适用这条规则。若用户明确切换到另一条会话、删除当前会话、取消生成，或者当前并不存在本地流式正文，那么历史回放应当以接口返回为准，不要强行保留旧的乐观内容。
