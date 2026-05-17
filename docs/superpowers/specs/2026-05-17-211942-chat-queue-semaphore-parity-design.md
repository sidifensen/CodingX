# Chat Queue Gate Semaphore Parity Design

## 背景

用户要求从 `D:\code\ragent` 迁移可直接复用的高价值运行时能力，并明确点名“限流并发、信号量”等并发治理能力。`CodingX` 现有 `ConversationQueueGate` 已具备基础排队门控，但在 Redis 模式下仍属于轻量轮询实现：

- 依赖 active count + 轮询，缺少明确的租约续期语义
- 释放后仅依赖下一次轮询重试，等待线程唤醒不够及时
- 对长时流式会话的兜底保护不足，可能出现租约误回收风险

## 目标

1. 在不改动上层聊天业务接口前提下，将 Redis 门控升级为“公平队列 + 可过期信号量语义”。
2. 增加租约续期能力，保障长会话并发占位可靠性。
3. 增加释放唤醒通知，降低无效轮询等待。
4. 保持单机模式行为兼容，避免影响未开启 Redis 门控的部署。

## 方案

### 1. Redis 门控模型升级

将 `ConversationQueueGate` 的 Redis 实现从“active-count + 单会话 key”升级为“waiting zset + active zset”双集合模型：

- `chat:queue:waiting`：按入队时间戳排序，保证排队公平性
- `chat:queue:active`：分数存储租约到期时间，表示当前活跃 permit

通过 Lua 原子脚本完成：

1. 清理过期 active 项
2. 处理同会话重入（会话已在 active 中时直接续期并放行）
3. 入队并判定 rank + active 数量
4. 满足条件时从 waiting 认领到 active

### 2. 租约续期机制

新增续租接口与后台续租任务：

- `ConversationQueueGate.renew(conversationId)`：对 active 会话续租
- 调度器按 `queue-lease-renew-interval-ms` 周期扫描当前节点活跃会话并续租

这样即使 SSE 会话处理时长超过默认租约窗口，也能避免 permit 被误释放。

### 3. 释放通知与等待唤醒

新增双层唤醒机制：

- 本节点：`notifyAll` + 版本号，提前打断等待窗口
- 跨节点：Redis Pub/Sub `chat:queue:notify` 广播 `permit_released`

等待线程在每轮等待前记录通知版本，释放后可立即重试，减少纯轮询抖动。

### 4. 配置与兼容

在 `app.runtime` 下增加并声明以下配置（全部提供默认值）：

- `use-redis-queue-gate`
- `queue-max-concurrent`
- `queue-acquire-timeout-ms`
- `queue-poll-interval-ms`
- `queue-lease-seconds`
- `queue-lease-renew-interval-ms`

单机模式仍走原进程内门控，不依赖 Redis。

## 测试策略

对 `ConversationQueueGateTest` 增加并维持以下关键断言：

1. 并发上限触发 `busy` 拒绝
2. 释放后后继会话可继续执行
3. Redis 模式等待者在释放后可推进
4. Redis 释放触发唤醒通知发布
5. 活跃会话可续租成功

## 风险与边界

- 当前跨节点“通知订阅”采用发布优先策略，若某节点通知短暂丢失，仍由轮询兜底，不会导致永久阻塞。
- 租约续期仅针对本节点已认领会话，若节点崩溃将依赖租约到期自动回收，符合分布式容错预期。
- 本次不改动上层业务异常语义与前端协议，仅增强运行时并发治理能力。
