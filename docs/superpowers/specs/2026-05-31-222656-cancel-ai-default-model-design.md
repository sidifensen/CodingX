# 取消 AI 默认模型设置设计

## 背景

当前聊天模型路由同时依赖候选池和两个默认模型指针：`ai.chat.default_model` 与 `ai.chat.deep_thinking_model`。这两个键只保存候选 ID，但会在用户没有显式选择 `preferredModel` 时抢占候选池优先级，导致“候选池 priority 是默认顺序”的规则不够直观。

## 目标

取消默认模型指针对运行时路由的影响。请求未显式指定 `preferredModel` 时，普通聊天、深度思考聊天和图片聊天都只按能力过滤后的候选池 `priority` 与候选 ID 排序。

## 范围

- 后端 `AiModelSelector` 不再读取或使用 `defaultModel` / `deepThinkingModel` 作为无显式首选时的排序指针。
- `DynamicAiProperties` 不再暴露 `ai.chat.default_model` 和 `ai.chat.deep_thinking_model` 的读取方法。
- 新库初始化不再写入这两个 setting 键；历史库通过新增迁移把这两个键标记删除。
- 管理端系统配置页不再特殊提升这两个键到 AI 基础配置；如果接口仍返回已删除或旧数据，页面按真实分类处理，不提供默认模型专用入口。
- 功能文档更新为候选池 priority 是默认路由顺序的唯一来源。

## 非目标

- 不删除候选池配置、provider 配置、首包超时或故障切换能力。
- 不改聊天请求的显式 `preferredModel` 语义；显式首选模型仍优先排序。
- 不重排现有候选池默认 priority。

## 核心流程

1. 用户发送聊天请求时，如果请求体没有传入 `preferredModel`，后端不会再读取默认模型 setting。`AiModelSelector` 先读取动态候选池，候选池不存在时回退静态候选骨架。
2. 选择器根据附件和 thinking 状态做能力过滤。图片附件存在且有视觉候选时只保留视觉候选；thinking 开启且有 thinking 候选时只保留 thinking 候选，没有 thinking 候选则回退普通候选。
3. 排序阶段只把非空 `preferredModel` 作为首选排序键。没有显式首选时，所有候选按 `priority` 升序、候选 ID 字典序排序。
4. `AiModelDispatchService` 按排序结果执行 provider 调用、首包探测、失败标记和 fallback。默认模型键被删除后，失败切换链路仍使用候选池顺序，不需要额外兜底。
5. 管理端系统配置页继续展示后端返回的普通配置项和候选池表格。因为初始化数据和清理迁移不再保留默认模型键，管理员通过候选池 `priority` 调整默认路由顺序。

## 数据迁移

新增迁移脚本将 `ai.chat.default_model` 与 `ai.chat.deep_thinking_model` 标记为 `deleted = 1`，避免历史数据库继续向管理端或运行时暴露默认模型指针。新库 `init.sql` 直接移除这两个键。

## 验证策略

- 后端单测验证无 `preferredModel` 时普通和 thinking 请求均按 priority 排序。
- 动态配置单测验证默认模型读取方法被移除，动态候选聚合仍正常。
- SQL 配置测试验证 `init.sql` 不再种默认模型键，并存在清理迁移。
- 管理端设置页测试验证默认模型配置项不再作为 AI 基础配置入口展示。
- 功能文档同步更新当前真实实现。
