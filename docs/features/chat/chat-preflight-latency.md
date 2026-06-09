# 聊天前置链路延迟优化

## 功能用途

减少普通聊天在真正模型回答前的额外等待。对自包含单一问题跳过问题改写模型，即使当前会话已有旧历史，只要本轮问题不依赖上文也不再强制改写；对配置示例精确命中和明显普通直答问题跳过意图分类模型，避免简单解释、分析、总结、写作类请求先串行等待改写和意图识别。

## 使用入口

- 云端聊天发送入口：`ChatApplicationService.sendMessage`
- 本地临时聊天入口：`ChatApplicationService.sendLocalOnlyMessage`
- 重新生成入口：`ChatApplicationService.processRegeneratedMessage`

## 核心流程

1. 用户发送消息后，聊天主流程仍先完成内容校验、会话归属校验、运行门控和消息落库。进入改写前，`plainUserContents(history)` 可能已经包含本轮用户输入，因此改写服务会把与当前问题完全一致的历史项视为本轮输入，而不是上一轮上下文。
2. `ConversationRewriteService` 先执行术语映射归一化，再判断是否需要改写模型。长度适中的单一自包含问题会直接返回归一化后的原问题；如果会话里存在旧历史，但本轮问题没有“这个、上面、刚才、继续、怎么改”等上下文依赖信号，也同样跳过改写模型。多诉求、显式搜索、时效信息、简单问候和需要上下文补全的短指代仍保留原 Prompt 改写与拆分能力。
3. `ConversationIntentResolver` 建立启用意图节点、叶子节点和示例索引后，先检查配置示例是否精确命中。归一化后完全等于示例的问题会直接返回 0.96 分候选，跳过意图分类模型；模糊相似问题仍走模型分类。
4. 当本地配置兜底没有任何候选，且问题明显属于解释、分析、总结、写作、翻译、代码说明等普通直答形态时，意图解析直接返回空候选。上层 `ConversationIntentService` 会按既有规则把空候选转为 `chat.normal` 的 `DIRECT` 决策，然后进入最终模型回答。
5. 搜索、天气、汇率、最新、新闻、版本、多诉求等可能需要搜索、MCP 或拆分的问题不会走普通直答旁路。它们继续通过模型分类或后续搜索/MCP 保护链路，避免为了速度丢失外部信息和工具分流。

## 关键文件

- `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationRewriteService.java`：判断首轮自包含问题并跳过改写 Prompt。
- `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentResolver.java`：配置示例精确命中和普通直答旁路。
- `backend/src/test/java/com/codingx/chat/application/service/ConversationRewriteServiceTest.java`：覆盖自包含问题跳过改写模型。
- `backend/src/test/java/com/codingx/chat/application/service/ConversationIntentResolverTest.java`：覆盖示例精确命中和普通直答跳过意图分类模型。

## 边界条件

- 带上一轮上下文的“这个怎么改”“继续优化”等问题保留改写模型，用于补全指代对象。
- 长会话里重新发起的独立问题不会因为 history 非空而强制改写，避免每轮回答都先串行等待改写模型。
- 包含“分别、然后、以及、同时”等多诉求标记的问题保留改写拆分能力。
- 包含搜索、时效、天气、汇率、新闻和版本信号的问题保留意图分类模型，避免错过搜索或 MCP。
- 旁路只减少前置模型调用，不改变最终回答模型、消息落库、SSE 推送和运行结果收口。

## 测试与验证

- `mvn compile`
- `ConversationRewriteServiceTest#rewriteResultBypassesPromptForSelfContainedQuestionWithoutHistory`
- `ConversationIntentResolverTest#resolveCandidatesBypassesPromptForExactConfiguredExample`
- `ConversationIntentResolverTest#resolveCandidatesBypassesPromptForPlainDirectQuestionWithoutConfiguredMatch`
