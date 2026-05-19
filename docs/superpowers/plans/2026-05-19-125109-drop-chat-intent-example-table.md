# 下线 chat_intent_example 表 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.  
> 步骤使用 `- [ ]` 任务勾选格式，按 RED-GREEN-REFACTOR 执行。

## Goal

删除冗余 `chat_intent_example` 表及代码依赖，统一示例数据来源到 `chat_intent_node.examples`，并保证旧环境升级数据安全。

## Task 1: RED - 调整测试暴露旧表依赖

**Files**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ConversationIntentServiceTest.java`

- [ ] 将测试改为仅验证节点 `examples` 生效，不再 mock `ChatIntentExampleRepository`
- [ ] 运行 `mvn -Dtest=ConversationIntentServiceTest test`，确认因生产代码仍依赖旧仓储而失败

## Task 2: GREEN - 移除运行时依赖并清理持久化骨架

**Files**
- Modify: `backend/src/main/java/com/codingx/chat/application/service/ConversationIntentService.java`
- Delete: `backend/src/main/java/com/codingx/chat/domain/repository/ChatIntentExampleRepository.java`
- Delete: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/dataobject/ChatIntentExampleDO.java`
- Delete: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/mapper/ChatIntentExampleMapper.java`
- Delete: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/repository/ChatIntentExampleRepositoryImpl.java`
- Modify: `backend/src/test/java/com/codingx/chat/infrastructure/persistence/repository/ChatRuntimePersistenceStructureTest.java`

- [ ] 生产代码只从节点 JSON 示例收集样例
- [ ] 删除旧示例表仓储相关实现与接口
- [ ] 调整结构测试去掉旧骨架断言
- [ ] 运行 `mvn -Dtest=ConversationIntentServiceTest test` 应转绿

## Task 3: REFACTOR - 数据库基线与迁移对齐

**Files**
- Modify: `backend/src/main/resources/db/schema.sql`
- Modify: `backend/src/main/resources/db/init.sql`
- Create: `backend/src/main/resources/db/migration/V20260519_125500__drop_chat_intent_example_table.sql`

- [ ] 移除 `schema.sql` 中 `chat_intent_example` 建表和索引
- [ ] 移除 `init.sql` 对该表的种子写入
- [ ] 新增迁移先回填后删表，避免数据丢失

## Task 4: Verify

- [ ] 运行 `mvn compile`
- [ ] 运行 `mvn test`
- [ ] 检查 `rg "chat_intent_example"` 仅保留历史迁移与新删表迁移引用

## Task 5: Delivery

- [ ] 汇总改动与验证结果
- [ ] 按规范使用中文提交信息提交代码
