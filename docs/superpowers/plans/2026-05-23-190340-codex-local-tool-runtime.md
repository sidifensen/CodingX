# Codex Local Tool Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Electron 本地 workspace 对话中的 AI 可以自主调用真实 Java 本地工具读写文件、执行命令和应用补丁。

**Architecture:** 后端以 Java 执行器注册表作为真实工具来源，生成 OpenAI function schema；模型层解析 tool call；聊天主流程执行工具并将结果回灌给模型。数据库工具记录只负责展示与启用态，不再伪装没有执行器的能力。

**Tech Stack:** Spring Boot, Java 21, Hutool, OkHttp, PostgreSQL migration, JUnit 5, Mockito.

---

### Task 1: Tool Schema And Runtime Metadata

**Files:**
- Create: `backend/src/main/java/com/codingx/tool/application/service/ChatToolSpec.java`
- Create: `backend/src/main/java/com/codingx/tool/application/service/ChatToolSpecService.java`
- Modify: `backend/src/main/java/com/codingx/tool/application/service/CodexBuiltinChatToolExecutor.java`
- Test: `backend/src/test/java/com/codingx/tool/application/service/ChatToolSpecServiceTest.java`

- [ ] **Step 1: Write failing tests**

Add tests asserting enabled registered tools appear in schema and disabled/missing tools do not.

- [ ] **Step 2: Run red test**

Run: `mvn -Dtest=ChatToolSpecServiceTest test`
Expected: fails because `ChatToolSpecService` does not exist.

- [ ] **Step 3: Implement minimal schema service**

Create immutable spec records with `name`、`description`、`parameters`，and generate schemas for executable local tools.

- [ ] **Step 4: Run green test**

Run: `mvn -Dtest=ChatToolSpecServiceTest test`
Expected: pass.

### Task 2: Tool Execution Safety And Real Workspace Output

**Files:**
- Modify: `backend/src/main/java/com/codingx/tool/application/service/CodexBuiltinChatToolExecutor.java`
- Modify: `backend/src/main/java/com/codingx/tool/application/service/AdminChatToolService.java`
- Test: `backend/src/test/java/com/codingx/tool/application/service/CodexBuiltinChatToolExecutorTest.java`

- [ ] **Step 1: Write failing tests**

Add tests for `shell_command` creating a file under bound workspace and for unsupported `spawn_agent` returning an unavailable error.

- [ ] **Step 2: Run red test**

Run: `mvn -Dtest=CodexBuiltinChatToolExecutorTest test`
Expected: workspace metadata or unsupported behavior assertions fail.

- [ ] **Step 3: Implement runtime metadata and unsupported handling**

Return `workingDirectory` for command tools and replace fake success for unsupported Codex session tools with explicit unavailable errors.

- [ ] **Step 4: Run green test**

Run: `mvn -Dtest=CodexBuiltinChatToolExecutorTest test`
Expected: pass.

### Task 3: AI Tool Call Model Parsing

**Files:**
- Create: `backend/src/main/java/com/codingx/common/support/ai/AiToolCall.java`
- Modify: `backend/src/main/java/com/codingx/common/support/ai/AiConversationRequest.java`
- Modify: `backend/src/main/java/com/codingx/common/support/ai/AiStreamHandler.java`
- Modify: `backend/src/main/java/com/codingx/common/support/ai/OpenAiStyleStreamParser.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/ai/OpenAiCompatibleChatClient.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/ai/DeepSeekOkHttpChatClient.java`
- Test: `backend/src/test/java/com/codingx/support/ai/OpenAiStyleStreamParserTest.java`

- [ ] **Step 1: Write failing parser test**

Add a test feeding SSE `tool_calls` delta and asserting parsed tool name and arguments.

- [ ] **Step 2: Run red test**

Run: `mvn -Dtest=OpenAiStyleStreamParserTest test`
Expected: fails because no tool call callback exists.

- [ ] **Step 3: Implement model structures and parser callback**

Add tool schema to request object, tool call callback to stream handler, parser accumulation for function name and arguments, and request body `tools` emission.

- [ ] **Step 4: Run green test**

Run: `mvn -Dtest=OpenAiStyleStreamParserTest test`
Expected: pass.

### Task 4: Chat Tool-Call Loop

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/domain/port/AiChatClient.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/ai/RoutingAiChatClient.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`
- Test: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationToolCallFlowTest.java`

- [ ] **Step 1: Write failing chat flow test**

Stub AI client first emits `test_sync_tool` call, then emits final answer after tool result is appended.

- [ ] **Step 2: Run red test**

Run: `mvn -Dtest=ChatApplicationToolCallFlowTest test`
Expected: fails because chat flow ignores model tool calls.

- [ ] **Step 3: Implement bounded tool-call loop**

Add AI client method accepting tool specs and tool results; in `ChatApplicationService` execute tool calls through `ChatToolExecutionService` with workspace context and append tool evidence before the next model request.

- [ ] **Step 4: Run green test**

Run: `mvn -Dtest=ChatApplicationToolCallFlowTest test`
Expected: pass.

### Task 5: Database Seed Alignment

**Files:**
- Create: `backend/src/main/resources/db/migration/V20260523_190340__align_codex_local_tool_runtime.sql`
- Modify: `backend/src/main/resources/db/schema.sql`
- Test: `backend/src/test/java/com/codingx/chat/infrastructure/persistence/repository/ChatRuntimePersistenceStructureTest.java`

- [ ] **Step 1: Write failing structure assertion**

Assert tool comments and seed updates include executable/unsupported distinction.

- [ ] **Step 2: Run red test**

Run: `mvn -Dtest=ChatRuntimePersistenceStructureTest test`
Expected: fails because migration does not exist.

- [ ] **Step 3: Add migration and schema seed alignment**

Update tool descriptions/enabled state with Chinese comments preserved.

- [ ] **Step 4: Run green test**

Run: `mvn -Dtest=ChatRuntimePersistenceStructureTest test`
Expected: pass.

### Task 6: Verification And Commit

**Files:**
- Verify all changed backend code and docs.

- [ ] **Step 1: Run focused tests**

Run focused Maven tests for tool executor, parser, schema, chat flow, and persistence structure.

- [ ] **Step 2: Run backend compile**

Run: `mvn compile`
Expected: exit 0.

- [ ] **Step 3: Run backend test**

Run: `mvn test`
Expected: exit 0.

- [ ] **Step 4: Commit**

Run: `git add ... && git commit -m "feat(tool): 接入 Codex 本地工具运行时"`
