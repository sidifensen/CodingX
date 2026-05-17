# Chat Queue Semaphore Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将聊天 Redis 并发门控升级为公平队列 + 租约续期 + 释放通知唤醒模型，并保持现有聊天调用链兼容。

**Architecture:** 以 `ConversationQueueGate` 为唯一并发门控入口，内部将 Redis 数据结构升级为 waiting/active 双 ZSet + Lua 原子认领；通过后台续租调度器延长长会话租约，通过发布通知降低等待线程轮询延迟。

**Tech Stack:** Spring Boot 3、StringRedisTemplate、Lua Script、JUnit5、Mockito

---

### Task 1: 用例先行锁定并发门控行为

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/infrastructure/runtime/ConversationQueueGateTest.java`

- [ ] **Step 1: 新增失败测试覆盖“通知唤醒 + 租约续期”**

```java
@Test
void redisReleasePublishesQueueNotify() { ... }

@Test
void redisActiveConversationCanRenewLease() { ... }
```

- [ ] **Step 2: 运行定向测试并确认 RED 状态**

Run: `mvn -Dtest=ConversationQueueGateTest test`
Expected: 编译/测试失败，提示缺失续租能力或新断言不满足

### Task 2: 升级 Redis 门控实现为信号量语义

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/runtime/ConversationQueueGate.java`

- [ ] **Step 1: 将 Redis 结构改为 waiting + active 双 ZSet，并实现原子认领 Lua**

```java
private static final String QUEUE_KEY = "chat:queue:waiting";
private static final String ACTIVE_ZSET_KEY = "chat:queue:active";
```

```lua
redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', nowMillis)
...
```

- [ ] **Step 2: 实现 release 广播通知与等待唤醒逻辑**

```java
private void publishQueueNotify() {
  notifyVersion.incrementAndGet();
  synchronized (redisWaitMonitor) {
    redisWaitMonitor.notifyAll();
  }
  stringRedisTemplate.convertAndSend(NOTIFY_CHANNEL, "permit_released");
}
```

- [ ] **Step 3: 实现会话续租接口与后台续租任务**

```java
public boolean renew(Long conversationId) { ... }
private void renewAllActiveLeases() { ... }
```

- [ ] **Step 4: 运行定向测试并确认 GREEN**

Run: `mvn -Dtest=ConversationQueueGateTest test`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

### Task 3: 接入配置与注释补齐

**Files:**
- Modify: `backend/src/main/java/com/codingx/config/RuntimeProperties.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: 增加续租配置项并补充业务注释**

```java
private long queueLeaseRenewIntervalMs = 10000L;
```

- [ ] **Step 2: 在 application.yml 声明运行时并发治理配置与默认值**

```yaml
app:
  runtime:
    use-redis-queue-gate: ${APP_RUNTIME_USE_REDIS_QUEUE_GATE:false}
    queue-lease-renew-interval-ms: ${APP_RUNTIME_QUEUE_LEASE_RENEW_INTERVAL_MS:10000}
```

### Task 4: 完整验证与提交准备

**Files:**
- Modify: `docs/superpowers/specs/2026-05-17-211942-chat-queue-semaphore-parity-design.md`
- Create: `docs/superpowers/acceptance/2026-05-17-211942-chat-queue-semaphore-parity-acceptance.md`
- Create: `docs/superpowers/plans/2026-05-17-211942-chat-queue-semaphore-parity.md`

- [ ] **Step 1: 执行后端编译验证**

Run: `mvn compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 2: 执行后端全量测试验证**

Run: `mvn test`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: 提交代码（中文前缀规范）**

```bash
git add backend/src/main/java/com/codingx/chat/infrastructure/runtime/ConversationQueueGate.java \
        backend/src/main/java/com/codingx/config/RuntimeProperties.java \
        backend/src/main/resources/application.yml \
        backend/src/test/java/com/codingx/chat/infrastructure/runtime/ConversationQueueGateTest.java \
        docs/superpowers/specs/2026-05-17-211942-chat-queue-semaphore-parity-design.md \
        docs/superpowers/plans/2026-05-17-211942-chat-queue-semaphore-parity.md \
        docs/superpowers/acceptance/2026-05-17-211942-chat-queue-semaphore-parity-acceptance.md
git commit -m "feat: 迁移聊天并发门控信号量与续租机制"
```
