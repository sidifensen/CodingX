# Codex Style Process Display Design

**Date:** 2026-05-27
**Status:** Approved by direct implementation request

## Goal

把聊天主消息区的过程链路调整成 Codex 风格：真实 thinking 默认可见，网页搜索和本地工具调用按聚合行展示，展开后能查看搜索词、来源、命令和输出。

## Current Context

项目已经具备 `thinking` SSE、`tool-call`、`mcp-call`、搜索 `reference` 事件和前端 `ProcessCardItem` 过程卡片。当前展示更接近逐条过程流：多条搜索结果会合并，但搜索动作、来源和 shell 工具仍缺少类似 Codex 的“已搜索网页 N 次 / 已运行 N 条命令”聚合入口，长任务会把主消息区撑得很长。

## Architecture

本次优先在前端展示层做归一化，不改变后端 Agent Loop 和工具执行协议。`ProcessTracePanel` 在渲染前把连续搜索相关卡片合并成搜索聚合段，把连续 shell/命令工具调用合并成命令聚合段；展开聚合段时复用现有工具明细行，并为 shell 结果提供 Codex 风格的命令输出块。

真实 thinking 继续只消费后端 `thinking` 事件或历史 `thinkingContent`，不伪造模型隐藏思维链。工具调用和搜索过程展示的是公开过程事件、参数与结果，属于可审计执行日志。

## User Experience

- 深度思考默认展开，显示真实 thinking 文本。
- 多条网页搜索过程默认折叠为一行：`已搜索网页 N 次`。
- 搜索聚合展开后展示搜索动作、搜索来源和来源链接。
- 多条 shell/命令工具默认折叠为一行：`已运行 N 条命令`。
- 命令聚合展开后展示每条命令的 Shell 块、输出摘要和状态。
- 非 shell 工具保持现有单行工具展示，避免误把普通业务工具包装成命令。

## Constraints

- 不展示不可访问的模型私有思维链；只展示真实 `thinking` 或公开过程摘要。
- 不改变后端数据库结构和工具调用协议。
- 不改动其他会话已有的任务提醒、会话字段等脏改动。
- 前端必须兼容亮色和暗色主题，展开面板背景、边框、滚动条都要可读。
- 历史回放和流式中间态都走同一套渲染规则。

## Testing Strategy

- 前端视图单测验证连续搜索结果折叠成 `已搜索网页 N 次`，展开后能看到来源。
- 前端视图单测验证连续 shell 调用折叠成 `已运行 N 条命令`，展开后能看到命令和输出。
- 前端视图单测验证真实 thinking 默认展示且仍可折叠。
- 执行用户前端定向测试、构建测试；如可启动服务，则用 CDP 跑一个长任务并保存截图。
