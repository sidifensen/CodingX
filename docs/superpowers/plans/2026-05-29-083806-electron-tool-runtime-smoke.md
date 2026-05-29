# Electron Tool Runtime Smoke Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 验证 Electron 本地环境可触发的 Codex 内置工具都能被后端真实调用，发现报错后补测试并修复。

**Architecture:** 以 `CodexBuiltinChatToolExecutor` 为工具执行真实入口，先用单元烟测覆盖全部注册工具的代表性输入，再通过后端 HTTP 接口和 Electron 宿主启动做集成验证。测试临时 workspace 内只写入测试文件，避免污染用户仓库和其他会话改动。

**Tech Stack:** Spring Boot, Java 21, JUnit 5, Mockito, Electron, TypeScript.

---

### Task 1: 全工具后端烟测

**Files:**
- Modify: `backend/src/test/java/com/codingx/tool/application/service/CodexBuiltinChatToolExecutorTest.java`

- [ ] **Step 1: Write the failing test**

Add `allRegisteredToolsShouldAcceptRepresentativeElectronSmokeInputs` with a temporary workspace. The test calls each `toolCodes()` entry with a representative payload:

```java
ChatToolExecutionContext.bindToolWorkingDirectory(projectRoot);
try {
    ChatToolExecutionResult shellResult = codexBuiltinChatToolExecutor.execute(
        "shell_command",
        "{\"command\":\"Set-Content -Path smoke-file.txt -Value electron-smoke\",\"timeoutMs\":10000}"
    );
    ChatToolExecutionResult patchResult = codexBuiltinChatToolExecutor.execute(
        "apply_patch",
        JSONUtil.toJsonStr(Map.of("patch", htmlPatch))
    );
    assertEquals(0, shellResult.metadata().get("exitCode"));
    assertTrue(Files.exists(projectRoot.resolve("smoke-file.txt")));
    assertTrue(Files.exists(projectRoot.resolve("smoke.html")));
    assertEquals(expectedToolCodes, invokedToolCodes);
} finally {
    ChatToolExecutionContext.clear();
}
```

- [ ] **Step 2: Run the red check**

Run: `mvn -Dtest=CodexBuiltinChatToolExecutorTest#allRegisteredToolsShouldAcceptRepresentativeElectronSmokeInputs test`

Expected: if any registered tool rejects realistic input, the test fails with the exact tool code and exception.

- [ ] **Step 3: Fix only failing tool behavior**

If the smoke test fails, patch the minimal tool-specific parser or runtime branch in `CodexBuiltinChatToolExecutor`. Preserve existing comments and add comments only for non-obvious constraints.

- [ ] **Step 4: Run the green check**

Run: `mvn -Dtest=CodexBuiltinChatToolExecutorTest test`

Expected: all tool executor tests pass.

### Task 2: 后端接口与 Electron 宿主验证

**Files:**
- Verify: `frontend/desktop/src/main.ts`
- Verify: `frontend/desktop/src/preload.ts`
- Verify: `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatToolController.java`

- [ ] **Step 1: Build Electron host**

Run: `npm run build` in `frontend/desktop`.

Expected: TypeScript compiles without errors.

- [ ] **Step 2: Launch Electron with the driver**

Use the Electron driver against `frontend/desktop/dist/main.js`. Verify `window.codingxHost` exists, `getContext()` returns `hostType=desktop`, and directory listing works against a temporary folder.

- [ ] **Step 3: Start backend**

Run: `mvn spring-boot:run` from `backend` after checking port `5001`.

Expected: backend starts on `http://localhost:5001` or reports a clear environment/database blocker.

- [ ] **Step 4: Invoke representative HTTP tools**

Call `/api/chat/tools/{toolCode}/invoke` for safe user-facing tools where authentication and enabled-state allow it. If auth blocks direct HTTP invocation, record the blocker and rely on service-level tests for execution correctness.

### Task 3: Final verification and commit

**Files:**
- Verify all files changed by this task only.

- [ ] **Step 1: Run focused backend tests**

Run: `mvn -Dtest=CodexBuiltinChatToolExecutorTest test`

Expected: exit code 0.

- [ ] **Step 2: Run required backend checks**

Run: `mvn compile` and `mvn test`.

Expected: exit code 0 unless failures are clearly caused by unrelated existing workspace changes.

- [ ] **Step 3: Run Electron build**

Run: `npm run build` in `frontend/desktop`.

Expected: exit code 0.

- [ ] **Step 4: Commit current task changes**

Stage only this task's files and commit with a Chinese message:

```bash
git add docs/superpowers/plans/2026-05-29-083806-electron-tool-runtime-smoke.md backend/src/test/java/com/codingx/tool/application/service/CodexBuiltinChatToolExecutorTest.java
git commit -m "test(tool): 补充 Electron 工具运行时烟测"
```
