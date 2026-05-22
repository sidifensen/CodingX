# 天气城市助词归一化实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复“北京的天气怎么样”被解析为“北京的”导致天气坐标查询失败的问题，并建立回归测试。

**Architecture:** 在 `WeatherMcpToolExecutor` 的城市提取链路中补充“天气关键词前可选助词”与“尾部助词归一化”两层防护，先通过失败测试锁定回归场景，再以最小代码修改让测试转绿，最后执行后端编译与测试验证。

**Tech Stack:** Java 21、Spring Boot、JUnit 5、Maven、Hutool

---

### Task 1: 编写失败测试（RED）

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/WeatherMcpToolExecutorTest.java`
- Test: `backend/src/test/java/com/codingx/chat/application/service/WeatherMcpToolExecutorTest.java`

- [x] **Step 1: 新增“北京的天气怎么样”回归测试**
- [x] **Step 2: 运行单测并确认失败，失败原因为 city 解析为“北京的”**

### Task 2: 最小实现修复（GREEN）

**Files:**
- Modify: `backend/src/main/java/com/codingx/mcp/application/service/WeatherMcpToolExecutor.java`
- Test: `backend/src/test/java/com/codingx/chat/application/service/WeatherMcpToolExecutorTest.java`

- [x] **Step 1: 在城市提取正则中兼容“的天气”表达**
- [x] **Step 2: 新增城市归一化方法，剔除尾部助词“的”**
- [x] **Step 3: 运行新增单测并确认通过**

### Task 3: 全量验证与交付准备

**Files:**
- Modify: `docs/superpowers/specs/2026-05-22-174535-weather-city-particle-normalization-design.md`
- Modify: `docs/superpowers/acceptance/2026-05-22-174535-weather-city-particle-normalization-acceptance.md`
- Modify: `docs/superpowers/plans/2026-05-22-174535-weather-city-particle-normalization.md`

- [x] **Step 1: 执行 `mvn compile`**
- [x] **Step 2: 执行 `mvn test`**
- [x] **Step 3: 汇总验证结果并准备提交**
