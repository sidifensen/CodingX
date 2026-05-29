# 本地工具运行时

## 功能用途

聊天模型可以调用后端已真实接入的 Codex 风格本地工具，在当前本地工作区读取信息、执行命令、应用补丁、维护计划与目标，或使用进程内子代理状态工具完成调试链路。

## 使用入口

用户在聊天页启用工具能力并发送消息后，后端会把可见工具 schema 传给模型；模型返回 `tool_calls` 时由后端在绑定工作区执行，并通过 `tool-call` SSE 事件回传过程与结果。

用户在输入框中显式选择 `@技能` 后，后端会把该技能的 `SKILL.md` 注入本轮模型系统上下文。显式技能选择本身会被视为当前任务意图的一部分，避免“这是啥”这类短句被普通闲聊或关于助手意图吞掉。

## 核心流程

1. `ChatToolSpecService` 从启用的工具配置中过滤出 Java 执行器真实支持的本地工具。
2. `shell_command` 与 `exec_command` 的模型说明会显式写入命令解释器边界：Windows 环境使用 Windows PowerShell，不应使用 Bash 专属语法。
3. `apply_patch` 的模型说明要求补丁路径基于当前工具工作目录，优先使用 `diary/index.html` 这类相对路径，不应编造 `C:\workspace` 等虚拟根目录。
4. 聊天执行前通过 `ChatToolExecutionContext` 绑定当前本地工作区，工具结果元数据回显实际工作目录。
5. `CodexBuiltinChatToolExecutor` 执行命令、补丁、计划、资源读取、图片读取、目标状态、权限申请、插件申请与进程内子代理状态工具；标准 diff 中若出现当前工作区内的绝对路径，会在应用前规范化为相对路径，工作区外路径继续拒绝。
6. 工具输出会作为系统证据追加给下一轮模型生成，证据中单独写出真实 `workingDirectory` 和后续路径约束；前端同步展示 start、complete 或 error 过程卡片。
7. `ChatSkillContextService` 读取已选技能说明并加入系统提示；若缺少 URL、页面、附件等必要目标，模型应围绕已选技能追问或尝试获取上下文，而不是转成普通闲聊回答。

## 关键文件

- `backend/src/main/java/com/codingx/tool/application/service/ChatToolSpecService.java`：生成模型可见工具 schema，并注入 PowerShell 语法约束。
- `backend/src/main/java/com/codingx/tool/application/service/CodexBuiltinChatToolExecutor.java`：执行已接入的 Codex 风格本地工具。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：把工具 schema 交给模型，执行模型工具调用并发布过程事件。
- `backend/src/main/java/com/codingx/skill/application/service/ChatSkillContextService.java`：读取已选技能说明，并声明显式技能选择的任务意图约束。
- `backend/src/main/resources/db/init.sql`：初始化内置工具配置。
- `backend/src/main/resources/db/migration/V20260527_153000__clarify_shell_command_windows_runtime.sql`：同步已有环境中的终端工具描述。
- `backend/src/main/resources/db/migration/V20260529_020000__enable_codex_runtime_tools.sql`：启用历史环境中已接入的 Codex 运行时工具配置。

## 关键逻辑

`shell_command` 当前在 Windows 服务端通过 `powershell -NoProfile -Command` 执行，因此模型需要使用 `New-Item -ItemType Directory -Force`、`Set-Content` 或 `apply_patch` 等 PowerShell/工具原生命令。多行 HTML/XML/代码文件不应通过 `shell_command` 创建或编辑，应改用 `apply_patch`。必要时才用 PowerShell here-string 配合 `Set-Content`。`mkdir -p`、`cat <<EOF`、`&&` 串联和 `<` 输入重定向属于 Bash 习惯写法，其中 `<` 也是 PowerShell 保留字符，未正确引用会直接触发语法错误。

`apply_patch` 以当前工具工作目录为写入边界。模型如果拿到工具输出中的真实工作目录，例如 `D:\`，后续补丁应写 `diary/index.html` 或工作目录内的绝对路径；如果补丁指向 `C:\workspace\...` 这类不属于当前工作目录的路径，后端会按越界路径拒绝执行，避免误写用户未授权目录。标准 diff 新增文件块里若模型漏写 hunk 内容行开头的 `+`，执行器只会在 `--- /dev/null` 且旧文件行号为 0 的新增文件 hunk 内补齐新增标记，避免误改普通修改补丁。若模型把已存在的同名普通文件继续写成 `new file mode`，执行器会把该新增块转换为整文件替换补丁，支持用户反复生成 `weather.html` 这类单文件页面；即使该补丁缺少 `diff --git` 文件块头，或 `+++ b/file` 文件头携带制表符分隔的时间戳，仍会先剥离元数据并进入同名覆盖兜底，避免 `already exists in working directory` 阻断工具链路。

`spawn_agent`、`send_input`、`wait_agent`、`close_agent`、`resume_agent`、`list_agents`、`spawn_agents_on_csv` 与 `report_agent_job_result` 当前提供后端进程内状态模拟，用于验证模型工具编排、管理端探测和链路调试，不启动真实外部 Codex 子进程。历史数据库若仍保留这些工具的禁用状态，会由迁移脚本统一启用，避免接口层在执行器可用时返回“工具已禁用”。

## 测试与验证

- `mvn -Dtest=ChatToolSpecServiceTest test`
- `mvn -Dtest=CodexBuiltinChatToolExecutorTest#applyPatchShouldNormalizeWorkspaceAbsolutePathInGitDiff test`
- `mvn -Dtest=CodexBuiltinChatToolExecutorTest#applyPatchShouldOverwriteExistingFileWhenNewFileDiffTargetsSameName test`
- `mvn -Dtest=CodexBuiltinChatToolExecutorTest#applyPatchShouldOverwriteExistingFileWhenStandaloneNewFileDiffTargetsSameName test`
- `mvn -Dtest=CodexBuiltinChatToolExecutorTest#applyPatchShouldOverwriteExistingFileWhenNewFileDiffHeaderHasTimestamp test`
- `mvn -Dtest=CodexBuiltinChatToolExecutorTest#codexRuntimeToolsShouldBeCallableThroughExecutorEntry test`
- `mvn -Dtest=ChatRuntimePersistenceStructureTest#codexRuntimeToolMigrationEnablesImplementedTools test`
- `mvn -Dtest=ChatRuntimePersistenceStructureTest#shellCommandRuntimeMigrationClarifiesPowerShellSyntax test`
- `mvn -Dtest=ChatSkillContextServiceTest test`
