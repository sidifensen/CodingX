# 聊天歧义引导配置化设计

## 背景

`D:\code\ragent` 的歧义引导通过 `rag.guidance` 配置控制开关、分数比值阈值、边界缓冲区和单次候选数量。`CodingX` 当前已在聊天意图路由中接入歧义澄清入口，但关键参数仍硬编码在 `ConversationIntentGuidanceService` 内，且候选处理逻辑集中在单个服务里，不利于后续维护和管理端配置。

本次目标是在不破坏现有聊天路由和其他会话未提交成果的前提下，尽量复用 `ragent` 的判定流程，并接入 `setting` 系统配置表，使管理员可以在“系统配置”中调整中文说明的参数。

## 方案选择

推荐采用“保留入口、拆分协作者、数据库配置化”的方案：

- 保留 `ConversationIntentService` 当前调用 `ConversationIntentGuidanceService.buildGuidancePrompt(...)` 的入口，不改聊天主流程。
- 将歧义候选排序、系统维度去重、显式系统名跳过、阈值/边界 LLM 复核、候选裁剪拆到独立类，避免继续堆在一个服务里。
- 在 `RuntimeSettingService` 新增歧义引导配置读取方法，默认值与 `ragent` 一致：启用、`0.8`、`0.15`、`6`。
- 通过迁移脚本和 `db/init.sql` 写入 `setting` 表，配置说明使用中文，管理端复用现有系统配置页展示和保存。

替代方案是只把硬编码常量改成配置读取，但会继续把候选选择、路径解析和 LLM 复核放在同一个类中，不满足“不要全部聚在一起”的要求。另一种方案是完整移植 `ragent` 的 `GuidanceProperties` 和 `GuidanceDecision` 类型，但 `CodingX` 已有运行时配置表和 `ConversationIntentDecision` 短路语义，直接照搬会产生重复抽象。

## 核心流程

1. 聊天意图识别返回候选列表后，`ConversationIntentGuidanceService` 先检查 `chat.intent.guidance.enabled`。
2. 歧义检测器按 `ragent` 思路过滤无效候选，并按“系统级节点”去重，每个系统只保留最高分候选。
3. 若用户问题已显式包含系统名，或第二名与第一名分数比值低于 `ambiguity_score_ratio - ambiguity_margin`，则跳过澄清。
4. 若比值达到 `ambiguity_score_ratio`，直接判定歧义；若处在边界区间，则用现有 LLM 提示词二次确认。
5. 命中歧义后按 `max_options` 裁剪候选，并用现有 `guidance-prompt.st` 渲染澄清提示返回给用户。

## 配置项

| 配置键 | 默认值 | 类型 | 中文说明 |
|---|---:|---|---|
| `chat.intent.guidance.enabled` | `true` | `BOOLEAN` | 是否启用聊天歧义引导 |
| `chat.intent.guidance.ambiguity_score_ratio` | `0.8` | `DECIMAL` | 歧义引导分数比值阈值 |
| `chat.intent.guidance.ambiguity_margin` | `0.15` | `DECIMAL` | 歧义引导边界缓冲宽度 |
| `chat.intent.guidance.max_options` | `6` | `INTEGER` | 歧义引导最大候选数量 |

## 文件边界

- `ConversationIntentGuidanceService`：仅负责开关判断、调用检测器和渲染提示词。
- `ConversationIntentAmbiguityDetector`：负责候选过滤、系统去重、阈值判断、LLM 二次确认和候选裁剪。
- `ConversationIntentPathResolver`：负责节点父链索引、完整路径、系统名和归一化匹配。
- `RuntimeSettingService`：新增 `DECIMAL` 读取能力和歧义引导配置方法。
- 数据库脚本：新增迁移脚本；同步补充 `db/init.sql` 基线数据。
- 管理端：仅补充分组中文标签，让新增配置在系统配置页以中文分类展示。

## 异常与边界

- 配置缺失时回退到 `ragent` 默认值，保证升级后无需立即配置即可工作。
- 配置值格式非法时沿用 `RuntimeSettingService` 的统一业务异常，避免不同页面重复解析错误。
- LLM 二次确认失败时按 `ragent` 降级策略触发澄清，优先避免歧义问题被系统硬答。
- 候选不足两个、候选节点为空、最高分无效、最大候选数小于两个时均不触发澄清。

## 验证策略

- 单元测试覆盖开关关闭、阈值直判、边界 LLM 复核、系统名显式跳过、候选数量裁剪和运行时配置默认值。
- 数据库结构测试覆盖迁移脚本和 `init.sql` 中的新增配置键、中文说明和类型。
- 管理端单元测试覆盖“歧义引导”中文分组展示。
- 完成后按本次后端和管理端前端改动执行 `mvn compile`、`mvn test`、`npm run build`、`npm run test:run`。
