# 聊天简单问候快速响应

## 功能用途

当用户只发送“你好”“hi”“hello”等简单问候时，后端直接返回固定中文问候回复，避免进入问题改写、意图识别、长期记忆、提示词组装和模型工具循环造成数秒等待。

## 使用入口

- 云端聊天发送入口：`ChatApplicationService.sendMessage`
- 本地临时聊天入口：`ChatApplicationService.sendLocalOnlyMessage`

## 核心流程

1. 用户发送消息后，后端先校验内容非空、会话归属和运行门控。本地临时会话只执行运行门控，不读取云端会话。
2. 服务合并消息里的技能 mention，并得到去 mention 后的 `plainQuestion`。快速路径只在没有技能、附件、专家、仓库路径、深度思考、规划模式和 skillPaths 时继续判断；输入框上保留的 MCP 选择只代表可用工具上下文，纯问候没有工具执行意图，因此不会阻止快答。
3. 问候文本会去掉空白与常见标点，并将英文转小写后进入白名单匹配。只有归一化结果完整等于白名单问候时才命中，带真实任务的“你好，帮我看代码”不会短路。
4. 云端命中后保存用户消息并发布用户消息 SSE，再保存固定助手消息、更新会话最后 runId、写入 `sys-welcome` 执行结果、完成 Trace，并发布助手完成 SSE。
5. 本地临时命中后只发布用户消息和助手完成 SSE，不写入云端消息、run、Trace 或任务表，保持本地模式不落库。
6. 未命中时继续原有完整链路，按历史读取、附件归属校验、自动化任务、改写、意图分流、搜索、MCP、模型工具循环和异常收口执行。

## 关键文件

- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：实现简单问候快速路径、白名单判断和云端收口。
- `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationIntentFlowTest.java`：验证纯问候跳过历史、附件、改写、意图和模型调用，同时保留系统意图模型回答。

## 关键数据

- `QUICK_GREETING_REPLY`：固定中文助手回复。
- `QUICK_GREETING_TEXTS`：允许短路的问候白名单。
- `chat_execution_run.intent_code`：快速路径记录为 `sys-welcome`，用于运行历史和 Hook 判断。

## 测试与验证

- 定向执行 `mvn "-Dtest=com.codingx.chat.application.service.ChatApplicationIntentFlowTest#sendMessageRepliesGreetingWithoutRewriteIntentOrModelCall" test`。
- 类级执行 `mvn "-Dtest=com.codingx.chat.application.service.ChatApplicationIntentFlowTest" test`。
- 后端执行 `mvn compile` 和 `mvn test`。
