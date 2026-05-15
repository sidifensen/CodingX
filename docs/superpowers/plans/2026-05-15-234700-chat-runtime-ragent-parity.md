# Chat Runtime Ragent Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `CodingX` 当前聊天运行时补齐到可用的 `ragent` 运行时子集，覆盖队列门控、Trace 自动采集、改写意图链路、MCP 工具链路与示例问题欢迎屏。

**Architecture:** 后端以 `ChatApplicationService` 维持主编排，在 `ConversationQueueGate`、`ConversationTraceAspect`、`ConversationIntent*`、`ChatMcp*` 与 `ChatSampleQuestion*` 上补能力；前端 `useChatWorkspace` 负责欢迎态示例问题与聊天工作区聚合。数据库通过增量迁移和种子回填保证运行态与代码同步。

**Tech Stack:** Spring Boot 3.4, Java 21, MyBatis-Plus, Redis, PostgreSQL, React, TypeScript, Vitest

---

### Task 1: 锁定 Redis 队列门控并发缺陷

**Files:**
- Modify: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\infrastructure\runtime\ConversationQueueGate.java`
- Modify: `D:\code\CodingX\backend\src\test\java\com\codingx\chat\infrastructure\runtime\ConversationQueueGateTest.java`

- [x] 写回归测试证明 Redis 轮询等待会阻塞 release
- [x] 调整门控实现，禁止 Redis 轮询持有 JVM 级锁
- [x] 重跑 `mvn -Dtest=ConversationQueueGateTest test`

### Task 2: 补齐 MCP 意图运行态结构与种子回填

**Files:**
- Modify: `D:\code\CodingX\backend\src\main\resources\db\schema.sql`
- Modify: `D:\code\CodingX\backend\src\main\resources\db\init.sql`
- Modify: `D:\code\CodingX\backend\src\main\resources\db\migration\V20260515_221800__extend_chat_intent_node_for_mcp.sql`

- [x] 为 `chat_intent_node` 增加 `mcp_tool_id` / `param_prompt_template`
- [x] 回填 `sales` / `sales-data` 启用状态与工具配置
- [x] 对当前本地数据库执行一次结构与数据补齐

### Task 3: 收口聊天运行时能力与欢迎屏

**Files:**
- Modify: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\application\service\ConversationTraceAspect.java`
- Modify: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\application\service\ChatMcpExecutionService.java`
- Modify: `D:\code\CodingX\frontend\user\src\views\chat\types.ts`
- Modify: `D:\code\CodingX\frontend\user\src\App.test.tsx`

- [x] 接入 `@ConversationTraceNode` 自动采集
- [x] 接入 MCP 执行服务和示例问题接口/类型
- [x] 确认欢迎屏示例问题与测试桩一致

### Task 4: 运行验证与缺口登记

**Files:**
- Modify: `D:\code\CodingX\docs\project\chat-search-runtime-refactor\10-实施计划与执行保障.md`
- Modify: `D:\code\CodingX\docs\project\chat-search-runtime-refactor\11-微任务执行清单.md`
- Create: `D:\code\CodingX\docs\superpowers\specs\2026-05-15-234700-chat-runtime-ragent-parity-design.md`
- Create: `D:\code\CodingX\docs\superpowers\acceptance\2026-05-15-234700-chat-runtime-ragent-parity-acceptance.md`
- Create: `D:\code\CodingX\docs\superpowers\plans\2026-05-15-234700-chat-runtime-ragent-parity.md`

- [x] 更新阶段状态与微任务勾选结果
- [x] 验证示例问题接口
- [x] 验证 `销售总额是多少` 的 MCP SSE 链路
- [x] 验证 `请介绍一下 OA 系统` 的搜索 SSE 链路
- [ ] 运行 `mvn compile`
- [ ] 运行 `mvn test`
- [ ] 运行 `npm run test:run`
- [ ] 运行 `npm run build`
- [ ] 梳理仍未迁移的 `ragent` 能力并形成后续列表
