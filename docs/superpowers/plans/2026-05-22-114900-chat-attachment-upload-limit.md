# Chat Attachment Upload Limit Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复附件上传被 1MB 提前拦截问题，并将上传上限统一纳入系统配置表，默认 10MB

**Architecture:** 采用“Servlet 层硬上限 + 业务层动态阈值”双层限制。Servlet 层放宽避免早期拦截，业务层统一读取 `setting` 配置键做精确校验，同时前端能力接口与异常处理链路统一对齐。

**Tech Stack:** Spring Boot、MyBatis、PostgreSQL Migration、JUnit5/Mockito

---

### Task 1: 先写失败测试锁定行为（RED）

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/interfaces/controller/ChatAttachmentControllerTest.java`
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatAttachmentServiceTest.java`
- Modify: `backend/src/test/java/com/codingx/config/GlobalExceptionHandlerTest.java`

- [x] **Step 1: 新增 `upload-capabilities` 默认 10MB 断言并验证失败**
- [x] **Step 2: 新增超 10MB 上传抛错断言并验证失败**
- [x] **Step 3: 新增容器层超限异常映射断言并验证失败**

### Task 2: 实现上传阈值统一配置（GREEN）

**Files:**
- Modify: `backend/src/main/java/com/codingx/config/RuntimeProperties.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/support/RuntimeSettingService.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatAttachmentService.java`
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatAttachmentController.java`
- Modify: `backend/src/main/java/com/codingx/common/error/ErrorMessageCatalog.java`
- Modify: `backend/src/main/java/com/codingx/config/GlobalExceptionHandler.java`
- Modify: `backend/src/main/resources/application.yml`

- [x] **Step 1: 增加上传上限运行时默认配置与数据库读取入口**
- [x] **Step 2: 业务层上传校验改为读取动态阈值**
- [x] **Step 3: 上传能力接口改为返回动态阈值**
- [x] **Step 4: 容器层超限异常统一映射中文业务错误**
- [x] **Step 5: 重新运行定向测试验证通过**

### Task 3: 数据库配置落地与回归验证

**Files:**
- Add: `backend/src/main/resources/db/migration/V20260522_112500__add_chat_attachment_upload_size_setting.sql`
- Modify: `backend/src/main/resources/db/init.sql`
- Add: `docs/superpowers/specs/2026-05-22-114900-chat-attachment-upload-limit-design.md`
- Add: `docs/superpowers/acceptance/2026-05-22-114900-chat-attachment-upload-limit-acceptance.md`

- [x] **Step 1: 新增系统配置键 `chat.attachment.max_file_size_bytes` 并写入迁移**
- [x] **Step 2: 同步初始化脚本默认配置**
- [x] **Step 3: 执行 `mvn compile` 验证编译**
- [x] **Step 4: 执行 `mvn test` 验证回归**
- [x] **Step 5: 选择性暂存本次改动并按规范中文提交**
