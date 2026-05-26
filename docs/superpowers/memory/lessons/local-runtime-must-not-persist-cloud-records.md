---
type: lesson
title: local-runtime-must-not-persist-cloud-records
summary: 本地运行态默认不得写入云端 workspace、conversation、message、task、run 或过程记录
tags:
  - chat
  - local-runtime
  - persistence
last_verified_commit: df563bd4
status: active
---

# Local Runtime Must Not Persist Cloud Records

## Situation

桌面端选择本地文件夹后，早期实现会把目录绑定转换成 `workspace` 表记录，再把本地聊天会话写入 `chat_conversation`、`chat_message` 以及 task/run/step 等执行侧表。

这会让“本地项目历史”进入云端数据库，用户在云端工作空间、管理端工作空间列表或默认历史中看到本应只留在本机的记录。

## Rule

本地运行态默认只能把后端当作临时执行器：

- 允许：校验本地路径、绑定线程级工具工作目录、发布 SSE、把最终结果交给前端本地快照
- 禁止：创建或复用云端 `workspace` 作为本地目录元数据
- 禁止：为本地聊天写 `chat_conversation`、`chat_message`
- 禁止：为本地聊天写 task、run、trace、能力绑定、step、artifact、search reference 等可被云端历史或管理端读取的记录

如果未来要做本地历史同步，必须是显式用户选择的同步功能，不能复用默认本地运行态。

## Implementation Notes

当前契约由以下边界共同保证：

- `ChatWorkspaceBindingService.bindRepositoryPathForCurrentUser` 只返回规范化目录与名称，`workspaceId` 为 `null`
- 前端本地 stream URL 带 `runtimeTarget=local` 和 `repositoryPath`，不带 `workspaceId`
- 前端本地历史只从 `codingx.chat.workspace.conversations.v1` 快照恢复，不回查云端 conversations/messages replay API
- 后端 `runtimeTarget=local` 使用临时数值 conversation id 做 SSE 路由，并通过 `SendChatMessageCommand.localOnly()` 跳过数据库持久化链路

## When to Apply

涉及以下入口时必须检查这个规则：

- 选择本地项目目录
- 本地模式发送、停止、重试、继续对话
- 本地工具调用、搜索、artifact 或任务状态接入
- 管理端工作空间和云端默认历史查询

## Verification

最小验证应覆盖：

- 本地目录绑定不调用 `workspaceMapper.insert`
- 本地 stream 不调用云端会话创建
- 本地发送完成后不回查云端会话列表或消息回放
- 本地运行态不写 conversation/message/task/run/process 相关仓储
