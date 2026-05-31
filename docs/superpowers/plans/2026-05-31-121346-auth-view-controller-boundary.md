# Auth 视图转换与 Controller 职责边界 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `auth/admin user` 相关后端代码中补齐必要注释，并把登录、当前用户、管理端用户响应转换从 Controller 下沉到应用服务。

**Architecture:** 新增 `UserViewService` 统一负责 `LoginResult/User/AdminUserPageView` 到接口响应对象的投影，Controller 只读取 HTTP 输入、调用应用服务并封装 `ApiResponse`。管理端用户状态和类型中文标签由视图服务集中生成，避免 Controller 承载业务展示规则。

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, Maven, Sa-Token, Hutool。

---

### Task 1: 编写视图转换与 Controller 边界测试

**Files:**
- Add: `backend/src/test/java/com/codingx/auth/application/service/UserViewServiceTest.java`
- Add: `backend/src/test/java/com/codingx/auth/interfaces/controller/AuthControllerTest.java`
- Modify: `backend/src/test/java/com/codingx/auth/interfaces/controller/AdminUserControllerTest.java`

- [x] **Step 1: 新增 UserViewService 响应投影测试**

验证登录响应、当前用户响应、管理端分页响应、详情响应和中文标签映射都由视图服务生成。

- [x] **Step 2: 新增/调整 Controller 边界测试**

验证 `AuthController` 和 `AdminUserController` 只委托应用服务与视图服务，不在 Controller 内持有仓储、持久化或展示标签判断逻辑。

RED 记录：
- `mvn "-Dtest=com.codingx.auth.application.service.UserViewServiceTest,com.codingx.auth.interfaces.controller.AuthControllerTest,com.codingx.auth.interfaces.controller.AdminUserControllerTest" test` 先因缺少 `UserViewService` 编译失败，失败原因符合新增视图服务测试预期。

### Task 2: 下沉 auth/admin user 响应转换

**Files:**
- Add: `backend/src/main/java/com/codingx/auth/application/service/UserViewService.java`
- Modify: `backend/src/main/java/com/codingx/auth/interfaces/controller/AuthController.java`
- Modify: `backend/src/main/java/com/codingx/admin/interfaces/controller/AdminUserController.java`

- [x] **Step 1: 新增 UserViewService**

新增 `toLoginResponse`、`toMeResponse`、`toAdminPageResponse`、`toAdminSummary`、`toAdminDetail` 方法，并用步骤注释说明字段投影和标签生成。

- [x] **Step 2: 精简 AuthController 与 AdminUserController**

移除 Controller 中的响应构造、stream 映射、用户状态标签和用户类型标签判断，改为委托 `UserViewService`。

### Task 3: 补齐 auth 数据载体与服务注释

**Files:**
- Modify: `backend/src/main/java/com/codingx/auth/application/command/LoginCommand.java`
- Modify: `backend/src/main/java/com/codingx/auth/application/service/AuthApplicationService.java`
- Modify: `backend/src/main/java/com/codingx/auth/application/service/AdminUserManagementService.java`
- Modify: `backend/src/main/java/com/codingx/auth/application/service/LoginResult.java`
- Modify: `backend/src/main/java/com/codingx/auth/domain/model/User.java`
- Modify: `backend/src/main/java/com/codingx/auth/domain/repository/UserRepository.java`
- Modify: `backend/src/main/java/com/codingx/auth/domain/service/AuthSessionGateway.java`
- Modify: `backend/src/main/java/com/codingx/auth/domain/service/PasswordHasher.java`
- Modify: `backend/src/main/java/com/codingx/auth/infrastructure/persistence/dataobject/UserDO.java`
- Modify: `backend/src/main/java/com/codingx/auth/infrastructure/persistence/repository/UserRepositoryImpl.java`
- Modify: `backend/src/main/java/com/codingx/auth/infrastructure/security/BCryptPasswordHasher.java`
- Modify: `backend/src/main/java/com/codingx/auth/infrastructure/security/SaTokenSessionGateway.java`
- Modify: `backend/src/main/java/com/codingx/auth/interfaces/request/LoginRequest.java`
- Modify: `backend/src/main/java/com/codingx/auth/interfaces/response/LoginResponse.java`
- Modify: `backend/src/main/java/com/codingx/auth/interfaces/response/MeResponse.java`
- Modify: `backend/src/test/java/com/codingx/auth/application/service/AuthApplicationServiceTest.java`

- [x] **Step 1: 补齐字段注释**

补齐登录请求、登录结果、登录响应、当前用户响应、用户领域对象和持久化对象字段语义，说明来源、状态含义和安全约束。

- [x] **Step 2: 补齐方法步骤注释**

对登录、登出、当前用户查询、密码哈希、会话网关、仓储映射和用户领域行为补充“步骤 1 / 步骤 2 / 步骤 3”注释。

### Task 4: 验证并提交

**Files:**
- Modify only files touched by this plan.

- [x] **Step 1: 运行后端验证**

运行 `mvn -DskipTests compile` 和 `mvn -DskipTests test-compile`。如果 `test-compile` 仍被无关 `AdminChatSkill*` 测试阻塞，记录具体错误并不修改无关测试。

验证记录：
- `mvn -DskipTests compile` 通过，主代码 384 个源文件编译成功。
- `mvn "-Dtest=com.codingx.auth.application.service.UserViewServiceTest,com.codingx.auth.interfaces.controller.AuthControllerTest,com.codingx.auth.interfaces.controller.AdminUserControllerTest" test` 未进入本批次测试执行阶段，因 Maven `testCompile` 先编译全部测试并被无关 `AdminChatSkill*` 旧签名调用阻塞。
- `mvn -DskipTests test-compile` 未通过，阻塞点仍为 `AdminChatSkillServiceTest.java:158`、`:215` 和 `AdminChatSkillControllerTest.java:161`、`:193` 调用 3 参数 `uploadSkillPackage`，当前可用方法为 2 参数和 4 参数重载；本批次不修改这些无关测试。

- [x] **Step 2: 暂存并提交 auth 批次**

只暂存本 plan 中列出的 auth/admin-user 文件和 plan 文档，提交信息使用 `refactor(auth): 下沉用户响应转换并补齐注释`。
