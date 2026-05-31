# Skill 管理端视图转换与测试阻塞修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 `AdminChatSkill*` 测试仍调用旧上传签名导致的后端测试编译阻塞，并把管理端技能包响应转换从 Controller 下沉到应用服务。

**Architecture:** 新增 `SkillPackageViewService` 负责把 `AdminChatSkillService` 返回的技能包迁移、目录树、文件内容内部结果投影为接口响应对象。`AdminChatSkillController` 只负责 HTTP 参数接收、调用应用服务和视图服务、封装 `ApiResponse`；上传测试统一改为当前 4 参数签名。

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, Maven, Hutool, Sa-Token。

---

### Task 1: 编写视图转换与 Controller 边界测试

**Files:**
- Add: `backend/src/test/java/com/codingx/skill/application/service/SkillPackageViewServiceTest.java`
- Modify: `backend/src/test/java/com/codingx/chat/interfaces/controller/AdminChatSkillControllerTest.java`

- [x] **Step 1: 新增 SkillPackageViewService 响应投影测试**

验证迁移统计、失败项、目录条目和文件内容都由视图服务完整转换。

- [x] **Step 2: 调整 AdminChatSkillController 边界测试**

验证 Controller 上传接口使用当前 4 参数签名，技能包迁移/目录/文件预览接口委托 `SkillPackageViewService` 转换响应。

### Task 2: 下沉技能包响应转换

**Files:**
- Add: `backend/src/main/java/com/codingx/skill/application/service/SkillPackageViewService.java`
- Modify: `backend/src/main/java/com/codingx/admin/interfaces/controller/AdminChatSkillController.java`

- [x] **Step 1: 新增 SkillPackageViewService**

新增 `toMigrationSummaryResponse`、`toEntryResponses`、`toFileContentResponse` 方法，并用步骤注释说明转换职责。

- [x] **Step 2: 精简 AdminChatSkillController**

移除 Controller 中的 `new AdminSkillPackage*Response` 和 `stream().map(...)` 响应转换逻辑，改为委托视图服务。

### Task 3: 修复上传测试签名阻塞并补齐注释

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/AdminChatSkillServiceTest.java`
- Modify: `backend/src/main/java/com/codingx/skill/application/service/AdminChatSkillService.java`
- Modify: `backend/src/main/java/com/codingx/skill/application/service/ChatSkillQueryService.java`
- Modify: `backend/src/main/java/com/codingx/skill/domain/model/ChatSkill.java`
- Modify: `backend/src/main/java/com/codingx/skill/infrastructure/persistence/dataobject/ChatSkillDO.java`
- Modify: `backend/src/main/java/com/codingx/skill/infrastructure/persistence/repository/ChatSkillRepositoryImpl.java`
- Modify: `backend/src/main/java/com/codingx/skill/interfaces/controller/ChatSkillController.java`
- Modify: `backend/src/main/java/com/codingx/skill/interfaces/response/AdminSkillPackageMigrationSummaryResponse.java`
- Modify: `backend/src/main/java/com/codingx/skill/interfaces/response/AdminSkillPackageEntryResponse.java`
- Modify: `backend/src/main/java/com/codingx/skill/interfaces/response/AdminSkillPackageFileContentResponse.java`

- [x] **Step 1: 修复旧上传签名测试**

把 `uploadSkillPackage(file, files, category)` 调整为 `uploadSkillPackage(file, files, category, forceOverwrite)`，保持测试语义不变。

- [x] **Step 2: 补齐 skill 相关必要注释**

补齐技能领域对象、DO、管理服务、查询服务、用户侧 Controller 和响应对象字段/依赖/关键流程注释，说明技能包存储格式、目录化上传、重复检测和预览边界。

### Task 4: 验证并提交

**Files:**
- Modify only files touched by this plan.

- [x] **Step 1: 运行后端验证**

运行 `mvn -DskipTests compile`、`mvn -DskipTests test-compile` 和本批次定向测试。若仍有无关测试阻塞，记录具体错误并不修改无关文件。

- [x] **Step 2: 暂存并提交 skill 批次**

只暂存本 plan 中列出的 skill/admin-skill 文件和 plan 文档，提交信息使用 `refactor(skill): 下沉技能包响应转换并修复测试签名`。

### Verification Notes

- RED：`mvn "-Dtest=com.codingx.skill.application.service.SkillPackageViewServiceTest,com.codingx.chat.interfaces.controller.AdminChatSkillControllerTest" test` 初次失败，原因是 `SkillPackageViewService` 尚未实现。
- 定向 GREEN：`mvn "-Dtest=com.codingx.skill.application.service.SkillPackageViewServiceTest,com.codingx.chat.interfaces.controller.AdminChatSkillControllerTest,com.codingx.chat.application.service.AdminChatSkillServiceTest" test` 通过，21 个测试全部通过。
- 编译验证：`mvn -DskipTests compile` 通过，后端 385 个源码文件编译成功。
- 测试编译：`mvn -DskipTests test-compile` 通过，后端 124 个测试源码文件编译成功。
- 全量测试：`mvn test` 仍失败，统计为 494 个测试、17 个失败、3 个错误；失败集中在既有 `AdminChatSettingsServiceTest`、`ChatApplication*`、`AiModelSelectorTest`、`CodexBuiltinChatToolExecutorTest`、`ConversationIntentResolverTest` 等与本次 skill/admin-skill 批次无关的测试。
