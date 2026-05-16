# Backend Integrated MCP Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不新增独立服务的前提下，让 CodingX 后端内聚支持 `sales_query`、`ticket_query`、`weather_query` 三个 MCP 工具并通过回归测试。

**Architecture:** 复用现有 `ChatMcpToolExecutor` + `ChatMcpToolRegistry` 分发链路，新增三个执行器组件并通过关键词解析问题参数，返回结构化文本结果。保留 mock 销售执行器但默认关闭，避免工具标识冲突。

**Tech Stack:** Java 21, Spring Boot 3, Hutool, JUnit 5, Maven

---

### Task 1: 先写失败测试（RED）

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatMcpExecutionServiceTest.java`
- Create: `backend/src/test/java/com/codingx/chat/application/service/SalesMcpToolExecutorTest.java`
- Create: `backend/src/test/java/com/codingx/chat/application/service/TicketMcpToolExecutorTest.java`
- Create: `backend/src/test/java/com/codingx/chat/application/service/WeatherMcpToolExecutorTest.java`

- [ ] **Step 1: 修改 MCP 分发测试，依赖真实销售执行器类型**
- [ ] **Step 2: 新增销售/工单/天气执行器测试**
- [ ] **Step 3: 运行定向测试并确认编译失败（类不存在）**

Run:
```bash
mvn "-Dtest=SalesMcpToolExecutorTest,TicketMcpToolExecutorTest,WeatherMcpToolExecutorTest,ChatMcpExecutionServiceTest" test
```

Expected:
- `SalesMcpToolExecutor` / `TicketMcpToolExecutor` / `WeatherMcpToolExecutor` 找不到符号，构建失败。

### Task 2: 最小实现让测试通过（GREEN）

**Files:**
- Create: `backend/src/main/java/com/codingx/chat/application/service/SalesMcpToolExecutor.java`
- Create: `backend/src/main/java/com/codingx/chat/application/service/TicketMcpToolExecutor.java`
- Create: `backend/src/main/java/com/codingx/chat/application/service/WeatherMcpToolExecutor.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/MockSalesMcpToolExecutor.java`

- [ ] **Step 1: 新增 `SalesMcpToolExecutor` 并实现汇总/排名/明细/趋势分支**
- [ ] **Step 2: 新增 `TicketMcpToolExecutor` 并实现汇总/列表/统计分支**
- [ ] **Step 3: 新增 `WeatherMcpToolExecutor` 并实现当前天气/预报分支**
- [ ] **Step 4: 为 `MockSalesMcpToolExecutor` 增加条件开关，默认禁用**
- [ ] **Step 5: 运行定向测试并确认通过**

Run:
```bash
mvn "-Dtest=SalesMcpToolExecutorTest,TicketMcpToolExecutorTest,WeatherMcpToolExecutorTest,ChatMcpExecutionServiceTest" test
```

Expected:
- 7 个测试全部通过。

### Task 3: 同步本地 MCP 配置与全量验证

**Files:**
- Create: `.mcp.json`
- Create: `docs/superpowers/specs/2026-05-16-152653-backend-integrated-mcp-tools-design.md`
- Create: `docs/superpowers/acceptance/2026-05-16-152653-backend-integrated-mcp-tools-acceptance.md`
- Create: `docs/superpowers/plans/2026-05-16-152653-backend-integrated-mcp-tools.md`

- [ ] **Step 1: 新增根目录 `.mcp.json`（postgres/redis/chrome-devtools）**
- [ ] **Step 2: 运行后端编译与全量测试**

Run:
```bash
mvn compile
mvn test
```

Expected:
- 编译成功，全量测试通过。

### Task 4: 提交变更

**Files:**
- Modify/Create: 本次所有 MCP 相关代码与 superpowers 文档

- [ ] **Step 1: 检查变更只覆盖 MCP 相关文件，不触碰已有前端未提交改动**
- [ ] **Step 2: 执行 `git add` 与中文规范提交信息**

Commit message:
```text
feat: 后端内聚接入销售工单天气MCP工具
```
