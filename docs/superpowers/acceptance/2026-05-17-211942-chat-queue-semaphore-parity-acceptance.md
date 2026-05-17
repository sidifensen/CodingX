# Chat Queue Semaphore Parity Acceptance

## 验收范围

本次验收仅覆盖后端聊天并发门控能力迁移，不包含前端界面与数据库结构变更。

## 功能验收项

1. Redis 门控支持公平排队与并发上限控制：
- 并发数达到 `queue-max-concurrent` 时，新请求进入等待并在超时后返回 `busy`
- 已占位会话重复进入时可直接放行，不重复占位

2. Redis 门控支持租约续期：
- 活跃会话在租约期内可通过 `renew` 成功续期
- 续租失败会清理本地活跃跟踪，避免假活跃

3. 释放后存在唤醒通知：
- `release` 后必须发布 `chat:queue:notify` 频道通知
- 同节点等待线程可提前被唤醒，不必完全依赖固定轮询间隔

4. 单机模式兼容性：
- 关闭 `use-redis-queue-gate` 时继续走原进程内门控逻辑
- 不引入对上层调用方的接口变更

## 测试验收项

1. 定向测试通过：
- `ConversationQueueGateTest` 包含并通过以下测试：
  - `acquireRejectsWhenConcurrencyLimitReached`
  - `releaseAllowsNextConversationToProceed`
  - `redisWaiterCanProceedAfterPermitReleased`
  - `redisReleasePublishesQueueNotify`
  - `redisActiveConversationCanRenewLease`

2. 后端编译通过：
- 执行 `mvn compile` 返回 `BUILD SUCCESS`

3. 后端全量测试通过：
- 执行 `mvn test` 返回 `BUILD SUCCESS`

## 交付验收项

1. 代码与配置更新完整：
- `ConversationQueueGate` 完成 Redis 双 ZSet + 续租 + 通知迁移
- `RuntimeProperties` 增加续租配置项
- `application.yml` 增加并发门控配置说明与默认值

2. 文档完整：
- 已新增 design/plan/acceptance 三类文档并落盘到 `docs/superpowers/`

3. 提交规范满足：
- 提交信息为中文，且使用允许前缀（`feat`）
