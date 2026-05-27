# 本地工具运行时

## 功能用途

聊天模型可以调用后端已真实接入的 Codex 风格本地工具，在当前本地工作区读取信息、执行命令或应用补丁，同时避免暴露尚未接入真实运行时的占位工具。

## 使用入口

用户在聊天页启用工具能力并发送消息后，后端会把可见工具 schema 传给模型；模型返回 `tool_calls` 时由后端在绑定工作区执行，并通过 `tool-call` SSE 事件回传过程与结果。

## 核心流程

1. `ChatToolSpecService` 从启用的工具配置中过滤出 Java 执行器真实支持的本地工具。
2. `shell_command` 与 `exec_command` 的模型说明会显式写入命令解释器边界：Windows 环境使用 Windows PowerShell，不应使用 Bash 专属语法。
3. 聊天执行前通过 `ChatToolExecutionContext` 绑定当前本地工作区，工具结果元数据回显实际工作目录。
4. `CodexBuiltinChatToolExecutor` 执行命令、补丁、计划、图片读取等本地子集；未接入真实 Codex session 的多代理和插件工具保持不可用。
5. 工具输出会作为系统证据追加给下一轮模型生成，前端同步展示 start、complete 或 error 过程卡片。

## 关键文件

- `backend/src/main/java/com/codingx/tool/application/service/ChatToolSpecService.java`：生成模型可见工具 schema，并注入 PowerShell 语法约束。
- `backend/src/main/java/com/codingx/tool/application/service/CodexBuiltinChatToolExecutor.java`：执行已接入的 Codex 风格本地工具。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：把工具 schema 交给模型，执行模型工具调用并发布过程事件。
- `backend/src/main/resources/db/init.sql`：初始化内置工具配置。
- `backend/src/main/resources/db/migration/V20260527_153000__clarify_shell_command_windows_runtime.sql`：同步已有环境中的终端工具描述。

## 关键逻辑

`shell_command` 当前在 Windows 服务端通过 `powershell -NoProfile -Command` 执行，因此模型需要使用 `New-Item -ItemType Directory -Force`、`Set-Content` 或 `apply_patch` 等 PowerShell/工具原生命令。多行 HTML/XML/代码文件不应通过 `shell_command` 创建或编辑，应改用 `apply_patch`。必要时才用 PowerShell here-string 配合 `Set-Content`。`mkdir -p`、`cat <<EOF`、`&&` 串联和 `<` 输入重定向属于 Bash 习惯写法，其中 `<` 也是 PowerShell 保留字符，未正确引用会直接触发语法错误。

## 测试与验证

- `mvn -Dtest=ChatToolSpecServiceTest test`
- `mvn -Dtest=ChatRuntimePersistenceStructureTest#shellCommandRuntimeMigrationClarifiesPowerShellSyntax test`
