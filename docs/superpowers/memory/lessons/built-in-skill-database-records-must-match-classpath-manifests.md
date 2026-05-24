---
type: lesson
title: built-in-skill-database-records-must-match-classpath-manifests
summary: 内置技能写入 skill 表后，必须同步提供类路径 skills/<skillCode>/SKILL.md，否则运行时上下文服务无法读取说明文档
tags:
  - skill
  - backend
  - chat
last_verified_commit: 3f59c7f
status: active
---

## Situation

`ChatSkillContextService` 读取技能说明时，对 `sourceType = built-in` 的技能会优先尝试从类路径 `skills/<skillCode>/SKILL.md` 取内容；只有存在 `storageKey` 的上传技能才会走对象存储。

这意味着，单纯在 `skill` 表里新增一条 built-in 记录并不够。如果没有对应的类路径 manifest，数据库里虽然“看得到”技能，但聊天上下文拼装阶段会把它跳过，最终表现为技能已入库却无法注入提示词。

## Rule

新增或维护 built-in 技能时，必须同时满足两项：

1. `skill` 表中存在对应记录，且 `source_type = 'built-in'`
2. `backend/src/main/resources/skills/<skillCode>/SKILL.md` 存在并包含可读内容

如果技能本来依赖对象存储包，则应走 `storageKey` 路径，不要把它伪装成 built-in。

## When to Apply

当新增内置技能、迁移内置技能，或者修复“数据库里有记录但聊天里读不到技能说明”的问题时，必须检查这条规则。

典型信号包括：

- 只改了数据库迁移，没有新增类路径 `SKILL.md`
- 测试里能查到技能记录，但 `buildSkillContext()` 返回空字符串
- 管理端列表里有技能，聊天上下文却没有对应 `## /<skillCode>` 段落

## Verification

可用的最小验证方式：

- 查询 `skill` 表确认记录存在
- 运行 `mvn test -Dtest=ChatSkillContextServiceTest`，确认 built-in 技能能够从类路径成功读取说明文档
- 如果是新增技能，补一条专门覆盖该 `skillCode` 的单测，避免下次只改数据不改资源
