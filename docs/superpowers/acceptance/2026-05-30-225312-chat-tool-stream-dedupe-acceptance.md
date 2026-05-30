# Chat Tool Stream Dedupe Acceptance

## 验收标准

- 当模型第一轮已经通过 SSE 输出正文并请求本地工具，第二轮又从相同正文前缀开始输出时，后端必须跳过重复前缀。
- 被跳过的重复前缀不得调用 `ChatStreamPublisher.publishAssistantDelta`，也不得追加到最终助手消息正文。
- 如果第二轮 delta 只有部分内容与已发布前缀重复，后端只发布和落库剩余的新内容。
- 如果第二轮 delta 与已发布前缀不匹配，后端按原样发布，避免误删真实新回答。
- 普通无工具聊天流保持现有行为。

## 验证方式

- 定向执行 `mvn -Dtest=ChatApplicationServiceTest#sendMessageSkipsRepeatedPublishedPrefixAfterToolCall test`，新增测试应先失败后通过。
- 根据改动面执行后端编译与测试；若遇到其他会话无关改动导致失败，只说明阻塞并保留本次修改。
