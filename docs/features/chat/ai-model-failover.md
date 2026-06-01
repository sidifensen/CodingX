# AI 模型故障切换策略

## 功能用途

聊天模型调用支持多候选自动故障切换：主模型不可用、首包超时、首包前报错、无内容完成或 provider 客户端缺失时，后端自动尝试下一个候选，避免单个模型故障直接中断聊天。

## 使用入口

聊天主流程通过 `RoutingAiChatClient` 进入 `AiModelDispatchService`。模型候选、provider 地址、密钥、首包超时、失败阈值和熔断窗口由 `AiProperties`、`DynamicAiProperties`、`DynamicAiRoutingProperties` 聚合提供。

## 核心流程

1. `AiModelSelector` 读取聊天候选池，过滤禁用候选；图片附件优先筛选视觉模型，深度思考模式优先筛选 thinking 模型。
2. 未指定 `preferredModel` 时，不再读取默认模型指针；普通模式和深度思考模式都在能力过滤后按 `priority` 和候选 ID 排序。
3. `AiModelDispatchService` 按候选顺序解析 provider 客户端，缺失客户端直接跳过；熔断中的模型在冷却前不参与调用。
4. provider 返回 `AiStreamSession` 后，调度层等待首包窗口；首包前失败、超时或无内容完成会取消 session、标记模型失败并切换后续候选。
5. 首包成功后提交缓冲事件并等待流结束；此后异常视为已输出后的失败，不再换模型接管，避免用户看到两套模型混合回答。

## 关键文件

- `backend/src/main/java/com/codingx/common/support/ai/AiModelSelector.java`：候选过滤、首选模型排序和 provider 配置装配。
- `backend/src/main/java/com/codingx/common/support/ai/AiModelDispatchService.java`：候选调度、首包探测、fallback、取消和失败标记。
- `backend/src/main/java/com/codingx/common/support/ai/AiProviderHealthRegistry.java`：模型候选维度的 `CLOSED` / `OPEN` / `HALF_OPEN` 三态熔断。
- `backend/src/main/java/com/codingx/common/support/ai/FirstTokenBufferingHandler.java`：首包确认前缓冲模型事件，防止失败候选泄漏脏输出。
- `backend/src/test/java/com/codingx/support/ai/AiModelSelectorTest.java`：候选排序与能力过滤单测。
- `backend/src/test/java/com/codingx/support/ai/AiModelDispatchServiceTest.java`：首包探测、fallback、熔断跳过和并发隔离单测。

## 关键逻辑

故障状态以模型候选 ID 为粒度，不以 provider 名称为粒度，避免同一 provider 下不同模型互相污染健康状态。熔断打开后，冷却期内 `allowCall` 返回 false；冷却结束进入半开状态，只允许一个探测请求在飞，成功恢复关闭状态，失败重新打开熔断窗口。

调度尝试顺序使用请求局部变量记录，并在请求完成时发布不可变快照到 `getLastAttemptedProviders()`。这保证 `AiModelDispatchService` 作为单例 Bean 时，并发请求不会互相清空或拼接尝试记录。

默认路由顺序只由 `ai.chat.candidates.<slot>.*` 候选池决定。候选字段中的 provider、模型名、能力标记和 `priority` 一起决定真实调用目标和 fallback 顺序；只有请求显式传入 `preferredModel` 时，才会在匹配候选内临时提升该候选到首位。

## 测试与验证

- `mvn -Dtest=AiModelSelectorTest,AiModelDispatchServiceTest test`
- `mvn compile`
- `mvn test`
