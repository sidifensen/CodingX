# 后端注释与 Controller 职责边界 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将后端注释规范写入 `AGENTS.md`，并在聊天主入口落地 Controller 业务下沉和字段/方法注释样板。

**Architecture:** `ChatController` 保留 HTTP 协议适配，业务编排迁入 `ChatConversationApplicationService` 和 `ChatApplicationService`。响应组装、任务投影、工作空间类型判断、消息附件和投票查询由 service 层完成。

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, Maven, Sa-Token, Hutool。

---

### Task 1: 固化规范和验收文档

**Files:**
- Modify: `AGENTS.md`
- Create: `docs/superpowers/specs/2026-05-31-104552-backend-comment-controller-boundary-design.md`
- Create: `docs/superpowers/acceptance/2026-05-31-104552-backend-comment-controller-boundary-acceptance.md`
- Create: `docs/superpowers/plans/2026-05-31-104552-backend-comment-controller-boundary.md`

- [x] **Step 1: 写入后端注释细则**

在 `AGENTS.md` 的“注释规范”后补充“后端注释细则”，覆盖依赖字段、数据载体字段、方法步骤和 Controller 分层职责。

- [x] **Step 2: 写入设计和验收文档**

创建本 spec、acceptance 和 plan，明确本批次不修改前端、不改数据库、不处理其他会话无关文件。

### Task 2: 编写 Controller 依赖边界失败测试

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/interfaces/controller/ChatControllerConversationMutationTest.java`

- [x] **Step 1: 新增反射测试**

新增 `controllerDoesNotDependOnRepositoriesOrPersistenceInfrastructure`，扫描 `ChatController` 字段类型，发现 `.domain.repository.`、`.infrastructure.repository.` 或 `.infrastructure.persistence.` 直接依赖即失败。

- [x] **Step 2: 执行 RED 验证**

运行：

```bash
mvn '-Dtest=com.codingx.chat.interfaces.controller.ChatControllerConversationMutationTest#controllerDoesNotDependOnRepositoriesOrPersistenceInfrastructure' test
```

当前仓库测试编译先被 `AdminChatSkillServiceTest` / `AdminChatSkillControllerTest` 的既有方法签名不匹配阻塞，记录为无关测试编译阻塞。

### Task 3: 下沉 ChatController 业务编排

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatController.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatConversationApplicationService.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`

- [x] **Step 1: 在 service 层增加响应组装方法**

新增专门的 `ChatConversationViewService` 承接会话列表、消息列表、公开分享详情等 `ChatConversationResponse` / `ChatMessageResponse` 组装，内部完成工作空间查询、任务投影、附件和用户投票组装。

- [x] **Step 2: 在 ChatApplicationService 增加同步发送适配方法**

新增面向同步 HTTP 入口的方法，接收 `conversationId`、请求内容、技能编码和附件标识，内部规整技能编码、加载可用 MCP、调用原 `sendMessage`，并在 finally 中释放会话门控。

- [x] **Step 3: 精简 ChatController**

移除 `ChatController` 中直接 repository / persistence 依赖和私有响应组装方法，接口方法只读取 `StpUtil.getLoginIdAsLong()`、转发请求参数和封装返回值。

### Task 4: 补齐字段和方法步骤注释

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/request/SendChatMessageRequest.java`
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/response/ChatAttachmentResponse.java`
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/response/ChatConversationResponse.java`
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/response/ChatMessageResponse.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/dataobject/conversation/ChatAttachmentDO.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/dataobject/conversation/ChatConversationDO.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/dataobject/conversation/ChatMessageDO.java`

- [x] **Step 1: 补齐数据载体字段注释**

逐个字段说明字段语义、状态含义、是否来自用户输入或持久化记录。

- [x] **Step 2: 补齐 service 方法步骤注释**

对新增或重写的 service 方法按“步骤 1 / 步骤 2 / 步骤 3”标明处理链路。

### Task 5: 验证并提交

**Files:**
- Modify only files touched by this plan.

- [x] **Step 1: 运行定向测试和后端验证**

运行 `mvn compile`、定向控制器测试和 `mvn test`。若 `testCompile` 被其他会话无关测试阻塞，记录具体文件和错误。

验证记录：`mvn -DskipTests compile` 通过；`mvn -DskipTests test-compile` 被既有 `AdminChatSkillServiceTest` / `AdminChatSkillControllerTest` 调用 `uploadSkillPackage` 三参数签名阻塞，未改动无关测试。

- [x] **Step 2: 暂存并提交本批次文件**

只暂存 `AGENTS.md`、本批次 superpowers 文档、`chat` 相关后端源码和对应测试。提交信息使用 `refactor(chat): 下沉控制器业务并补齐后端注释规范`。
