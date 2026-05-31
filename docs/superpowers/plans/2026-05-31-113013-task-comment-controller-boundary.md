# Task 模块注释与 Controller 职责边界 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `task` 模块继续落地后端注释规范，并把任务响应转换从 Controller 下沉到应用服务。

**Architecture:** `TaskController` 保持 HTTP 协议适配，只读取登录态、组装 command、调用应用服务并封装 `ApiResponse`。新增 `TaskViewService` 负责把 `Task` 领域对象投影为 `TaskResponse`，命令/查询服务继续承接任务创建、启动、归属校验和仓储访问。

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, Maven, Sa-Token。

---

### Task 1: 编写响应投影与 Controller 边界测试

**Files:**
- Add: `backend/src/test/java/com/codingx/task/application/service/TaskViewServiceTest.java`
- Add: `backend/src/test/java/com/codingx/task/interfaces/controller/TaskControllerTest.java`

- [x] **Step 1: 新增 TaskViewService 响应投影测试**

验证 `TaskViewService#toResponse` 保留任务主键、状态、运行时、归属用户、摘要和错误信息，避免 Controller 手写字段映射。

- [x] **Step 2: 新增 TaskController 边界测试**

验证 `TaskController` 不直接持有 repository / persistence 依赖，创建、列表和详情接口都委托 `TaskViewService` 转换响应。

### Task 2: 下沉 TaskController 响应转换

**Files:**
- Add: `backend/src/main/java/com/codingx/task/application/service/TaskViewService.java`
- Modify: `backend/src/main/java/com/codingx/task/interfaces/controller/TaskController.java`

- [x] **Step 1: 新增 TaskViewService**

新增 `toResponse` / `toResponses` 方法，方法体用步骤注释说明单对象转换和列表转换职责。

- [x] **Step 2: 精简 TaskController**

移除 `TaskController#toResponse` 私有方法，Controller 只调用命令/查询服务和视图服务。

### Task 3: 补齐 task 数据载体与服务注释

**Files:**
- Modify: `backend/src/main/java/com/codingx/task/interfaces/request/CreateTaskRequest.java`
- Modify: `backend/src/main/java/com/codingx/task/interfaces/response/TaskResponse.java`
- Modify: `backend/src/main/java/com/codingx/task/application/command/CreateTaskCommand.java`
- Modify: `backend/src/main/java/com/codingx/task/application/command/StartTaskCommand.java`
- Modify: `backend/src/main/java/com/codingx/task/infrastructure/persistence/dataobject/TaskDO.java`
- Modify: `backend/src/main/java/com/codingx/task/domain/model/Task.java`
- Modify: `backend/src/main/java/com/codingx/task/application/service/TaskCommandApplicationService.java`
- Modify: `backend/src/main/java/com/codingx/task/application/service/TaskQueryApplicationService.java`
- Modify: `backend/src/main/java/com/codingx/task/interfaces/controller/TaskStreamController.java`

- [x] **Step 1: 补齐字段注释**

补齐 Request / Response / Command / DO / 领域对象字段语义，说明来源、状态含义和可空条件。

- [x] **Step 2: 补齐方法步骤注释**

对创建、启动、查询、归属校验和 SSE 订阅入口补充“步骤 1 / 步骤 2 / 步骤 3”注释。

### Task 4: 验证并提交

**Files:**
- Modify only files touched by this plan.

- [x] **Step 1: 运行后端验证**

运行 `mvn -DskipTests compile` 和 `mvn -DskipTests test-compile`。如果 `test-compile` 仍被无关 `AdminChatSkill*` 测试阻塞，记录具体错误并不修改无关测试。

验证记录：
- `mvn -DskipTests compile` 通过，主代码 383 个源文件编译成功。
- `mvn -DskipTests test-compile` 未通过，阻塞点来自无关 `AdminChatSkill*` 测试仍按旧的 3 参数 `uploadSkillPackage` 签名调用；本批次不修改这些无关测试。

- [x] **Step 2: 暂存并提交 task 批次**

只暂存本 plan 中列出的 task 文件和 plan 文档，提交信息使用 `refactor(task): 下沉任务响应转换并补齐注释`。
