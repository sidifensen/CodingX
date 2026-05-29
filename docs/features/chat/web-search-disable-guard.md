# 系统联网搜索禁用守卫

## 功能用途

聊天运行时支持按 `web_search.enabled` 控制系统内置联网搜索。关闭后，搜索型意图只作为普通模型输入处理，不再创建搜索过程卡片、调用外部搜索 provider、写入搜索引用或生成搜索整理文档。用户显式选择 `web-access` 技能时，也不会再抢先走系统内置搜索链路。

## 使用入口

- 管理端或运行时配置将 `web_search.enabled` 设置为 `false`。
- 用户在聊天输入中选择或输入 `@web-access`，例如 `@web-access 搜一下今天的新闻`。

## 核心流程

1. `ChatCapabilityMentionSupport` 合并前端显式技能和正文前缀里的技能编码。
2. `ConversationIntentService` 仍按自然语言完成意图识别。
3. `ChatApplicationService` 在搜索执行前检查系统搜索开关和 `web-access` 技能选择。
4. 命中禁用条件时，`SEARCH` 子问题降级为 `DIRECT`，后续不进入 `executeSearchQuestions(...)`。
5. 模型继续接收普通会话历史；若选择了 `web-access`，系统消息会包含对应技能上下文。

## 关键文件

- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：集中执行自动搜索守卫和搜索决策降级。
- `backend/src/main/java/com/codingx/chat/application/service/support/RuntimeSettingService.java`：读取 `web_search.enabled` 运行时开关。
- `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationSearchFlowTest.java`：覆盖搜索关闭和 `web-access` 技能选择的回归场景。

## 边界条件

- 本守卫只禁用系统内置搜索，不新增 `web-access` 的 Java/CDP 执行器。
- 系统搜索开启且未选择 `web-access` 时，原有搜索、引用收集和文档产物链路保持不变。
- 混合问题中的 MCP 子问题不受搜索守卫影响。

## 验证方式

```bash
cd backend
mvn -DskipTests compile
mvn -Dtest=ChatApplicationSearchFlowTest#sendMessageSkipsSearchFlowWhenWebSearchDisabled+sendMessageWithWebAccessSkillSkipsSystemSearchFlow test
```

当前工作区的测试编译会被无关的技能上传测试签名问题阻塞；修复那些测试后可执行上述定向回归。
