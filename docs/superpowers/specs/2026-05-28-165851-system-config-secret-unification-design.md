# 系统配置与密钥统一化设计

**目标**

将后端 `app.*` 自定义业务配置收敛为“系统配置表唯一业务配置源”，并为 AI / Web Search 等敏感密钥引入数据库加密存储能力。`.env` 仅保留数据库外的信任根 `APP_CONFIG_MASTER_KEY` 与基础设施启动配置，`application.yml` 只保留 Spring / 中间件 / 启动期必要配置。

**范围**

- 后端系统配置表结构增强，支持密文、脱敏和密钥元数据
- 运行时配置读取链路统一收口到系统配置服务
- AI / Web Search 等业务密钥从环境变量迁移到系统配置表
- 精简 `application.yml` 中 `app.*` 自定义配置
- 更新配置文档与系统配置种子数据

**非范围**

- 数据库、Redis、对象存储连接配置迁入系统配置表
- `APP_CONFIG_MASTER_KEY` 迁入数据库
- 管理端新增复杂权限体系；本次仅保证返回值脱敏与更新链路安全

## 现状

当前项目已经存在 `setting` 表与 `RuntimeSettingService`，普通运行时参数大多是“数据库优先、`RuntimeProperties` / `AiProperties` / `ChatExecutorRuntimeProperties` fallback”的双层读取。与此同时：

- `application.yml` 下 `app.ai`、`app.runtime`、`app.chat.*` 仍保留大量业务配置
- `setting` 表只有 `setting_value` 明文字段，没有敏感项标识、密文列、脱敏列、加密元数据与审计语义
- `web_search.api_key` 之类敏感值已经进入 `setting` 默认种子，但仍以明文方式存在
- `RedissonConfig` 仍依赖 `@ConditionalOnProperty(prefix = "app.runtime", ...)` 决定是否创建 Bean，属于启动期配置

这导致配置来源分散、密钥风险明显、`application.yml` 可读性差且维护成本高。

## 设计原则

1. 业务配置唯一来源是 `setting` 表，避免 `application.yml`、`.env`、数据库三处重复维护
2. 密钥永不以明文返回给管理端，也不以明文持久化到数据库
3. 数据库中的业务密钥使用 `APP_CONFIG_MASTER_KEY` 加密；主密钥只存在 `.env` / 宿主机环境
4. 启动期必须决定 Bean 装配的配置，不强行做成数据库唯一来源；必要时保留极小桥接配置
5. 新旧环境可平滑迁移：先兼容读取，再迁移种子数据，最后删除 YAML fallback

## 配置分层

### 第一层：宿主机环境 / `.env`

仅保留以下内容：

- `APP_CONFIG_MASTER_KEY`
- 数据库连接配置
- Redis 连接配置
- 对象存储连接配置
- `server.port` 等启动基础配置
- 极少数启动期桥接配置，例如 `app.runtime.use-redis-queue-gate`

### 第二层：`application.yml`

只保留：

- Spring Boot 基础配置
- 中间件基础配置
- 启动期骨架配置与默认结构
- 非业务性的格式化和日志配置

不再保留大段 `app.ai`、`app.runtime`、`app.chat.memory` 等业务默认值。

### 第三层：`setting` 表

作为业务配置唯一来源，托管：

- `ai.*`
- `web_search.*`
- `chat.memory.*`
- `chat.executor.*`
- `queue.*`
- `code_search.*`
- `chat.attachment.*`
- `chat.intent.guidance.*`

## 数据模型调整

在 `setting` 表新增以下字段：

- `encrypted_value TEXT`：敏感配置密文
- `secret BOOLEAN NOT NULL DEFAULT FALSE`：是否敏感配置
- `masked_value VARCHAR(255)`：管理端回显的脱敏值快照
- `encryption_algorithm VARCHAR(64)`：加密算法标识，例如 `AES_GCM`
- `encryption_key_version VARCHAR(64)`：主密钥版本，支持后续轮换

约束规则：

- `secret = FALSE` 时，使用 `setting_value`，`encrypted_value` 为空
- `secret = TRUE` 时，使用 `encrypted_value`，`setting_value` 允许为空字符串，管理端只返回 `masked_value`
- 同一条配置只能走一种存储语义，服务层负责校验

## 后端组件设计

### 1. ConfigCryptoService

新增专用密钥服务，职责：

- 从环境变量读取 `APP_CONFIG_MASTER_KEY`
- 提供 `encrypt(plainText)` / `decrypt(cipherText)` / `mask(plainText)` 能力
- 校验主密钥长度与格式，启动期失败时直接阻止应用启动

主密钥策略：

- 默认使用 Base64 编码的 256-bit key
- 算法采用 AES-GCM
- 加密输出统一编码为 Base64，便于存库和迁移

### 2. RuntimeSettingService

扩展为统一配置网关：

- 普通值直接读 `setting_value`
- 敏感值解密 `encrypted_value`
- 删除大部分 `RuntimeProperties` / `AiProperties` fallback
- 对缺失配置抛出明确异常，避免“静默 fallback 到旧 YAML”

### 3. AdminChatSettingsService

增强系统配置管理能力：

- 查询列表时对 `secret = TRUE` 的项只返回 `masked_value`
- 更新敏感项时，若前端未提交新值，则保持原密文不变
- 更新敏感项时，后端完成加密、脱敏、算法写入

## AI 配置整理

当前 `app.ai` 过于臃肿，本次按以下方式收敛：

### 迁入系统配置表

- `ai.provider`
- `ai.base_url`
- `ai.api_key`（敏感）
- `ai.chat_model`
- `ai.connect_timeout_ms`
- `ai.read_timeout_ms`
- `ai.system_prompt`
- `ai.selection.*`
- `ai.chat.default_model`
- `ai.chat.deep_thinking_model`
- provider 级密钥与地址，如 `ai.providers.siliconflow.api_key`

### 保留在代码 / 资源文件

模型候选列表 `ai.chat.candidates` 不适合继续堆在 `application.yml`。本次改为：

- 候选清单进入系统配置种子，按 `setting` 统一管理
- 若当前实现改造成本过高，则短期保留为 `AiProperties` 中的最小静态资源定义，并在后续单独拆为模型配置表

优先级判断：

- 本次先完成密钥与主要运行时参数唯一化
- 若候选列表迁表牵动范围过大，可在本轮保留静态清单，只剥离 provider / 默认模型 / 密钥

## 启动期配置处理

`app.runtime.use-redis-queue-gate` 这类配置不能直接删，因为 `RedissonConfig` 目前在 Bean 装配期就需要。处理方式：

- 保留为极小桥接配置，继续存于 `.env` / `application.yml`
- 明确标注为“启动期配置”，不纳入系统配置唯一来源承诺

这意味着“唯一来源”适用于业务配置与业务密钥，不适用于数据库外基础设施和 Bean 装配开关。

## 迁移策略

### 阶段 1：结构升级

- 为 `setting` 表新增敏感配置字段
- 在 `schema.sql`、migration、init.sql 中补齐定义和中文注释

### 阶段 2：运行时兼容读取

- 新增密钥服务
- `RuntimeSettingService` 支持普通值与密文值双读
- 管理端接口支持敏感值脱敏回显

### 阶段 3：种子与默认值迁移

- 将现有 `app.*` 里的业务默认值写入 `init.sql` 和新 migration
- 将 `web_search.api_key`、AI provider key 等标记为敏感项

### 阶段 4：删除 YAML 业务 fallback

- 从 `application.yml` 中删除已迁入表的业务配置
- 精简 `RuntimeProperties`、`AiProperties`、`ChatMemoryProperties`、`ChatExecutorRuntimeProperties`

## 错误处理

- 主密钥缺失：应用启动失败，返回中文异常并记录统一格式日志
- 敏感值解密失败：运行时抛业务异常，提示配置损坏或主密钥不匹配
- 管理端敏感配置更新为空：视为不修改，不清空旧密钥
- 敏感项读取：只返回脱敏结果，避免接口泄露明文

## 测试策略

1. 配置加密服务单测
   - 主密钥合法时可加解密
   - 主密钥缺失 / 非法时启动失败
2. 系统配置服务单测
   - 普通项读取
   - 密文项解密读取
   - 密文损坏异常处理
3. 管理端配置服务单测
   - 密钥回显脱敏
   - 更新时保留旧密文
   - 更新新密钥后可被运行时读取
4. 编译与后端测试
   - `mvn compile`
   - `mvn test`

## 文档更新

需更新：

- `docs/features/index.md`
- 新增 `docs/features/chat/system-config-secret-unification.md`

文档记录真实配置分层、主密钥约束、密文存储和管理端使用方式。
