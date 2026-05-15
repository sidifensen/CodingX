# Chat Model Engine Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 CodingX 聊天模型层从 provider 级 fallback 骨架升级为对齐 ragent 的模型级调度、首包探测、三态熔断与自动降级实现。

**Architecture:** 保留 `app.ai` 配置前缀与 `RoutingAiChatClient` 作为旧领域桥接层，在 `support.ai` 下新增模型目标、选择器、流式会话、首包等待器与三态健康注册表。路由层按“选择候选 -> 启动会话 -> 等待首包 -> 成功提交或失败 fallback”的顺序串联，应用层继续通过旧 `AiChatClient` 领域接口消费。

**Tech Stack:** Spring Boot 3.4, Java 21, Hutool, OkHttp, JUnit 5, Mockito

---

### Task 1: 写失败测试锁定模型选择与三态熔断

**Files:**
- Modify: `D:\code\CodingX\backend\src\test\java\com\codingx\support\ai\AiProviderHealthRegistryTest.java`
- Modify: `D:\code\CodingX\backend\src\test\java\com\codingx\support\ai\AiModelDispatchServiceTest.java`
- Create: `D:\code\CodingX\backend\src\test\java\com\codingx\support\ai\AiModelSelectorTest.java`

- [ ] **Step 1: 写失败测试覆盖三态熔断与候选排序**
- [ ] **Step 2: 运行受影响测试，确认出现预期失败**
- [ ] **Step 3: 实现最小模型选择器与三态熔断能力**
- [ ] **Step 4: 重新运行受影响测试，确认转绿**

### Task 2: 写失败测试锁定首包探测、超时回退与元信息透传

**Files:**
- Modify: `D:\code\CodingX\backend\src\test\java\com\codingx\support\ai\FirstTokenBufferingHandlerTest.java`
- Modify: `D:\code\CodingX\backend\src\test\java\com\codingx\chat\infrastructure\ai\RoutingAiChatClientTest.java`
- Modify: `D:\code\CodingX\backend\src\test\java\com\codingx\support\ai\OpenAiStyleStreamParserTest.java`
- Modify: `D:\code\CodingX\backend\src\test\java\com\codingx\support\ai\AiModelDispatchServiceTest.java`

- [ ] **Step 1: 写失败测试覆盖首包前错误 fallback、首包超时/无内容回退、thinking 事件识别、provider/model 元信息透传**
- [ ] **Step 2: 运行受影响测试，确认失败原因是能力缺失而非测试错误**
- [ ] **Step 3: 实现首包等待器、缓冲处理器、可取消流会话与路由桥接更新**
- [ ] **Step 4: 重新运行受影响测试，确认转绿**

### Task 3: 接入配置与 provider 客户端新抽象

**Files:**
- Modify: `D:\code\CodingX\backend\src\main\java\com\codingx\config\AiProperties.java`
- Modify: `D:\code\CodingX\backend\src\main\java\com\codingx\config\ChatAiConfig.java`
- Modify: `D:\code\CodingX\backend\src\main\resources\application.yml`
- Modify: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\infrastructure\ai\DeepSeekOkHttpChatClient.java`
- Modify: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\infrastructure\ai\StubAiChatClient.java`
- Modify: `D:\code\CodingX\backend\src\main\java\com\codingx\support\ai\AiProviderClient.java`
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\support\ai\AiModelTarget.java`
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\support\ai\AiModelSelector.java`
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\support\ai\AiStreamSession.java`
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\support\ai\FirstTokenAwaiter.java`

- [ ] **Step 1: 按 spec 调整配置对象与默认候选生成规则**
- [ ] **Step 2: 将 provider 抽象改为按 `AiModelTarget` 执行流式调用并返回可取消会话**
- [ ] **Step 3: 保持旧本地环境兼容，确保未配置新字段时仍可运行**
- [ ] **Step 4: 运行相关单测，确认配置与 provider 适配通过**

### Task 4: 更新聊天应用层元信息落库与失败收口

**Files:**
- Modify: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\domain\service\AiChatClient.java`
- Modify: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\infrastructure\ai\RoutingAiChatClient.java`
- Modify: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\application\service\ChatApplicationService.java`
- Modify: `D:\code\CodingX\backend\src\test\java\com\codingx\chat\application\service\ChatApplicationServiceTest.java`

- [ ] **Step 1: 写失败测试验证 assistant message 会落 provider/model，且 all-fail 通过 `onError` 收口**
- [ ] **Step 2: 运行测试并确认失败**
- [ ] **Step 3: 实现桥接层与应用层收口改造**
- [ ] **Step 4: 重跑受影响测试，确认转绿**

### Task 5: 全量后端验证与缺口登记

**Files:**
- Modify: `D:\code\CodingX\docs\project\chat-search-runtime-refactor\11-微任务执行清单.md`
- Modify: `D:\code\CodingX\docs\project\chat-search-runtime-refactor\10-实施计划与执行保障.md`

- [ ] **Step 1: 在项目计划文档中登记本轮已补齐与仍待完成的缺口**
- [ ] **Step 2: 运行 `mvn compile`**
- [ ] **Step 3: 运行 `mvn test`**
- [ ] **Step 4: 根据结果修正剩余问题后再次验证**
