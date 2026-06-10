# 聊天改写历史上下文

## 功能用途

聊天改写阶段会把短指代问题补全为独立问题，例如用户先问“牛顿力学是什么”，再问“那个有什么用”时，改写模型应看到上一轮真实问答并输出“牛顿力学有什么用”。这样后续意图识别和联网搜索都使用改写后的独立查询，而不是拿当前短指代原句直接搜索。

## 使用入口

- 云端聊天发送消息入口：`ChatApplicationService` 保存本轮用户消息后调用 `ConversationRewriteService.rewriteResultFromMessages(...)`。
- 云端重新生成入口：重新生成会恢复原始用户消息和历史，再调用同一个完整消息历史改写入口。
- 系统配置入口：`setting.chat.rewrite.history_turns` 控制改写阶段最多读取最近多少轮历史，默认 `3`，最小 `0`，硬上限 `10`。

## 核心流程

1. 用户在聊天页发送消息后，后端先生成或恢复本轮 `ChatMessage`，并把用户输入中的 `@skill` 这类能力标记剥离成模型可读文本。普通云端发送会把新用户消息追加到 `history`，重新生成会确保原始用户消息仍在历史中。后端随后把完整用户/助手消息列表传给改写服务，而不是只传用户问题字符串。
2. `ConversationRewriteService` 先按 `chat.rewrite.history_turns` 读取配置并限制历史轮次。构造上下文时会定位本轮用户问题，截断掉本轮问题及其后的旧助手回复，只保留它之前最近 N 个用户轮次以及这些轮次后的助手答复。配置为 `0` 时上下文为空，配置过大时最多取 `10` 轮，避免 prompt 不受控膨胀。
3. 改写 Prompt 使用稳定的角色行渲染历史，例如 `用户：牛顿力学是什么` 和 `助手：牛顿力学是经典力学体系。`。如果没有可用历史，上下文写为 `无`，模型不会被误导去补不存在的指代。后端会在调用改写模型前打印 `改写历史上下文` INFO 日志，包含配置轮次和实际渲染上下文，超长内容按日志预览长度截断。
4. 自包含问题仍可走改写旁路，减少不必要的 LLM 等待；但只要当前问题存在“那个/这个/它”等依赖历史的短指代，并且上下文中有上一轮用户问题，就会继续调用改写模型。模型返回 JSON 后，服务解析 `rewrite`、`should_split` 和 `sub_questions`；格式不符合约定时回退到归一化后的当前问题并记录降级日志。
5. 搜索链路不直接接收历史上下文。`ChatApplicationService` 后续通过 `resolveSubQuestionDecisions(...)` 和 `routedQuestions(...)` 读取 `ConversationRewriteResult`，联网搜索 provider 只拿到改写后的独立问题或拆分后的子问题。这样“那个有什么用”的搜索请求会使用“牛顿力学有什么用”，但 provider 端不会暴露整段聊天历史。

## 关键文件

- `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationRewriteService.java`：构造结构化历史上下文、调用改写模型、打印实际上下文日志。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：云端发送和重新生成入口改为传完整 `ChatMessage` 历史。
- `backend/src/main/java/com/codingx/chat/application/service/support/RuntimeSettingService.java`：读取并约束 `chat.rewrite.history_turns`。
- `backend/src/main/resources/db/migration/V20260610_151611__add_chat_rewrite_history_turns_setting.sql`：新增系统配置迁移。
- `backend/src/main/resources/db/init.sql`：新环境初始化配置种子。

## 测试与验证

- `mvn "-Dtest=ConversationRewriteServiceTest" test`
- `mvn "-Dtest=RuntimeSettingServiceTest" test`
- `mvn compile`
