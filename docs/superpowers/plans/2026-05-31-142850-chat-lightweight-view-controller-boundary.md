# Chat 轻量接口视图转换与 Controller 边界 Implementation Plan

> **Scope:** 本批次只处理用户侧示例问题和附件接口层，避开当前工作区中其他会话已修改的核心聊天服务与前端文件。

**Goal:** 把 `ChatSampleQuestionController` 和 `ChatAttachmentController` 中的响应对象组装、上传能力 Map 组装下沉到应用层视图服务，并补齐依赖、字段和关键流程注释。

**Architecture:** 新增 `ChatLightweightViewService`，负责示例问题列表、附件元数据和附件上传能力响应投影。新增 `ChatAttachmentUploadCapabilitiesResponse` 替代 Controller 内临时 Map，保证上传能力字段有明确注释。Controller 只负责接收 HTTP 参数、调用应用服务、调用视图服务和封装 `ApiResponse` / `ResponseEntity`。

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, Maven。

---

### Task 1: 编写 Controller 委托边界测试

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/interfaces/controller/ChatSampleQuestionControllerTest.java`
- Modify: `backend/src/test/java/com/codingx/chat/interfaces/controller/ChatAttachmentControllerTest.java`

- [x] **Step 1: 示例问题 Controller 测试改为验证视图服务委托**

测试 `ChatSampleQuestionController` 从 `ChatSampleQuestionService` 获取领域对象后，必须调用视图服务转换响应。

- [x] **Step 2: 附件 Controller 测试改为验证视图服务委托**

测试上传接口、上传能力接口分别委托视图服务生成响应，Controller 不再直接 new 响应对象或 Map。

### Task 2: 新增轻量视图服务并精简 Controller

**Files:**
- Add: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatLightweightViewService.java`
- Add: `backend/src/main/java/com/codingx/chat/interfaces/response/ChatAttachmentUploadCapabilitiesResponse.java`
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatSampleQuestionController.java`
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatAttachmentController.java`

- [x] **Step 1: 新增 ChatLightweightViewService**

提供 `toSampleQuestionResponses`、`toAttachmentResponse`、`toUploadCapabilitiesResponse` 方法，并补齐步骤注释。

- [x] **Step 2: 精简两个 Controller**

移除 Controller 内的 `stream().map(...)`、`new ChatAttachmentResponse(...)` 和上传能力 `Map.of(...)` 组装。

### Task 3: 补齐相关注释

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatSampleQuestionService.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatAttachmentService.java`
- Modify: `backend/src/main/java/com/codingx/chat/domain/model/ChatSampleQuestion.java`
- Modify: `backend/src/main/java/com/codingx/chat/domain/model/ChatAttachment.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/dataobject/intent/ChatSampleQuestionDO.java`
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/response/ChatSampleQuestionResponse.java`
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/response/ChatAttachmentResponse.java`
- Add: `backend/src/test/java/com/codingx/chat/application/service/ChatLightweightViewServiceTest.java`

- [x] **Step 1: 补齐依赖与字段注释**

为服务依赖、响应字段补充业务语义、可空规则和来源说明。

### Task 4: 验证并提交

**Files:**
- Modify only files touched by this plan.

- [x] **Step 1: 执行 RED/GREEN 与编译验证**

先运行本批次 Controller 测试确认 RED，再实现后运行定向测试和 `mvn -DskipTests compile`。若全量测试仍受无关改动阻塞，只记录不修改。

- [x] **Step 2: 限定暂存并提交**

只暂存本 plan 中列出的文件和 plan 文档，提交信息使用 `refactor(chat): 下沉轻量接口响应转换并补齐注释`。

### Verification Notes

- RED：`mvn "-Dtest=com.codingx.chat.interfaces.controller.ChatSampleQuestionControllerTest,com.codingx.chat.interfaces.controller.ChatAttachmentControllerTest" test` 失败，原因是 `ChatLightweightViewService` 和 `ChatAttachmentUploadCapabilitiesResponse` 尚未实现。
- 定向 GREEN：`mvn "-Dtest=com.codingx.chat.interfaces.controller.ChatSampleQuestionControllerTest,com.codingx.chat.interfaces.controller.ChatAttachmentControllerTest,com.codingx.chat.application.service.ChatLightweightViewServiceTest" test` 通过，7 个测试全部通过。
- 编译验证：`mvn -DskipTests compile` 通过，后端 387 个源码文件编译成功。
- 全量测试：`mvn test` 仍失败，统计为 497 个测试、17 个失败、3 个错误；失败集中在既有 `AdminChatSettingsServiceTest`、`ChatApplication*`、`ConversationIntentResolverTest`、`AiModelSelectorTest`、`CodexBuiltinChatToolExecutorTest` 等与本次轻量接口视图批次无关的测试。
