# 聊天实时代理流式过程 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让聊天助手在工具调用前后保留真实正文、真实 thinking 和工具过程，并按 SSE 到达顺序在同一条消息中穿插展示。

**Architecture:** 后端保留工具调用循环，但把最终正文缓冲改为跨轮累积，不再在工具调用后清空已发布的正文与 thinking。前端沿用 `timelineItems`，重点补齐 `finish` 收口和渲染顺序测试，确保最终事件不重排或覆盖已到达片段。

**Tech Stack:** Spring Boot 3.4 / Java 21 / JUnit 5 / Mockito；React 19 / TypeScript / Vitest；Hutool 用于后端字符串与 JSON 辅助。

---

## File Map

- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`
  - 负责工具调用循环、正文和 thinking 缓冲、工具结果回灌、最终 assistant 消息保存。
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationToolCallFlowTest.java`
  - 增加工具前正文、工具后正文、thinking 保留、工具失败保留缓冲的后端单测。
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`
  - 只在现有逻辑不足时调整 `finish` 收口与时间线补齐；实施前必须先保留当前未提交的“工具事件不伪造 analysis”改动。
- Modify: `frontend/user/tests/views/chat/useChatWorkspace.test.ts`
  - 增加或补强 `message -> tool-call -> message -> finish` 和无 thinking 不伪造 analysis 的 Hook 测试。
- Modify: `frontend/user/src/views/ChatView.tsx`
  - 仅当现有渲染测试发现顺序不正确时调整渲染层。
- Modify: `frontend/user/tests/views/ChatView.test.tsx`
  - 补强 DOM 顺序断言。
- Modify: `docs/features/chat/interleaved-process-timeline.md`
  - 更新功能文档，记录工具前正文保留、工具后正文追加、finish 不覆盖时间线。

## Preflight: Protect Existing Work

- [ ] **Step 1: Inspect dirty files before editing**

Run:

```bash
git status --short
git diff -- backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java backend/src/test/java/com/codingx/chat/application/service/ChatApplicationToolCallFlowTest.java frontend/user/src/views/chat/useChatWorkspace.ts frontend/user/tests/views/chat/useChatWorkspace.test.ts frontend/user/src/views/ChatView.tsx frontend/user/tests/views/ChatView.test.tsx
```

Expected: Output may include existing unrelated changes. Treat them as other-session work; do not revert, reformat, or restage unrelated hunks.

---

### Task 1: Backend Tool Loop Keeps Pre-Tool Content

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationToolCallFlowTest.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`

- [ ] **Step 1: Write the failing backend test for content before and after a tool call**

Add this test method to `ChatApplicationToolCallFlowTest` near `sendMessageExecutesModelToolCallAndContinuesWithToolEvidence`:

```java
/**
 * 模型在工具调用前已经说出的正文必须保留，工具结果回灌后继续追加后续正文。
 */
@Test
void sendMessagePreservesContentBeforeAndAfterModelToolCall(@TempDir Path tempDir) throws Exception {
    Long runId = 9401005L;
    ChatExecutionContext.start(runId);
    Path workspace = tempDir.resolve("repo");
    Files.createDirectories(workspace);
    ChatConversation conversation = ChatConversation.create(5L, "Live Tool Stream", 1002L, ChatConversationStatus.ACTIVE);
    when(chatConversationRepository.requireById(5L)).thenReturn(conversation);
    when(chatMessageRepository.findByConversationId(5L)).thenReturn(new ArrayList<>());
    when(chatAttachmentService.requireOwnedAttachments(any(), eq(5L), eq(1002L))).thenReturn(List.of());
    when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
        new ConversationRewriteResult("检查目录后继续说明", false, List.of("检查目录后继续说明"))
    );
    when(conversationIntentService.route("检查目录后继续说明", false)).thenReturn(
        new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
    );
    when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
    when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
    when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
    when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    when(conversationTitleService.generateTitle(any(), any())).thenReturn("实时工具过程");
    when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
        new ChatToolSpec("test_sync_tool", "同步测试工具", Map.of("type", "object"))
    ));
    when(chatToolExecutionService.execute(eq("test_sync_tool"), eq("{\"message\":\"pwd\"}"))).thenReturn(
        new ChatToolExecutionResult("test_sync_tool", "D:/code/CodingX", Map.of("exitCode", 0))
    );
    AtomicInteger modelRound = new AtomicInteger();
    doAnswer(invocation -> {
        AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
        if (modelRound.incrementAndGet() == 1) {
            handler.onDelta("我先检查当前目录。\n\n");
            handler.onToolCall(new AiToolCall("call-live-1", "test_sync_tool", "{\"message\":\"pwd\"}"));
            handler.onComplete();
            return null;
        }
        handler.onDelta("目录确认后，我继续说明结果。");
        handler.onComplete();
        return null;
    }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

    chatApplicationService.sendMessage(
        new SendChatMessageCommand(5L, "检查目录后继续说明", false, List.of(), List.of(), null, workspace.toString(), List.of()),
        1002L
    );

    verify(chatStreamPublisher).publishAssistantDelta(5L, "我先检查当前目录。\n\n");
    verify(chatStreamPublisher).publishAssistantDelta(5L, "目录确认后，我继续说明结果。");
    verify(chatStreamPublisher).publishAssistantCompleted(
        5L,
        "我先检查当前目录。\n\n目录确认后，我继续说明结果。",
        "实时工具过程"
    );
    ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
    verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
    ChatMessage assistantMessage = messageCaptor.getAllValues().get(1);
    assertEquals("我先检查当前目录。\n\n目录确认后，我继续说明结果。", assistantMessage.getContent());
    ChatExecutionContext.clear();
}
```

- [ ] **Step 2: Run the failing backend test**

Run:

```bash
cd backend
mvn -Dtest=ChatApplicationToolCallFlowTest#sendMessagePreservesContentBeforeAndAfterModelToolCall test
```

Expected: FAIL because current `runAiToolAwareLoop()` clears `builder` after tool calls, so final content only contains the second-round text.

- [ ] **Step 3: Implement minimal backend fix**

In `ChatApplicationService.runAiToolAwareLoop()`, remove the post-tool-call reset of the user-visible content buffer. Keep the thinking reset only if tests confirm thinking should be per-round; otherwise Task 2 will adjust it.

Replace this block:

```java
builder.setLength(0);
thinkingBuilder.setLength(0);
// 工具重入会开启下一轮模型生成，思考起点必须重新计时，避免把上一轮时长混进来。
thinkingStartedAt.set(null);
```

with:

```java
// 工具重入后模型会继续追加正文；已发布给用户的正文不能清空，否则最终保存和 finish 会丢失工具前说明。
```

If `thinkingStartedAt` is still needed for duration, do not reset it here. The first thinking 到达时间 should represent the whole assistant answer.

- [ ] **Step 4: Run the backend test again**

Run:

```bash
cd backend
mvn -Dtest=ChatApplicationToolCallFlowTest#sendMessagePreservesContentBeforeAndAfterModelToolCall test
```

Expected: PASS.

---

### Task 2: Backend Thinking and Tool Failure Retain Existing Stream Buffers

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationToolCallFlowTest.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`

- [ ] **Step 1: Write the failing test for thinking retention across tool calls**

Add this test method:

```java
/**
 * 深度思考内容是模型真实返回的 reasoning，工具回灌轮次不能清空已收到的 thinking。
 */
@Test
void sendMessagePreservesThinkingBeforeModelToolCall(@TempDir Path tempDir) throws Exception {
    Long runId = 9401006L;
    ChatExecutionContext.start(runId);
    Path workspace = tempDir.resolve("repo");
    Files.createDirectories(workspace);
    ChatConversation conversation = ChatConversation.create(6L, "Thinking Tools", 1002L, ChatConversationStatus.ACTIVE);
    when(chatConversationRepository.requireById(6L)).thenReturn(conversation);
    when(chatMessageRepository.findByConversationId(6L)).thenReturn(new ArrayList<>());
    when(chatAttachmentService.requireOwnedAttachments(any(), eq(6L), eq(1002L))).thenReturn(List.of());
    when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
        new ConversationRewriteResult("深度思考后调用工具", false, List.of("深度思考后调用工具"))
    );
    when(conversationIntentService.route("深度思考后调用工具", false)).thenReturn(
        new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
    );
    when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
    when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
    when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
    when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    when(conversationTitleService.generateTitle(any(), any())).thenReturn("思考工具过程");
    when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
        new ChatToolSpec("test_sync_tool", "同步测试工具", Map.of("type", "object"))
    ));
    when(chatToolExecutionService.execute(eq("test_sync_tool"), eq("{\"message\":\"inspect\"}"))).thenReturn(
        new ChatToolExecutionResult("test_sync_tool", "工具观察结果", Map.of("ok", true))
    );
    AtomicInteger modelRound = new AtomicInteger();
    doAnswer(invocation -> {
        AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
        if (modelRound.incrementAndGet() == 1) {
            handler.onThinkingDelta("先判断是否需要工具。");
            handler.onDelta("我先确认一下信息。\n\n");
            handler.onToolCall(new AiToolCall("call-thinking-1", "test_sync_tool", "{\"message\":\"inspect\"}"));
            handler.onComplete();
            return null;
        }
        handler.onThinkingDelta("根据工具结果继续分析。");
        handler.onDelta("工具结果已经确认。");
        handler.onComplete();
        return null;
    }).when(aiChatClient).streamChatWithTools(any(), eq(true), any(), any());

    chatApplicationService.sendMessage(
        new SendChatMessageCommand(6L, "深度思考后调用工具", true, List.of(), List.of(), null, workspace.toString(), List.of()),
        1002L
    );

    verify(chatStreamPublisher).publishAssistantThinkingDelta(6L, "先判断是否需要工具。");
    verify(chatStreamPublisher).publishAssistantThinkingDelta(6L, "根据工具结果继续分析。");
    ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
    verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
    ChatMessage assistantMessage = messageCaptor.getAllValues().get(1);
    assertEquals("先判断是否需要工具。根据工具结果继续分析。", assistantMessage.getThinkingContent());
    ChatExecutionContext.clear();
}
```

- [ ] **Step 2: Run the failing thinking test**

Run:

```bash
cd backend
mvn -Dtest=ChatApplicationToolCallFlowTest#sendMessagePreservesThinkingBeforeModelToolCall test
```

Expected: FAIL if `thinkingBuilder` is still cleared between tool rounds.

- [ ] **Step 3: Implement minimal thinking fix**

If Task 1 did not already remove `thinkingBuilder.setLength(0)`, remove it now. Leave `applyThinkingRuntimeState()` unchanged so it persists the combined thinking content.

- [ ] **Step 4: Extend existing tool failure test**

In `sendMessageRecordsFailureWhenModelToolExecutionFails`, make the first model round emit visible content before the failing tool:

```java
handler.onThinkingDelta("先判断工具可用性。");
handler.onDelta("我先尝试调用工具。\n\n");
handler.onToolCall(new AiToolCall("call-failed", "spawn_agent", "{\"message\":\"x\"}"));
handler.onComplete();
```

Then add assertions after the failed message is captured:

```java
assertEquals("我先尝试调用工具。\n\n", failedMessage.getContent());
assertEquals("先判断工具可用性。", failedMessage.getThinkingContent());
```

Expected: The existing test may need its old `assertEquals("AI 回复失败", failedMessage.getContent())` replaced by the new assertion.

- [ ] **Step 5: Run the backend tool flow test class**

Run:

```bash
cd backend
mvn -Dtest=ChatApplicationToolCallFlowTest test
```

Expected: PASS.

---

### Task 3: Frontend Finish Keeps Timeline Order

**Files:**
- Modify: `frontend/user/tests/views/chat/useChatWorkspace.test.ts`
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`

- [ ] **Step 1: Write or confirm the failing Hook test for finish preservation**

If `useChatWorkspace.test.ts` does not already assert finish preservation after a `message -> tool-call -> tool-call -> message` sequence, add this assertion to the existing `按流式事件顺序记录正文与工具过程时间线` test after the `finishEvent` and before stream completion:

```ts
await waitFor(() => {
  const assistantMessage = result.current.messages.find((item) => item.role === 'ASSISTANT');
  const timelineItems = ((assistantMessage as Record<string, unknown> | undefined)?.timelineItems ?? []) as Array<Record<string, unknown>>;
  expect(assistantMessage?.content).toBe('我先检查当前目录。\n\n我再根据结果继续分析。');
  expect(timelineItems.map((item) => item.type)).toEqual([
    'content',
    'process',
    'process',
    'content',
  ]);
  expect(timelineItems[0].content).toBe('我先检查当前目录。\n\n');
  expect((timelineItems[1].card as Record<string, unknown>).id).toBe('tool-call-timeline-call-1');
  expect((timelineItems[2].card as Record<string, unknown>).id).toBe('tool-result-timeline-call-1');
  expect(timelineItems[3].content).toBe('我再根据结果继续分析。');
});
```

- [ ] **Step 2: Run the focused frontend Hook test**

Run:

```bash
cd frontend/user
npm run test:run -- tests/views/chat/useChatWorkspace.test.ts -t "按流式事件顺序记录正文与工具过程时间线"
```

Expected: PASS if current `ensureTimelineContent()` already protects the timeline; FAIL if finish overwrites or duplicates content.

- [ ] **Step 3: Implement minimal frontend fix if needed**

If the test fails, keep `ensureTimelineContent()` with this behavior:

```ts
function ensureTimelineContent(
  items: MessageTimelineItem[] | undefined,
  content: string,
): MessageTimelineItem[] | undefined {
  if (!content) {
    return items;
  }
  const timelineContent = (items ?? [])
    .filter((item): item is Extract<MessageTimelineItem, { type: 'content' }> => item.type === 'content')
    .map((item) => item.content)
    .join('');
  if (!timelineContent) {
    return appendContentToTimeline(items, content);
  }
  if (content.length <= timelineContent.length || !content.startsWith(timelineContent)) {
    return items;
  }
  return appendContentToTimeline(items, content.slice(timelineContent.length));
}
```

Do not reintroduce an `analysis` process card from `reactThought`; existing dirty diff intentionally prevents backend template text from appearing as model thinking.

- [ ] **Step 4: Run local frontend Hook tests for process timeline**

Run:

```bash
cd frontend/user
npm run test:run -- tests/views/chat/useChatWorkspace.test.ts -t "tool-call|时间线|finish"
```

Expected: PASS.

---

### Task 4: Frontend Render Order and No Fake Thinking

**Files:**
- Modify: `frontend/user/tests/views/ChatView.test.tsx`
- Modify: `frontend/user/src/views/ChatView.tsx` only if test fails

- [ ] **Step 1: Confirm render-order test covers content -> tool -> content**

Use or add this assertion in `ChatView.test.tsx` inside `按时间线穿插渲染助手正文与过程节点`:

```ts
const messageShell = screen.getByTestId('assistant-message-body-969');
const messageText = messageShell.textContent ?? '';
expect(messageText.indexOf('我先检查当前目录。')).toBeLessThan(
  messageText.indexOf('调用 shell_command'),
);
expect(messageText.indexOf('调用 shell_command')).toBeLessThan(
  messageText.indexOf('我再根据结果继续分析。'),
);
expect(screen.getAllByTestId('process-tool-row-969-tool-call-shell-969')).toHaveLength(1);
```

- [ ] **Step 2: Run focused render test**

Run:

```bash
cd frontend/user
npm run test:run -- tests/views/ChatView.test.tsx -t "按时间线穿插渲染助手正文与过程节点"
```

Expected: PASS. If it fails, update only `AssistantMessageBody` / timeline rendering so it maps `timelineItems` in order before falling back to `processCards + content`.

- [ ] **Step 3: Confirm no fake thinking from `reactThought`**

Run:

```bash
cd frontend/user
npm run test:run -- tests/views/chat/useChatWorkspace.test.ts -t "merges tool-call events into local tool process cards"
```

Expected: PASS and assertions show no `analysis` card created from `reactThought`.

---

### Task 5: Feature Documentation

**Files:**
- Modify: `docs/features/chat/interleaved-process-timeline.md`

- [ ] **Step 1: Update feature doc**

Add these points to the existing “核心流程” or “关键数据结构” section:

```markdown
6. 工具模式下，后端保留工具调用前已经发布的正文和 thinking；工具结果回灌后，后续模型正文继续追加到同一条助手消息。
7. `finish` 事件只做状态收口和缺失尾段补齐，不重建已到达的时间线，避免工具前正文在流结束后丢失。
8. 工具事件中的 `reactThought` 只作为工具阶段元数据，不等价于模型真实 thinking；前端只有收到 `thinking` SSE 时才展示深度思考内容。
```

- [ ] **Step 2: Check feature index**

Run:

```bash
rg -n "助手消息过程时间线穿插展示|interleaved-process-timeline" docs/features/index.md docs/features/chat/interleaved-process-timeline.md
```

Expected: Existing index already points to the feature doc. Do not edit `docs/features/index.md` unless the link is missing.

---

### Task 6: Verification and Commit

**Files:**
- All modified files from Tasks 1-5

- [ ] **Step 1: Run backend focused tests**

Run:

```bash
cd backend
mvn -Dtest=ChatApplicationToolCallFlowTest test
```

Expected: PASS.

- [ ] **Step 2: Run frontend focused tests**

Run:

```bash
cd frontend/user
npm run test:run -- tests/views/chat/useChatWorkspace.test.ts -t "tool-call|时间线|finish"
npm run test:run -- tests/views/ChatView.test.tsx -t "按时间线穿插渲染助手正文与过程节点"
```

Expected: PASS.

- [ ] **Step 3: Run required full validation**

Run:

```bash
cd backend
mvn compile
mvn test
cd ../frontend/user
npm run build
npm run test:run
```

Expected: All commands PASS. If existing unrelated dirty changes make a broad suite fail, capture the failing test names and prove the focused tests for this feature pass.

- [ ] **Step 4: Browser verification if render code changed**

If `frontend/user/src/views/ChatView.tsx` or visible styles changed, start services after checking ports 5001 and 5002, then use CDP via `/web-access` to open `http://localhost:5002`, reproduce a chat stream with an interleaved timeline fixture or real tool call, and save a screenshot under `logs/`.

Expected: Screenshot shows assistant text before a tool node, then text after the tool node, with readable dark-mode styling.

- [ ] **Step 5: Stage only relevant hunks**

Because the worktree already contains unrelated dirty files, do not run broad `git add .`. Stage only files changed for this plan, and if a file contains unrelated pre-existing hunks, use a non-interactive patch or split commit strategy that does not stage unrelated hunks.

Run:

```bash
git diff -- backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java backend/src/test/java/com/codingx/chat/application/service/ChatApplicationToolCallFlowTest.java frontend/user/src/views/chat/useChatWorkspace.ts frontend/user/tests/views/chat/useChatWorkspace.test.ts frontend/user/src/views/ChatView.tsx frontend/user/tests/views/ChatView.test.tsx docs/features/chat/interleaved-process-timeline.md
git status --short
```

Expected: Diff contains only this feature's changes plus any pre-existing hunks that must remain unstaged.

- [ ] **Step 6: Commit with Chinese message**

Run only after staging exactly the relevant changes:

```bash
git commit -m "fix(chat): 保留工具调用前后的流式正文"
```

Expected: Commit succeeds. If unrelated pre-existing changes in the same files cannot be separated safely, do not commit implementation; report the exact conflicting files and staged state.
