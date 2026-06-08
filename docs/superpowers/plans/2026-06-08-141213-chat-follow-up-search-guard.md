# Chat Follow-up Search Guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent code-generation follow-up edits like “丰富一下” from accidentally invoking web search.

**Architecture:** Keep the guard in `ChatApplicationService`, where search decisions are already normalized before execution. The guard rewrites only the local `SubQuestionIntentDecision` from `SEARCH` to `DIRECT` when the current short instruction is an edit/follow-up command and the history contains code or file artifact signals.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, Hutool.

---

### Task 1: Add Failing Regression Test

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationSearchFlowTest.java`

- [ ] **Step 1: Write the failing test**

Add a test named `sendMessageDoesNotInvokeSearchForCodeArtifactFollowUpShortEdit` that:

```java
when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>(List.of(
    ChatMessage.create(11L, 1L, ChatMessageRole.USER, "写个好看的html", ChatMessageStatus.COMPLETED, null, null, null),
    ChatMessage.create(12L, 1L, ChatMessageRole.ASSISTANT, "文件已写入：D:/code/test/index.html", ChatMessageStatus.COMPLETED, null, null, null)
)));
when(conversationRewriteService.rewriteResult(any(), eq("丰富一下"))).thenReturn(
    new ConversationRewriteResult("丰富一下", false, List.of("丰富一下"))
);
when(conversationIntentService.route("丰富一下", false)).thenReturn(
    new ConversationIntentDecision("search-general", ConversationIntentAction.SEARCH, null)
);
```

Then assert `webSearchExecutionService.search(any())`, `searchReferenceCollector.collect(...)`, and `documentArtifactService.createDocxArtifact(...)` are never called, while the assistant completion is published.

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend
mvn -Dtest=ChatApplicationSearchFlowTest#sendMessageDoesNotInvokeSearchForCodeArtifactFollowUpShortEdit test
```

Expected before implementation: FAIL because `webSearchExecutionService.search("丰富一下")` is invoked.

### Task 2: Implement Search Guard

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`

- [ ] **Step 1: Add decision normalization call**

In `sendMessage(...)` and `processRegeneratedMessage(...)`, apply the new guard after `resolveSubQuestionDecisions(...)` and before `suppressAutomaticSearchDecisions(...)`, passing original user input and current history.

- [ ] **Step 2: Add focused helper methods**

Add helpers with comments explaining business intent:

```java
private List<SubQuestionIntentDecision> suppressCodeArtifactFollowUpSearchDecisions(
    List<SubQuestionIntentDecision> decisions,
    String originalQuestion,
    List<ChatMessage> history
)
```

The helper should:
- return unchanged decisions when empty;
- skip non-`SEARCH` decisions;
- skip explicit search/current-info requests;
- only downgrade when `isCodeArtifactFollowUpQuestion(...)` and `historyHasCodeArtifactSignal(...)` are both true;
- log the downgrade with original intent and question preview.

- [ ] **Step 3: Keep matching rules narrow**

Use normalized Chinese/English text helpers. Short follow-up commands include `丰富一下`、`完善一下`、`优化一下`、`继续优化`、`再丰富一下`、`扩展一下`、`增强一下`、`美化一下`、`改好看点`、`加点内容`、`继续`。 Explicit search terms include `搜索`、`搜一下`、`查询`、`查一下`、`最新`、`今天`、`当前`、`联网`、`网页`、`新闻`、`资料`、`版本`。

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
cd backend
mvn -Dtest=ChatApplicationSearchFlowTest#sendMessageDoesNotInvokeSearchForCodeArtifactFollowUpShortEdit test
```

Expected after implementation: PASS.

### Task 3: Update Feature Documentation

**Files:**
- Create: `docs/features/chat/follow-up-search-guard.md`
- Modify: `docs/features/index.md`

- [ ] **Step 1: Document current behavior**

Document feature purpose, entry, core flow, key files, edge cases, and verification commands.

- [ ] **Step 2: Add index entry**

Add `聊天代码产物续写搜索保护` under the Chat section.

### Task 4: Verify

- [ ] **Step 1: Run focused search flow suite**

```bash
cd backend
mvn -Dtest=ChatApplicationSearchFlowTest test
```

Expected: PASS.

- [ ] **Step 2: Run backend compile**

```bash
cd backend
mvn compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Run backend tests**

```bash
cd backend
mvn test
```

Expected: BUILD SUCCESS.

### Task 5: Commit

**Files to stage only:**
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`
- `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationSearchFlowTest.java`
- `docs/features/index.md`
- `docs/features/chat/follow-up-search-guard.md`
- `docs/superpowers/specs/2026-06-08-141213-chat-follow-up-search-guard-design.md`
- `docs/superpowers/acceptance/2026-06-08-141213-chat-follow-up-search-guard-acceptance.md`
- `docs/superpowers/plans/2026-06-08-141213-chat-follow-up-search-guard.md`

Commit:

```bash
git add <listed files>
git commit -m "fix(chat): 修复代码续写误触发网页搜索"
```
