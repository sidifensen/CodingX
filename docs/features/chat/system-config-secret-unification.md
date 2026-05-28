# 系统配置与密钥统一化

## 功能用途

将聊天运行时相关的业务配置统一收敛到 `setting` 系统配置表，并为 AI / Web Search 等敏感配置提供数据库密文存储能力。宿主环境只保留主密钥 `APP_CONFIG_MASTER_KEY` 和基础设施启动配置，避免大量业务配置继续散落在 `.env` 与 `application.yml`。

## 使用入口

- 后端运行时读取入口：`RuntimeSettingService`
- 管理端系统配置入口：`AdminChatSettingsService`
- 敏感配置加解密入口：`ConfigCryptoService`

## 核心流程

1. 应用启动时从 `.env` / 环境变量读取 `APP_CONFIG_MASTER_KEY`，初始化 `ConfigCryptoService`
2. 管理端保存敏感配置时，后端负责加密明文、生成脱敏值，并写入 `setting`
3. 运行时读取普通配置时使用 `setting_value`，读取敏感配置时解密 `encrypted_value`
4. `DynamicAiProperties` 会从系统配置表聚合 `ai.providers.*` 与 `ai.chat.candidates.<slot>.*`，由数据库控制 provider endpoint 与候选模型池
5. `application.yml` 只保留 Spring / 中间件 / 启动期桥接配置，以及 AI 的最小静态骨架；真实业务值优先从系统配置表读取

## 关键文件

- `backend/src/main/java/com/codingx/chat/application/service/support/ConfigCryptoService.java`
- `backend/src/main/java/com/codingx/chat/application/service/support/RuntimeSettingService.java`
- `backend/src/main/java/com/codingx/admin/application/service/AdminChatSettingsService.java`
- `backend/src/main/java/com/codingx/config/DynamicAiProperties.java`
- `backend/src/main/java/com/codingx/common/support/ai/AiModelSelector.java`
- `backend/src/main/resources/db/schema.sql`
- `backend/src/main/resources/db/init.sql`
- `backend/src/main/resources/db/migration/V20260528_170500__add_setting_secret_encryption_fields.sql`
- `backend/src/main/resources/db/migration/V20260528_204500__seed_full_ai_system_settings.sql`

## 关键数据结构

`setting` 表新增字段：

- `secret`：是否敏感配置
- `encrypted_value`：敏感配置密文
- `masked_value`：管理端脱敏展示值
- `encryption_algorithm`：加密算法标识
- `encryption_key_version`：主密钥版本

## 约束与边界

- `APP_CONFIG_MASTER_KEY` 不允许进入系统配置表
- 敏感配置不能回显明文，接口只返回脱敏值
- `ai.chat.candidates.<slot>.*` 使用扁平系统配置键维护候选池，不新增独立配置表
- 敏感 AI 密钥在管理端留空表示“保持原值不变”，不会用空字符串覆盖旧密文
- `use-redis-queue-gate` 等启动期开关仍保留在启动配置中，不纳入本轮“唯一业务配置源”
- 数据库、Redis、对象存储连接信息仍由宿主环境管理

## 测试与验证

- `mvn compile`
- `mvn test`
- 重点单测覆盖加解密、脱敏、敏感配置读取和管理端保存行为
