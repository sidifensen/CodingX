# Chat Tool Narration Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop internal tool execution narration from being streamed or reused as model history when a tool call arrives later in the same model round.

**Architecture:** Keep the existing `ChatApplicationService` tool-loop buffer, but expand its process-text classification from short progress phrases to long execution narration. Sanitize model-visible history only, leaving persisted UI history unchanged.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, Maven.

---

### Task 1: Reproduce Leaked Long Tool Narration

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationToolCallFlowTest.java`

- [x] **Step 1: Write the failing test**

Add `sendMessageSuppressesLongToolNarrationBeforeLaterToolCall`, where round 2 streams “我们先确认/下一步行动/现在执行” before emitting a later `read` tool call.

- [x] **Step 2: Run test to verify it fails**

Run: `mvn "-Dtest=ChatApplicationToolCallFlowTest#sendMessageSuppressesLongToolNarrationBeforeLaterToolCall" test`

Expected before implementation: failure because `publishAssistantDelta` receives the internal narration.

### Task 2: Keep Long Narration Buffered

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`

- [x] **Step 1: Implement minimal classifier**

Add `isExecutionNarrationOnlyToolRoundContent` and call it from both final progress detection and possible-progress buffering.

- [x] **Step 2: Run regression**

Run: `mvn "-Dtest=ChatApplicationToolCallFlowTest#sendMessageSuppressesLongToolNarrationBeforeLaterToolCall" test`

Expected after implementation: pass.

### Task 3: Filter Persisted Pollution From Model History

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationToolCallFlowTest.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`

- [x] **Step 1: Write the failing test**

Add `sendMessageFiltersPersistedToolNarrationFromModelHistory`, where a previous assistant message already contains internal execution narration and a new “同意” user message is sent.

- [x] **Step 2: Implement history sanitizer**

Add `modelVisibleAiMessages` and filter assistant messages identified by `isPersistedInternalToolNarration` before summary/model history construction.

- [x] **Step 3: Run regression**

Run the focused `ChatApplicationToolCallFlowTest` method set covering long narration, persisted history pollution, final answer streaming, split progress hiding, command-plan hiding, and repeated write convergence.

### Task 4: Document And Verify

**Files:**
- Modify: `docs/features/chat/local-tool-runtime.md`
- Create: `docs/superpowers/plans/2026-06-08-110610-chat-tool-narration-loop.md`

- [x] **Step 1: Update feature documentation**

Document the long narration suppression and model-visible history filtering contract.

- [ ] **Step 2: Run final verification**

Run `mvn compile`, focused backend tests, and desktop smoke verification through the app.
