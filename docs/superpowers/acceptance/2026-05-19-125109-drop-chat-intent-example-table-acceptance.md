# Acceptance Criteria: 下线 chat_intent_example 表

**Spec:** `docs/superpowers/specs/2026-05-19-125109-drop-chat-intent-example-table-design.md`  
**Date:** 2026-05-19  
**Status:** Approved

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 意图路由示例仅来自 `chat_intent_node.examples` | Unit | `ConversationIntentServiceTest` 可执行 | 仅节点 JSON 示例被传递到 resolver，旧表示例不再参与 |
| AC-002 | 运行时代码不再依赖 `ChatIntentExampleRepository` | Static check | 后端源码可检索 | 全仓无 `ChatIntentExampleRepository`/`ChatIntentExampleMapper`/`ChatIntentExampleDO` 引用 |
| AC-003 | 数据库基线不再声明 `chat_intent_example` | Static check | `schema.sql` 存在 | `schema.sql` 中无该表及其索引定义 |
| AC-004 | 初始化脚本不再写入 `chat_intent_example` | Static check | `init.sql` 存在 | `init.sql` 中无 `INSERT INTO chat_intent_example` 语句 |
| AC-005 | 存在安全下线迁移脚本 | Migration check | migration 目录可检索 | 新迁移先回填 `chat_intent_node.examples` 后 `DROP TABLE chat_intent_example` |
| AC-006 | 后端编译和测试通过 | Build/Test | Maven 环境可用 | `mvn compile` 与 `mvn test` 全部通过 |
