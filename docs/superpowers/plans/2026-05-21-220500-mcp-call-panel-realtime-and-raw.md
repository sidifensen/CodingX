# MCP调用面板实时状态与原始信息增强 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 MCP 调用面板在工具开始执行时即时可见，并展示参数/原始结果/元数据而非重复对话文案。

**Architecture:** 后端在同一 MCP 调用中发送 start/complete 两阶段 `mcp-call` 事件；前端基于 `callId` 合并事件并实时更新消息内 `mcpCalls`；渲染层改为展示结构化参数与原始结果。保持旧事件兼容。

**Tech Stack:** Spring Boot + SSE、React + TypeScript、Vitest、JUnit5 + Mockito

---

### Task 1: 先写失败测试（后端 MCP 两阶段事件）

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationMcpFlowTest.java`

- [ ] **Step 1: 新增 start/complete 事件断言（先失败）**

```java
verify(chatStreamPublisher, atLeast(2)).publishMcpCall(eq(1L), argThat(payload -> {
    if (!(payload instanceof Map<?, ?> map)) {
        return false;
    }
    return "start".equals(map.get("phase")) && map.containsKey("callId") && map.containsKey("params");
}));
verify(chatStreamPublisher, atLeast(2)).publishMcpCall(eq(1L), argThat(payload -> {
    if (!(payload instanceof Map<?, ?> map)) {
        return false;
    }
    return "complete".equals(map.get("phase")) && map.containsKey("callId") && map.containsKey("rawResult");
}));
```

- [ ] **Step 2: 运行后端定向测试并确认失败**

Run: `mvn -Dtest=ChatApplicationMcpFlowTest test`
Expected: FAIL，提示 `publishMcpCall` 未包含 `phase=start/complete` 断言

### Task 2: 先写失败测试（前端流式合并与展示）

**Files:**
- Modify: `frontend/user/src/views/chat/useChatWorkspace.test.ts`
- Modify: `frontend/user/src/views/ChatView.test.tsx`

- [ ] **Step 1: 为 useChatWorkspace 新增 start->complete 合并测试（先失败）**

```ts
expect(assistantMessage?.mcpCalls).toHaveLength(1);
expect(assistantMessage?.mcpCalls?.[0].status).toBe('running');
// complete 后仍 1 条，并转为 completed
expect(updatedAssistantMessage?.mcpCalls).toHaveLength(1);
expect(updatedAssistantMessage?.mcpCalls?.[0].status).toBe('completed');
```

- [ ] **Step 2: 为 ChatView 面板新增参数/原始结果/元数据渲染断言（先失败）**

```ts
expect(screen.getByText('参数')).toBeInTheDocument();
expect(screen.getByText('原始结果')).toBeInTheDocument();
expect(screen.getByText('元数据')).toBeInTheDocument();
```

- [ ] **Step 3: 运行前端定向测试并确认失败**

Run: `npm run test:run -- tests/views/chat/useChatWorkspace.test.ts tests/views/ChatView.test.tsx`
Expected: FAIL，当前实现未支持 `callId` 合并与新字段渲染

### Task 3: 实现后端两阶段 MCP 事件

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`

- [ ] **Step 1: 在 MCP 分支生成统一 callId 并发布 start 事件**

```java
Long mcpCallId = IdUtil.getSnowflakeNextId();
LocalDateTime startedAt = LocalDateTime.now();
Map<String, Object> startPayload = new LinkedHashMap<>();
startPayload.put("callId", String.valueOf(mcpCallId));
startPayload.put("phase", "start");
startPayload.put("toolId", intentNode.getMcpToolId());
startPayload.put("displayName", resolveMcpDisplayName(intentNode.getMcpToolId()));
startPayload.put("params", Map.of("question", rewrittenQuestion, "intentCode", intentDecision.intentCode()));
startPayload.put("startedAt", startedAt.toString());
chatStreamPublisher.publishMcpCall(command.conversationId(), startPayload);
```

- [ ] **Step 2: 工具执行后发布 complete 事件（保留兼容字段）**

```java
Map<String, Object> completePayload = new LinkedHashMap<>();
completePayload.put("callId", String.valueOf(mcpCallId));
completePayload.put("phase", "complete");
completePayload.put("toolId", toolResult.toolId());
completePayload.put("displayName", resolveMcpDisplayName(toolResult.toolId()));
completePayload.put("params", Map.of("question", rewrittenQuestion, "intentCode", intentDecision.intentCode()));
completePayload.put("rawResult", toolResult.content());
completePayload.put("resultMetadata", toolResult.metadata() == null ? Map.of() : toolResult.metadata());
completePayload.put("finishedAt", LocalDateTime.now().toString());
// 兼容旧前端字段
completePayload.put("input", rewrittenQuestion);
completePayload.put("content", toolResult.content());
completePayload.put("metadata", toolResult.metadata() == null ? Map.of() : toolResult.metadata());
chatStreamPublisher.publishMcpCall(command.conversationId(), completePayload);
```

- [ ] **Step 3: 运行后端定向测试确保通过**

Run: `mvn -Dtest=ChatApplicationMcpFlowTest test`
Expected: PASS

### Task 4: 实现前端事件合并与类型扩展

**Files:**
- Modify: `frontend/user/src/views/chat/types.ts`
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`

- [ ] **Step 1: 扩展 McpCallItem 类型（兼容旧字段）**

```ts
export interface McpCallItem {
  callId?: string;
  toolId: string;
  displayName: string;
  input: string;
  content: string;
  metadata?: Record<string, unknown>;
  phase?: 'start' | 'complete' | 'error';
  status?: 'running' | 'completed' | 'error';
  params?: Record<string, unknown> | string;
  rawResult?: unknown;
  resultMetadata?: Record<string, unknown>;
  startedAt?: string;
  finishedAt?: string;
  errorMessage?: string;
}
```

- [ ] **Step 2: 增加 `mcp-call` 合并逻辑（按 callId upsert）**

```ts
function mergeMcpCallEvent(calls: McpCallItem[], incoming: McpCallItem): McpCallItem[] {
  if (!incoming.callId) {
    return [...calls, incoming];
  }
  const index = calls.findIndex((item) => item.callId === incoming.callId);
  if (index < 0) {
    return [...calls, incoming];
  }
  const merged = { ...calls[index], ...incoming };
  return calls.map((item, i) => (i === index ? merged : item));
}
```

- [ ] **Step 3: 收到 `phase=start` 时即时写入消息 `mcpCalls`**

```ts
const normalizedPhase = resolveMcpCallPhase(payload);
const call = buildMcpCallFromPayload(payload, normalizedPhase);
setMessages((previous) => previous.map((message) =>
  message.id === optimisticAssistantId
    ? { ...message, mcpCalls: mergeMcpCallEvent(message.mcpCalls ?? [], call) }
    : message,
));
```

- [ ] **Step 4: 运行前端 Hook 测试确保通过**

Run: `npm run test:run -- tests/views/chat/useChatWorkspace.test.ts`
Expected: PASS

### Task 5: 实现 MCP 面板展示改造

**Files:**
- Modify: `frontend/user/src/views/ChatView.tsx`

- [ ] **Step 1: 调整面板全局状态优先依据调用项 status**

```ts
const hasRunningCall = calls.some((call) => call.status === 'running');
const hasErrorCall = calls.some((call) => call.status === 'error');
const targetStatus = hasRunningCall || messageStatus === 'streaming'
  ? 'running'
  : hasErrorCall
    ? 'error'
    : 'completed';
```

- [ ] **Step 2: 用统一格式化函数渲染参数/原始结果/元数据**

```ts
function formatStructuredPayload(value: unknown): string {
  if (value == null) return '';
  if (typeof value === 'string') return value;
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}
```

```tsx
{parameterText ? (
  <div>参数 ...</div>
) : null}
{rawResultText ? (
  <div>原始结果 ...</div>
) : null}
{metadataText ? (
  <div>元数据 ...</div>
) : null}
```

- [ ] **Step 3: 运行 ChatView 定向测试确保通过**

Run: `npm run test:run -- tests/views/ChatView.test.tsx`
Expected: PASS

### Task 6: 联合验证与提交

**Files:**
- Modify: `docs/superpowers/specs/2026-05-21-220500-mcp-call-panel-realtime-and-raw-design.md`
- Modify: `docs/superpowers/acceptance/2026-05-21-220500-mcp-call-panel-realtime-and-raw-acceptance.md`
- Modify: `docs/superpowers/plans/2026-05-21-220500-mcp-call-panel-realtime-and-raw.md`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationMcpFlowTest.java`
- Modify: `frontend/user/src/views/chat/types.ts`
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`
- Modify: `frontend/user/src/views/chat/useChatWorkspace.test.ts`
- Modify: `frontend/user/src/views/ChatView.tsx`
- Modify: `frontend/user/src/views/ChatView.test.tsx`

- [ ] **Step 1: 执行改动面最小回归测试**

Run: `cd backend && mvn -Dtest=ChatApplicationMcpFlowTest test`
Expected: PASS

Run: `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.test.ts tests/views/ChatView.test.tsx`
Expected: PASS

- [ ] **Step 2: 提交代码（中文前缀）**

```bash
git add docs/superpowers/specs/2026-05-21-220500-mcp-call-panel-realtime-and-raw-design.md \
        docs/superpowers/acceptance/2026-05-21-220500-mcp-call-panel-realtime-and-raw-acceptance.md \
        docs/superpowers/plans/2026-05-21-220500-mcp-call-panel-realtime-and-raw.md \
        backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java \
        backend/src/test/java/com/codingx/chat/application/service/ChatApplicationMcpFlowTest.java \
        frontend/user/src/views/chat/types.ts \
        frontend/user/src/views/chat/useChatWorkspace.ts \
        frontend/user/src/views/chat/useChatWorkspace.test.ts \
        frontend/user/src/views/ChatView.tsx \
        frontend/user/src/views/ChatView.test.tsx

git commit -m "feat: 增强MCP调用面板实时状态与原始信息展示"
```
