# Weather Slot Clarify And Ambiguity Log Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 天气 MCP 缺少城市参数时在执行工具前澄清，并在真正触发歧义引导时打印日志。

**Architecture:** 在意图路由层增加天气 MCP 槽位保护，保持“多意图歧义”和“工具参数缺失”两个概念分离。抽取天气问题参数解析组件，供路由保护和天气工具执行器复用，避免两处维护城市识别规则。

**Tech Stack:** Spring Boot、JUnit 5、Mockito、Hutool、Logback 测试 appender。

---

### Task 1: RED 覆盖天气缺城市澄清

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ConversationIntentServiceTest.java`
- Create: `backend/src/test/java/com/codingx/chat/application/service/WeatherQuestionParserTest.java`

- [x] **Step 1: Write the failing tests**

新增用例要求 `weather_query` 命中但问题为“天气怎么样”时返回 `CLARIFY`，问题为“上海今天天气怎么样”时仍返回 `MCP`；新增解析器用例覆盖“那个天气怎么样”不能被识别为城市。

- [x] **Step 2: Run tests to verify RED**

Run: `mvn "-Dtest=ConversationIntentServiceTest,WeatherQuestionParserTest" test`

Expected: 至少天气缺城市澄清用例失败，当前实现仍返回 `MCP`。

### Task 2: GREEN 实现天气槽位保护

**Files:**
- Create: `backend/src/main/java/com/codingx/mcp/application/executor/WeatherQuestionParser.java`
- Modify: `backend/src/main/java/com/codingx/mcp/application/executor/WeatherMcpToolExecutor.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentService.java`

- [x] **Step 1: Extract parser**

把天气工具中的城市、查询类型和天数解析拆到 `WeatherQuestionParser`，保留 AI 城市抽取优先级入口。

- [x] **Step 2: Add route guard**

`ConversationIntentService` 在 `weather_query` 且 MCP 启用时检查城市；缺城市返回中文澄清，不执行 MCP。

- [x] **Step 3: Run GREEN tests**

Run: `mvn "-Dtest=ConversationIntentServiceTest,WeatherQuestionParserTest,WeatherMcpToolExecutorTest" test`

Expected: 相关测试全部通过。

### Task 3: 歧义触发日志与文档

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentAmbiguityDetector.java`
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ConversationIntentGuidanceServiceTest.java`
- Create: `docs/features/chat/weather-mcp-city-clarify.md`
- Modify: `docs/features/index.md`
- Modify: `docs/features/chat/intent-guidance-config.md`

- [x] **Step 1: Add log assertion**

给真实歧义触发用例挂载 Logback appender，断言输出包含“歧义引导触发”。

- [x] **Step 2: Add INFO log**

检测器生成 `AmbiguityGroup` 前打印问题、主题、候选数和候选摘要。

- [x] **Step 3: Update docs**

补充天气城市澄清功能说明，并在歧义配置文档中记录触发日志。

### Task 4: 验证与提交

**Files:**
- Stage only files changed by this plan.

- [ ] **Step 1: Run backend verification**

Run: `mvn compile`

Run: `mvn test`

- [ ] **Step 2: Commit**

Run: `git add <this task files>`

Run: `git commit -m "fix(chat): 完善天气澄清与歧义日志"`
