# Redisson String Codec Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让聊天队列门控写入 Redis 的字符串成员以可读文本展示，避免运维工具中出现二进制乱码。

**Architecture:** 在 `RedissonConfig` 显式设置 `StringCodec`，保持现有单节点 Redis 连接与队列门控逻辑不变。通过配置单测锁定 codec，防止后续回退到 Redisson 默认二进制序列化。

**Tech Stack:** Java 21, Spring Boot 3.4, Redisson 3.31, JUnit 5.

---

### Task 1: Redisson 配置可读序列化

**Files:**
- Create: `backend/src/test/java/com/codingx/config/RedissonConfigTest.java`
- Modify: `backend/src/main/java/com/codingx/config/RedissonConfig.java`
- Create: `docs/features/chat/redis-queue-gate.md`
- Modify: `docs/features/index.md`

- [ ] **Step 1: Write the failing test**

新增 `RedissonConfigTest`，构造 `RedisProperties` 并创建 `RedissonClient`，断言底层配置使用 `StringCodec`。测试结束关闭 client，避免线程泄漏。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=RedissonConfigTest test`

Expected: FAIL，当前配置未显式设置 `StringCodec`。

- [ ] **Step 3: Write minimal implementation**

在 `RedissonConfig#redissonClient` 创建 `Config` 后调用 `config.setCodec(StringCodec.INSTANCE)`，并补充注释说明业务意图：聊天队列门控只写入字符串 permit/member，使用文本 codec 便于排查 Redis 运行态。

- [ ] **Step 4: Update feature docs**

新增聊天 Redis 队列门控功能文档，说明用途、入口、Redis key、可读序列化约束与验证方式；在 `docs/features/index.md` 增加索引。

- [ ] **Step 5: Verify**

Run: `mvn -Dtest=RedissonConfigTest test`

Expected: PASS。

Run: `mvn compile`

Expected: PASS。

Run: `mvn test`

Expected: PASS。
