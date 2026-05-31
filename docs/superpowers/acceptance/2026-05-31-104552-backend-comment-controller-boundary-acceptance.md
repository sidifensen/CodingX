# Acceptance Criteria: 后端注释与 Controller 职责边界

**Spec:** `docs/superpowers/specs/2026-05-31-104552-backend-comment-controller-boundary-design.md`
**Date:** 2026-05-31
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | `AGENTS.md` 明确记录后端依赖字段、数据载体字段、方法步骤和 Controller 分层注释规范。 | Logic | 打开仓库根目录 `AGENTS.md`。 | 文件包含“后端注释细则”，且明确依赖字段、VO/DTO/PO/DO/Request/Response 字段、方法步骤注释和 Controller 不写业务逻辑。 |
| AC-002 | `ChatController` 不直接持有 domain repository、infrastructure repository 或 persistence data object 依赖。 | Logic | 编译测试源码。 | `ChatControllerConversationMutationTest#controllerDoesNotDependOnRepositoriesOrPersistenceInfrastructure` 反射检查非法字段列表为空。 |
| AC-003 | 同步发送消息入口只由 Controller 接收 HTTP 参数，能力编码规整、启用 MCP 收敛和门控释放由 service 执行。 | Logic | 调用 `ChatController#sendMessage`。 | Controller 调用服务层发送方法，异常和成功路径均由服务层释放会话门控，接口返回 `CHAT_MESSAGE_PROCESSED`。 |
| AC-004 | 会话和消息响应组装从 Controller 下沉到 service，公开分享页不依赖登录态查询用户投票。 | Logic | 调用公开分享页回放接口。 | Controller 从 service 获取响应对象；匿名公开消息响应的 `userVote` 为空，且不会读取当前登录用户。 |
| AC-005 | 本批次触碰的 `Request` / `Response` / `DO` 字段均有业务语义注释。 | Logic | 检查本批次修改的 Java 数据载体文件。 | 每个字段旁包含说明字段语义、来源或状态含义的注释。 |
| AC-006 | 本批次触碰的 service 核心方法包含步骤注释，能说明输入、关键判断、状态变化和输出。 | Logic | 检查本批次修改的 service 方法体。 | 方法体关键链路包含“步骤 1 / 步骤 2 / 步骤 3”等注释，且注释与真实处理顺序一致。 |

## Coverage Check

以上验收覆盖规范写入、Controller 依赖边界、同步发送职责下沉、响应组装职责下沉、数据载体字段注释和 service 方法步骤注释。数据库、前端视觉和浏览器验证不属于本批次范围。
