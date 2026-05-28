# Task Artifact Merge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `artifact` 相关类并入 `task` 模块，保持 `task_artifact` 表和任务执行产物能力不变，同时修正读库时领域对象恢复会丢失主键的问题。

**Architecture:** `TaskArtifact` 归入 `com.codingx.task.domain`，持久化映射归入 `com.codingx.task.infrastructure.persistence`，运行时执行器只更新 import。仓储查询从数据库恢复对象时使用显式 `restore` 方法，避免 `create` 重新生成 ID 和创建时间。

**Tech Stack:** Java 21, Spring Boot, MyBatis-Plus, Hutool, JUnit 5, Mockito

---

### Task 1: Add regression coverage for artifact restoration

**Files:**
- Modify: `backend/src/test/java/com/codingx/common/persistence/TableNameMappingTest.java`
- Create: `backend/src/test/java/com/codingx/task/infrastructure/persistence/repository/TaskArtifactRepositoryImplTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void restoreArtifactPreservesDatabaseIdAndCreatedAt() {
    TaskArtifactDO dataObject = new TaskArtifactDO();
    dataObject.setId(88L);
    dataObject.setTaskId(11L);
    dataObject.setArtifactType("summary");
    dataObject.setName("summary.txt");
    dataObject.setContent("done");
    dataObject.setStoragePath("artifacts/11/summary.txt");
    dataObject.setCreatedAt(LocalDateTime.of(2026, 5, 28, 10, 30, 0));
    when(taskArtifactMapper.selectList(any())).thenReturn(List.of(dataObject));

    List<TaskArtifact> artifacts = taskArtifactRepository.findByTaskId(11L);

    assertEquals(88L, artifacts.getFirst().getId());
    assertEquals(LocalDateTime.of(2026, 5, 28, 10, 30, 0), artifacts.getFirst().getCreatedAt());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -Dtest=TaskArtifactRepositoryImplTest test`
Expected: FAIL because repository still rebuilds artifacts with `create(...)` and generates a new ID.

- [ ] **Step 3: Write minimal implementation**

Add `TaskArtifact.restore(...)` and make `TaskArtifactRepositoryImpl.findByTaskId(...)` map rows through it instead of `create(...)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -Dtest=TaskArtifactRepositoryImplTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/2026-05-28-task-artifact-merge.md backend/src/test/java/com/codingx/common/persistence/TableNameMappingTest.java backend/src/test/java/com/codingx/task/infrastructure/persistence/repository/TaskArtifactRepositoryImplTest.java backend/src/main/java/com/codingx/task
git commit -m "fix(task): 合并任务产物模块并修复恢复映射"
```

