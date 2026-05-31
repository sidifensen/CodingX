# Admin Settings Controller 边界与注释补齐 Implementation Plan

> **Scope:** 本批次只处理管理端聊天运行时配置接口，避开当前工作区中其他会话已修改的核心聊天服务与前端文件。

**Goal:** 将 `AdminChatSettingsController` 中批量保存配置的循环逻辑下沉到 `AdminChatSettingsService`，并补齐运行时配置领域对象、DO、服务依赖和 Controller 步骤注释。

**Architecture:** `AdminChatSettingsController` 只负责接收请求、调用服务和封装 `ApiResponse`。新增 `AdminChatSettingsService.saveAll(List<ChatRuntimeSetting>)` 承接批量保存、空集合处理和保存后读取最新配置列表。

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, Maven。

---

### Task 1: 编写批量保存委托测试

**Files:**
- Add: `backend/src/test/java/com/codingx/chat/interfaces/controller/AdminChatSettingsControllerTest.java`
- Add: `backend/src/test/java/com/codingx/admin/application/service/AdminChatSettingsBatchServiceTest.java`

- [x] **Step 1: 新增 Controller 边界测试**

验证 `/api/admin/chat/settings/batch` 只调用 `AdminChatSettingsService.saveAll(...)`，不在 Controller 中逐条调用 `save(...)`。

- [x] **Step 2: 新增服务批量保存测试**

验证 `saveAll(...)` 会按顺序调用单条保存逻辑，并返回最新全量配置列表；空列表应直接返回刷新后的全量配置。

### Task 2: 下沉批量保存逻辑

**Files:**
- Modify: `backend/src/main/java/com/codingx/admin/interfaces/controller/AdminChatSettingsController.java`
- Modify: `backend/src/main/java/com/codingx/admin/application/service/AdminChatSettingsService.java`

- [x] **Step 1: 新增 AdminChatSettingsService.saveAll**

服务层统一处理空集合、逐条保存和最终列表刷新。

- [x] **Step 2: 精简 AdminChatSettingsController**

移除 Controller 中的 `if + for` 保存循环，改为调用 `saveAll(...)`。

### Task 3: 补齐运行时配置注释

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/domain/model/ChatRuntimeSetting.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/dataobject/intent/ChatRuntimeSettingDO.java`

- [x] **Step 1: 补齐领域对象和 DO 字段注释**

为运行时配置字段说明业务语义、敏感配置字段、可空规则和默认含义。

### Task 4: 验证并提交

**Files:**
- Modify only files touched by this plan.

- [x] **Step 1: 执行 RED/GREEN 与编译验证**

先运行本批次测试确认 RED，再实现后运行定向测试和 `mvn -DskipTests compile`。若全量测试仍受既有无关失败阻塞，只记录不修改。

- [x] **Step 2: 限定暂存并提交**

只暂存本 plan 中列出的文件和 plan 文档，提交信息使用 `refactor(admin): 下沉配置批量保存并补齐注释`。

### Verification Notes

- RED：`mvn "-Dtest=com.codingx.chat.interfaces.controller.AdminChatSettingsControllerTest,com.codingx.admin.application.service.AdminChatSettingsBatchServiceTest" test` 失败，原因是 `AdminChatSettingsService.saveAll(...)` 尚未实现。
- 定向 GREEN：`mvn "-Dtest=com.codingx.chat.interfaces.controller.AdminChatSettingsControllerTest,com.codingx.admin.application.service.AdminChatSettingsBatchServiceTest" test` 通过，4 个测试全部通过。
- 编译验证：`mvn -DskipTests compile` 通过，后端 387 个源码文件编译成功。
- 全量测试：`mvn test` 仍失败，统计为 501 个测试、19 个失败、3 个错误；失败集中在既有 `AdminChatSettingsServiceTest`、`ChatApplication*`、`ChatStreamExecutionServiceTest`、`ConversationQueueGateTest`、`ConversationIntentResolverTest`、`AiModelSelectorTest`、`CodexBuiltinChatToolExecutorTest` 等与本次 Controller 边界批次无关的测试。
