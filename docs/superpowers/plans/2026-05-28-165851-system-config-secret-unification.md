# System Config Secret Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将业务运行时配置统一收敛到系统配置表，并为 AI / Web Search 等敏感配置引入数据库加密存储与受控读取链路。

**Architecture:** 通过扩展 `setting` 表结构、增加主密钥加密服务、统一 `RuntimeSettingService` 读取逻辑，并精简 `application.yml` 中的 `app.*` 业务配置，实现“系统配置表唯一业务配置源 + `.env` 主密钥信任根”的双层架构。启动期 Bean 装配开关保留最小桥接配置，不强行迁表。

**Tech Stack:** Spring Boot, MyBatis-Plus, PostgreSQL, JUnit 5, Maven

---

### Task 1: 为系统配置表增加敏感配置字段与种子数据

**Files:**
- Modify: `backend/src/main/resources/db/schema.sql`
- Modify: `backend/src/main/resources/db/init.sql`
- Create: `backend/src/main/resources/db/migration/V20260528_170500__add_setting_secret_encryption_fields.sql`

- [ ] **Step 1: 写数据库结构测试或校验用例，明确需要新增的敏感字段**

- [ ] **Step 2: 运行目标测试并确认失败**

- [ ] **Step 3: 在 migration、schema、init 中补齐 `secret`、`encrypted_value`、`masked_value`、`encryption_algorithm`、`encryption_key_version` 字段及中文注释**

- [ ] **Step 4: 为 AI / Web Search 敏感配置种子打上敏感标记，避免继续以明文种子存储**

- [ ] **Step 5: 运行相关测试确认结构定义通过**

### Task 2: 新增加密服务并建立主密钥校验

**Files:**
- Create: `backend/src/main/java/com/codingx/config/ConfigCryptoProperties.java`
- Create: `backend/src/main/java/com/codingx/chat/application/service/support/ConfigCryptoService.java`
- Create: `backend/src/test/java/com/codingx/chat/application/service/support/ConfigCryptoServiceTest.java`
- Modify: `backend/src/main/java/com/codingx/CodingXApplication.java`

- [ ] **Step 1: 先写 `ConfigCryptoService` 单测，覆盖加解密、脱敏、主密钥缺失分支**

- [ ] **Step 2: 运行单测并确认 RED**

- [ ] **Step 3: 实现 `APP_CONFIG_MASTER_KEY` 读取、AES-GCM 加解密与脱敏逻辑，并补齐必要注释**

- [ ] **Step 4: 重新运行单测确认 GREEN**

### Task 3: 扩展系统配置领域模型与管理端服务

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/domain/model/ChatRuntimeSetting.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/dataobject/intent/ChatRuntimeSettingDO.java`
- Modify: `backend/src/main/java/com/codingx/admin/application/service/AdminChatSettingsService.java`
- Modify: `backend/src/test/java/com/codingx/admin/application/service/AdminChatSettingsServiceTest.java`

- [ ] **Step 1: 先写管理端配置服务测试，覆盖敏感项脱敏展示与保留旧密文更新**

- [ ] **Step 2: 运行该测试并确认 RED**

- [ ] **Step 3: 扩展 DO / 领域模型 / 管理服务，使敏感项读取脱敏、写入时通过加密服务落库**

- [ ] **Step 4: 重新运行测试确认 GREEN**

### Task 4: 统一 RuntimeSettingService 读取链路

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/application/service/support/RuntimeSettingService.java`
- Modify: `backend/src/test/java/com/codingx/chat/application/service/support/RuntimeSettingServiceTest.java`

- [ ] **Step 1: 先写运行时配置测试，覆盖普通值读取、敏感值解密读取、损坏密文异常**

- [ ] **Step 2: 运行测试并确认 RED**

- [ ] **Step 3: 改造 `RuntimeSettingService`，统一从系统配置表读取普通值和敏感值，逐步移除已迁移配置的 YAML fallback**

- [ ] **Step 4: 重新运行测试确认 GREEN**

### Task 5: 收口 AI / Web Search 配置来源并精简 application.yml

**Files:**
- Modify: `backend/src/main/java/com/codingx/config/AiProperties.java`
- Modify: `backend/src/main/java/com/codingx/config/RuntimeProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/search/ConfigurableWebSearchChannel.java`
- Modify: related tests under `backend/src/test/java`

- [ ] **Step 1: 先补测试，明确 AI / Web Search 读取来自系统配置表，而非旧 `app.*` fallback**

- [ ] **Step 2: 运行测试并确认 RED**

- [ ] **Step 3: 删除可迁移的 `app.*` 业务配置，保留启动期桥接项，更新相关 properties 类与调用点**

- [ ] **Step 4: 运行测试确认 GREEN**

### Task 6: 更新功能文档并完成后端验证

**Files:**
- Modify: `docs/features/index.md`
- Create: `docs/features/chat/system-config-secret-unification.md`

- [ ] **Step 1: 更新功能文档，说明配置分层、主密钥要求、敏感配置更新方式**

- [ ] **Step 2: 运行 `mvn compile`**

- [ ] **Step 3: 运行 `mvn test`**

- [ ] **Step 4: 检查工作区变更，仅保留本次需求相关文件并准备提交**
