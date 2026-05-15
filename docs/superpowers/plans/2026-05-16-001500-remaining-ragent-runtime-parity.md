# Remaining Ragent Runtime Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐 `ragent` 剩余的聊天运行时高价值能力，包括搜索后处理、文件存储抽象、后台管理面、运行时治理和用户前端思考/停止生成体验。

**Architecture:** 后端以聊天运行时为中心，抽出搜索通道层、后处理链、文件存储接口、token/清洗/线程池基础设施，并向管理端暴露 Trace/Intent/Mapping/Setting/Dashboard API；前端 admin/user 分别消费这些接口完成真实页面与聊天体验收口。

**Tech Stack:** Spring Boot 3.4, Java 21, MyBatis-Plus, Redis, PostgreSQL, React 19, TypeScript, Vitest, Chrome DevTools

---

### Task 1: 搜索通道化与后处理链

**Files:**
- Modify: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\application\service\WebSearchExecutionService.java`
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\application\service\SearchChannel.java`
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\application\service\SearchRequestContext.java`
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\application\service\SearchResultPostProcessor.java`
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\application\service\DeduplicationPostProcessor.java`
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\application\service\RerankPostProcessor.java`
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\application\service\TopKTruncationPostProcessor.java`
- Test: `D:\code\CodingX\backend\src\test\java\com\codingx\chat\application\service\WebSearchExecutionServiceTest.java`

- [ ] 先写失败测试验证多通道汇总、去重、rerank/截断顺序
- [ ] 跑受影响测试确认 RED
- [ ] 写最小实现转绿
- [ ] 重跑受影响测试

### Task 2: 文件存储抽象与产物改造

**Files:**
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\application\service\FileStorageService.java`
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\infrastructure\storage\LocalFileStorageService.java`
- Modify: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\application\service\DocumentArtifactService.java`
- Test: `D:\code\CodingX\backend\src\test\java\com\codingx\chat\application\service\DocumentArtifactServiceTest.java`

- [ ] 先写失败测试验证产物通过存储接口写入而非硬编码路径
- [ ] 跑测试确认 RED
- [ ] 写最小存储实现与产物服务改造
- [ ] 重跑测试确认 GREEN

### Task 3: token / 清洗 / 线程池治理

**Files:**
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\support\ai\TokenCounterService.java`
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\support\ai\HeuristicTokenCounterService.java`
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\support\ai\LlmResponseCleaner.java`
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\config\ChatExecutorConfig.java`
- Modify: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\application\service\ChatApplicationService.java`
- Modify: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\application\service\ChatStreamExecutionService.java`
- Test: `D:\code\CodingX\backend\src\test\java\com\codingx\support\ai\HeuristicTokenCounterServiceTest.java`
- Test: `D:\code\CodingX\backend\src\test\java\com\codingx\support\ai\LlmResponseCleanerTest.java`

- [ ] 先写失败测试锁定 token 估算与响应清洗行为
- [ ] 跑受影响测试确认 RED
- [ ] 写最小实现并切换线程池使用
- [ ] 重跑测试确认 GREEN

### Task 4: 后台 API 与管理页

**Files:**
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\interfaces\controller\AdminChatTraceController.java`
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\interfaces\controller\AdminChatIntentController.java`
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\interfaces\controller\AdminChatQueryTermMappingController.java`
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\interfaces\controller\AdminChatSettingsController.java`
- Create: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\interfaces\controller\AdminChatDashboardController.java`
- Create: `D:\code\CodingX\backend\src\main\resources\db\migration\V20260516_001600__create_chat_runtime_setting.sql`
- Modify/Create: `D:\code\CodingX\frontend\admin\src\App.tsx`
- Modify/Create: `D:\code\CodingX\frontend\admin\src\pages\Dashboard.tsx`
- Create: `D:\code\CodingX\frontend\admin\src\pages\TracePage.tsx`
- Create: `D:\code\CodingX\frontend\admin\src\pages\IntentTreePage.tsx`
- Create: `D:\code\CodingX\frontend\admin\src\pages\QueryTermMappingPage.tsx`
- Modify: `D:\code\CodingX\frontend\admin\src\pages\Settings.tsx`
- Tests: backend controller/service tests + frontend admin tests

- [ ] 先写失败测试锁定后台 API 契约
- [ ] 跑受影响测试确认 RED
- [ ] 写最小 API 与页面实现
- [ ] 重跑后后端/前端 admin 相关测试

### Task 5: 用户端 thinking / 停止生成 / CDP 验证

**Files:**
- Modify: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\domain\service\AiChatClient.java`
- Modify: `D:\code\CodingX\backend\src\main\java\com\codingx\chat\application\service\ChatApplicationService.java`
- Modify: `D:\code\CodingX\frontend\user\src\views\chat\useChatWorkspace.ts`
- Modify: `D:\code\CodingX\frontend\user\src\views\ChatView.tsx`
- Modify: `D:\code\CodingX\frontend\user\src\views\ChatView.test.tsx`

- [ ] 先写失败测试验证 SSE thinking 消费与停止生成 UI 状态
- [ ] 跑受影响测试确认 RED
- [ ] 写最小实现转绿
- [ ] 使用 `/web-access` + CDP 产出用户端与管理端截图证据
- [ ] 跑 `backend mvn compile/test`、`frontend/user npm run test:run/build`、`frontend/admin npm run test:run/build`
