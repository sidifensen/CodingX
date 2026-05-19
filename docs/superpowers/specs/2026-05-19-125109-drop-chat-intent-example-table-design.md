# 下线 chat_intent_example 表设计规格

## 1. 背景

当前聊天意图识别示例数据存在双存储：`chat_intent_node.examples`（JSON）与 `chat_intent_example`（行表）。  
运行时代码同时读取两处，导致配置维护与初始化脚本存在重复写入风险。

## 2. 目标

- 移除运行时对 `chat_intent_example` 的依赖；
- 统一以 `chat_intent_node.examples` 作为示例数据唯一来源；
- 通过迁移脚本兼容旧环境，确保下线不丢示例数据；
- 同步清理基线结构与初始化脚本中的冗余表定义和种子数据。

## 3. 方案

### 3.1 应用层

- `ConversationIntentService.collectExamples` 仅解析节点 `examples`；
- 删除 `ChatIntentExampleRepository` 及其 MyBatis 实现（DO/Mapper/RepositoryImpl）；
- 保留 `ChatIntentExample` 领域模型用于解析后的内存对象传递，避免影响 `ConversationIntentResolver` 接口。

### 3.2 数据库层

- 新增迁移脚本：
  1. 将 `chat_intent_example` 按 `intent_code` 聚合回填到 `chat_intent_node.examples`（仅在目标为空时）；
  2. 删除索引 `idx_chat_intent_example_code`；
  3. 删除表 `chat_intent_example`。
- 更新 `schema.sql`，移除该表和索引定义；
- 更新 `init.sql`，移除对该表的初始化写入。

### 3.3 测试层

- 更新 `ConversationIntentServiceTest`，验证仅节点 `examples` 参与路由示例构建；
- 更新 `ChatRuntimePersistenceStructureTest`，去除对下线表骨架的断言；
- 运行后端编译与测试确保无回归。

## 4. 风险与缓解

- 风险：历史环境可能仅在 `chat_intent_example` 有示例数据。  
  缓解：迁移脚本先回填后删表，且只覆盖空的 `chat_intent_node.examples`。

- 风险：修改已发布迁移会触发 Flyway 校验失败。  
  缓解：不修改历史迁移，仅新增迁移完成下线动作。

## 5. 完成判定

- 代码中不再存在 `ChatIntentExampleRepository` 相关引用；
- `schema.sql` 与 `init.sql` 不再包含 `chat_intent_example`；
- 新迁移存在且可执行；
- `mvn compile`、`mvn test` 通过。
