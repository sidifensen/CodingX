# Redis 聊天队列门控

## 功能用途

Redis 聊天队列门控用于多实例部署时限制聊天执行并发，避免同一批请求同时占满后端执行资源。未启用 Redis 门控时，系统回退到进程内并发控制。

## 使用入口

- 配置开关：`APP_RUNTIME_USE_REDIS_QUEUE_GATE=true`
- 最大并发：`APP_RUNTIME_QUEUE_MAX_CONCURRENT`
- 获取超时：`APP_RUNTIME_QUEUE_ACQUIRE_TIMEOUT_MS`
- 租约时长：`APP_RUNTIME_QUEUE_LEASE_SECONDS`
- 续租间隔：`APP_RUNTIME_QUEUE_LEASE_RENEW_INTERVAL_MS`

## 核心流程

1. `ConversationQueueGate` 按会话 ID 写入 `chat:queue:waiting` 排队集合。
2. 当前排队名次进入并发窗口后，通过 `chat:queue:semaphore` 获取 Redisson 可过期许可。
3. 获取许可后移除排队成员，并通过 `chat:queue:notify` 发布唤醒通知。
4. 后台续租任务定期刷新活动会话许可，降低长会话被租约误释放的风险。
5. 会话完成或取消时释放许可，并再次发布队列唤醒通知。

## Redis Key

- `chat:queue:waiting`：等待队列，member 为会话 ID 字符串。
- `chat:queue:semaphore`：Redisson 可过期信号量。
- `{chat:queue:semaphore}:timeout`：Redisson 内部超时集合，用于记录 permit 过期时间。
- `chat:queue:notify`：队列唤醒 topic。

## 关键文件

- `backend/src/main/java/com/codingx/chat/infrastructure/runtime/ConversationQueueGate.java`：聊天队列门控主流程。
- `backend/src/main/java/com/codingx/config/RedissonConfig.java`：Redisson 客户端连接与序列化配置。
- `backend/src/test/java/com/codingx/chat/infrastructure/runtime/ConversationQueueGateTest.java`：队列获取、释放、续租行为测试。
- `backend/src/test/java/com/codingx/config/RedissonConfigTest.java`：Redisson 字符串 codec 配置测试。

## 序列化约束

Redisson 客户端必须使用 `StringCodec`。聊天队列门控写入 Redis 的 permit 与 member 都是字符串，使用文本 codec 可以保证 Redis 管理工具中显示为可读内容，避免默认二进制 codec 被误判为乱码。

## 验证方式

- 定向验证：`mvn -Dtest=RedissonConfigTest test`
- 后端编译：`mvn compile`
- 后端全量测试：`mvn test`
