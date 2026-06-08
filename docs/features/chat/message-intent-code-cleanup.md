# 消息表意图字段清理

## 功能用途

`chat_message.intent_code` 是早期运行时扩展遗留字段，当前聊天消息领域对象、`ChatMessageDO` 和历史回放接口都不再读取或写入该字段。消息表只保留消息正文、角色、状态、模型、错误、思考内容和所属 run 等消息级数据。

## 使用入口

无需前端入口。已有数据库通过幂等迁移 `V20260609_090000__drop_legacy_chat_message_intent_code.sql` 清理残留列；新环境基线结构 `schema.sql` 本身不定义该列。

## 核心流程

1. 数据库初始化时，`schema.sql` 创建 `chat_message` 表，不包含 `intent_code` 列，也不写该列注释。
2. 旧库升级时，迁移脚本执行 `ALTER TABLE chat_message DROP COLUMN IF EXISTS intent_code`，即使字段已被历史迁移删除也不会失败。
3. 运行时消息保存仍由 `ChatMessageRepositoryImpl` 把 `ChatMessage` 映射为 `ChatMessageDO`，映射范围不包含 `intentCode`。
4. 聊天意图审计保留在 `chat_execution_run.intent_code`，意图树业务编码保留在 `chat_intent_node.intent_code`，本次清理只影响 message 表遗留列。

## 关键文件

- `backend/src/main/resources/db/schema.sql`：当前 message 表基线结构。
- `backend/src/main/resources/db/migration/V20260609_090000__drop_legacy_chat_message_intent_code.sql`：清理旧库残留字段。
- `backend/src/main/java/com/codingx/chat/infrastructure/persistence/dataobject/conversation/ChatMessageDO.java`：消息表 DO 映射，不包含 `intentCode`。
- `backend/src/test/java/com/codingx/chat/infrastructure/persistence/repository/ChatRuntimePersistenceStructureTest.java`：结构回归测试，防止 message 表重新引入该字段。

## 验证方式

- 通过 PostgreSQL `information_schema.columns` 查询 `chat_message.intent_code`，应无返回行。
- 执行 `mvn compile` 确认生产代码不依赖该字段。
- 执行 `ChatRuntimePersistenceStructureTest#chatMessageSchemaDoesNotContainLegacyIntentCode` 可验证 schema、DO 与迁移约束；若工作区存在其他会话导致测试编译失败，应先隔离无关改动后再运行。
