# 会话压缩闭环设计

## 背景

当前聊天链路虽然已存在 `chat_conversation_summary` 与 `ConversationSummaryService`，但仅完成“摘要落库”，未完成“摘要参与后续入模上下文”。随着会话轮次增长，模型输入持续膨胀，容易导致 token 成本上升与响应不稳定。

## 目标

1. 对齐 `D:\code\ragent` 的核心思路：摘要 + 最近窗口原文。
2. 让摘要真正参与后续模型请求，而不是只做持久化记录。
3. 保持现有聊天意图分流、MCP、搜索链路行为不回归。

## 方案

### 1. 引入会话压缩配置

新增 `app.chat.memory` 配置段，统一管理：

- `summary-enabled`：是否启用压缩
- `summary-trigger-messages`：摘要触发阈值
- `history-keep-turns`：入模保留最近轮次
- `summary-max-characters`：摘要最大长度

通过 `ChatMemoryProperties` 绑定配置，并在 `CodingXApplication` 注册。

### 2. 摘要生成改为“增量压缩”

`ConversationSummaryService.refreshSummaryIfNeeded` 调整为：

1. 只在开启压缩且达到阈值时触发
2. 按“最近窗口保留”计算可压缩区间（窗口外消息）
3. 基于 `last_message_id` 只压缩新增区间，避免重复汇总
4. 更新摘要并推进 `last_message_id` 到本次压缩截止点

### 3. 入模上下文改为“摘要 + 最近原文”

新增 `ConversationSummaryService.buildModelHistory`：

1. 命中摘要时，先构造摘要 `system` 消息
2. 从完整历史中过滤摘要覆盖点之后的消息
3. 再按 `history-keep-turns` 进行窗口裁剪
4. 输出给 `ChatApplicationService` 作为真实入模历史

`ChatApplicationService` 在 `buildAiHistory` 前调用该方法，确保所有 LLM 链路共享压缩后的上下文。

## 风险与约束

1. 摘要文本由规则拼接，不调用额外 LLM 生成，先保证可用性与稳定性。
2. 摘要说明词通过 `system` 消息注入，避免模型把摘要误解为最新用户输入。
3. 若摘要记录异常或缺失，回退到最近窗口原文，不影响主链路可用性。

## 测试策略

1. `ConversationDigestServiceTest`：验证阈值配置化生效。
2. `ConversationSummaryServiceTest`：验证增量压缩截止点与“摘要+窗口”上下文拼装。
3. `ChatApplication*` 相关测试：验证主链路接入新上下文构造后不回归。
4. 后端构建与全量测试：`mvn compile`、`mvn test`。
