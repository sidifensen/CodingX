package com.codingx.tool.application.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvReader;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.codingx.chat.application.service.goal.ChatGoalService;
import com.codingx.chat.application.service.goal.ChatGoalView;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.tool.domain.model.ChatTool;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.tool.domain.repository.ChatToolRepository;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 提供 Codex CLI 同名内置工具在本服务内的执行能力。
 * <p>
 * 说明：此执行器覆盖 chat_tool 预置的工具编码，所有能力均在后端进程内完成，
 * 不依赖 MCP 协议转发，便于管理端做可见性、探测与调试调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodexBuiltinChatToolExecutor implements ChatToolExecutor {

    private static final List<String> TOOL_CODES = List.of(
        "read", "write", "edit", "bash", "grep", "find", "ls",
        "shell_command", "apply_patch", "git_diff", "list_mcp_resources", "list_mcp_resource_templates", "read_mcp_resource",
        "update_plan", "request_user_input", "view_image", "spawn_agent", "send_input", "wait_agent", "close_agent",
        "resume_agent", "tool_search", "request_plugin_install", "request_permissions", "exec_command", "write_stdin",
        "get_goal", "create_goal", "update_goal", "send_message", "followup_task", "list_agents", "spawn_agents_on_csv",
        "report_agent_job_result", "test_sync_tool"
    );
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_BUFFER_LENGTH = 12000;
    private static final Pattern GIT_HUNK_HEADER_PATTERN = Pattern.compile(
        "^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@(.*)$"
    );

    /** 工具配置仓储，用于内置工具读取管理端工具配置和启用态。 */
    private final ChatToolRepository chatToolRepository;
    /** MCP 配置仓储，用于内置 MCP 资源工具模拟 MCP 清单读取。 */
    private final ChatMcpRepository chatMcpRepository;
    /** 技能仓储，用于工具搜索时返回当前系统已配置技能。 */
    private final ChatSkillRepository chatSkillRepository;
    /** 目标应用服务，用于 get_goal/create_goal/update_goal 读写数据库权威目标状态。 */
    private final ChatGoalService chatGoalService;

    /** 子代理会话缓存，用于模拟 spawn/wait/send/close 等代理生命周期操作。 */
    private final Map<String, AgentSession> agentSessions = new ConcurrentHashMap<>();
    /** 后台命令会话缓存，用于 exec_command 与 write_stdin 之间复用进程句柄。 */
    private final Map<String, CommandSession> commandSessions = new ConcurrentHashMap<>();
    /** 计划步骤缓存，用于 update_plan 工具保存会话级任务进度。 */
    private final Map<String, List<PlanStep>> plans = new ConcurrentHashMap<>();
    /** 插件安装请求记录，用于返回 request_plugin_install 调用历史。 */
    private final List<Map<String, Object>> pluginInstallRequests = new CopyOnWriteArrayList<>();
    /** 权限请求记录，用于返回 request_permissions 调用历史。 */
    private final List<Map<String, Object>> permissionRequests = new CopyOnWriteArrayList<>();
    /** 子任务上报记录，用于 report_agent_job_result 工具保存异步结果。 */
    private final List<Map<String, Object>> jobReports = new CopyOnWriteArrayList<>();
    @Override
    public List<String> toolCodes() {
        return TOOL_CODES;
    }

    @Override
    public ChatToolExecutionResult execute(String toolCode, String question) {
        String normalizedCode = normalizeToolCode(toolCode);
        ToolInput input = parseInput(question);
        return switch (normalizedCode) {
            case "read" -> executeRead(input);
            case "write" -> executeWrite(input);
            case "edit" -> executeEdit(input);
            case "bash" -> executeBash(input);
            case "grep" -> executeGrep(input);
            case "find" -> executeFind(input);
            case "ls" -> executeLs(input);
            case "shell_command" -> executeShellCommand(input);
            case "exec_command" -> executeExecCommand(input);
            case "write_stdin" -> executeWriteStdin(input);
            case "apply_patch" -> executeApplyPatch(input);
            case "git_diff" -> executeGitDiff(input);
            case "list_mcp_resources" -> executeListMcpResources();
            case "list_mcp_resource_templates" -> executeListMcpResourceTemplates();
            case "read_mcp_resource" -> executeReadMcpResource(input);
            case "update_plan" -> executeUpdatePlan(input);
            case "request_user_input" -> executeRequestUserInput(input);
            case "view_image" -> executeViewImage(input);
            case "spawn_agent" -> executeSpawnAgent(input);
            case "send_input", "send_message" -> executeSendInput(input);
            case "wait_agent" -> executeWaitAgent(input);
            case "close_agent" -> executeCloseAgent(input);
            case "resume_agent" -> executeResumeAgent(input);
            case "tool_search" -> executeToolSearch(input);
            case "request_plugin_install" -> executeRequestPluginInstall(input);
            case "request_permissions" -> executeRequestPermissions(input);
            case "get_goal" -> executeGetGoal(input);
            case "create_goal" -> executeCreateGoal(input);
            case "update_goal" -> executeUpdateGoal(input);
            case "followup_task" -> executeFollowupTask(input);
            case "list_agents" -> executeListAgents();
            case "spawn_agents_on_csv" -> executeSpawnAgentsOnCsv(input);
            case "report_agent_job_result" -> executeReportAgentJobResult(input);
            case "test_sync_tool" -> executeTestSyncTool(input);
            default -> throw new BusinessException("CHAT_TOOL_NOT_SUPPORTED", ErrorMessageCatalog.CHAT_TOOL_UNSUPPORTED);
        };
    }

    /**
     * 执行单次命令并返回输出。
     */
    private ChatToolExecutionResult executeShellCommand(ToolInput input) {
        String command = extractCommand(input);
        if (StrUtil.isBlank(command)) {
            throw new BusinessException("CHAT_TOOL_INVALID_COMMAND", ErrorMessageCatalog.CHAT_TOOL_COMMAND_REQUIRED);
        }
        long timeoutMs = extractTimeoutMs(input);
        Path workingDirectory = resolveToolWorkingDirectory();
        CommandExecution execution = runCommand(command, timeoutMs, workingDirectory);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("command", command);
        metadata.put("timeoutMs", timeoutMs);
        metadata.put("exitCode", execution.exitCode());
        metadata.put("timedOut", execution.timedOut());
        metadata.put("durationMs", execution.durationMs());
        metadata.put("workingDirectory", workingDirectory.toString());
        return new ChatToolExecutionResult(
            "shell_command",
            execution.output(),
            metadata
        );
    }

    /**
     * OpenClaw 风格 bash 工具：对模型暴露短名称，内部复用统一命令执行边界。
     * @param input 工具输入。
     * @return 命令执行结果。
     */
    private ChatToolExecutionResult executeBash(ToolInput input) {
        ChatToolExecutionResult result = executeShellCommand(input);
        return new ChatToolExecutionResult("bash", result.content(), result.metadata());
    }

    /**
     * 启动后台命令会话，返回 sessionId 供 write_stdin 使用。
     */
    private ChatToolExecutionResult executeExecCommand(ToolInput input) {
        // 步骤 1：解析并校验命令文本，空命令直接返回中文业务错误。
        String command = extractCommand(input);
        if (StrUtil.isBlank(command)) {
            throw new BusinessException("CHAT_TOOL_INVALID_COMMAND", ErrorMessageCatalog.CHAT_TOOL_COMMAND_REQUIRED);
        }
        try {
            // 步骤 2：在当前工具工作目录启动真实进程，并注入已绑定 skill 目录环境变量。
            ProcessBuilder pb = new ProcessBuilder(resolveShellCommand(command));
            pb.directory(resolveToolWorkingDirectory().toFile());
            injectSkillEnvironmentVariables(pb.environment());
            Process process = pb.start();
            // 步骤 3：登记可交互会话和 stdin writer，后续 write_stdin 通过 sessionId 找回进程。
            String sessionId = "cmd-" + UUID.fastUUID().toString(true);
            CommandSession session = new CommandSession(
                sessionId,
                command,
                process,
                new BufferedWriter(process.outputWriter(StandardCharsets.UTF_8)),
                new StringBuilder(),
                LocalDateTime.now()
            );
            commandSessions.put(sessionId, session);
            // 步骤 4：异步监听 stdout/stderr，避免交互进程输出缓冲区阻塞。
            watchProcessOutput(session, process.getInputStream());
            watchProcessOutput(session, process.getErrorStream());
            // 步骤 5：返回 sessionId、工作目录和启动状态，供模型继续写入输入或检查结果。
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sessionId", sessionId);
            metadata.put("command", command);
            metadata.put("status", "running");
            metadata.put("createdAt", DATE_TIME_FORMATTER.format(session.createdAt()));
            metadata.put("workingDirectory", resolveToolWorkingDirectory().toString());
            return new ChatToolExecutionResult(
                "exec_command",
                "命令已启动，可通过 write_stdin 写入交互输入",
                metadata
            );
        } catch (Exception exception) {
            throw new BusinessException(
                "CHAT_TOOL_EXEC_COMMAND_FAILED",
                ErrorMessageCatalog.CHAT_TOOL_EXEC_COMMAND_START_FAILED_PREFIX + exception.getMessage()
            );
        }
    }

    /**
     * 向后台命令会话写入标准输入。
     */
    private ChatToolExecutionResult executeWriteStdin(ToolInput input) {
        // 步骤 1：从 JSON 或自然语言文本中解析 sessionId，并确认会话仍在注册表中。
        String sessionId = prefer(input.object().getStr("sessionId"), ReUtil.get("sessionId[:=]\\s*([\\w-]+)", input.raw(), 1));
        if (StrUtil.isBlank(sessionId)) {
            throw new BusinessException("CHAT_TOOL_SESSION_REQUIRED", ErrorMessageCatalog.CHAT_TOOL_SESSION_ID_REQUIRED);
        }
        CommandSession session = commandSessions.get(sessionId);
        if (session == null) {
            throw new BusinessException("CHAT_TOOL_SESSION_NOT_FOUND", ErrorMessageCatalog.CHAT_TOOL_SESSION_NOT_FOUND);
        }
        // 步骤 2：解析要写入的文本，空输入直接拒绝，避免向交互进程发送无意义换行。
        String text = prefer(input.object().getStr("text"), ReUtil.get("text[:=]\\s*(.+)", input.raw(), 1));
        if (StrUtil.isBlank(text)) {
            throw new BusinessException("CHAT_TOOL_TEXT_REQUIRED", ErrorMessageCatalog.CHAT_TOOL_TEXT_REQUIRED);
        }
        try {
            // 步骤 3：写入一行 stdin 并刷新，按调用参数可等待进程输出稳定。
            session.writer().write(text);
            session.writer().newLine();
            session.writer().flush();
            waitForInteractiveCommandIfRequested(session, normalizeWaitMs(input.object().getLong("waitMs", 0L)));
            // 步骤 4：返回会话存活状态、输出快照和可选退出码，供模型判断下一步动作。
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sessionId", sessionId);
            metadata.put("alive", session.process().isAlive());
            metadata.put("output", snapshotOutput(session.outputBuffer()));
            if (!session.process().isAlive()) {
                metadata.put("exitCode", session.process().exitValue());
            }
            return new ChatToolExecutionResult(
                "write_stdin",
                "已写入标准输入",
                metadata
            );
        } catch (Exception exception) {
            throw new BusinessException(
                "CHAT_TOOL_WRITE_STDIN_FAILED",
                ErrorMessageCatalog.CHAT_TOOL_STDIN_WRITE_FAILED_PREFIX + exception.getMessage()
            );
        }
    }

    /**
     * 读取当前 workspace 内的文本文件内容。
     * @param input 工具输入。
     * @return 文件内容与路径元数据。
     */
    private ChatToolExecutionResult executeRead(ToolInput input) {
        Path workingDirectory = resolveToolWorkingDirectory();
        Path filePath = resolveWorkspacePath(workingDirectory, extractPath(input, "path"));
        if (!Files.isRegularFile(filePath)) {
            throw new BusinessException("CHAT_TOOL_FILE_NOT_FOUND", "文件不存在");
        }
        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            Integer offset = input.object().getInt("offset");
            Integer limit = input.object().getInt("limit");
            String slicedContent = sliceLines(content, offset, limit);
            Map<String, Object> metadata = workspaceFileMetadata(workingDirectory, filePath);
            metadata.put("length", content.length());
            metadata.put("truncated", !StrUtil.equals(content, slicedContent));
            return new ChatToolExecutionResult("read", slicedContent, metadata);
        } catch (Exception exception) {
            throw new BusinessException("CHAT_TOOL_READ_FAILED", "读取文件失败: " + exception.getMessage());
        }
    }

    /**
     * 写入当前 workspace 内的文本文件，必要时自动创建父目录。
     * @param input 工具输入。
     * @return 写入结果。
     */
    private ChatToolExecutionResult executeWrite(ToolInput input) {
        // 步骤 1：解析当前工具工作目录和目标路径，路径约束统一由 resolveWorkspacePath 保证不越界。
        Path workingDirectory = resolveToolWorkingDirectory();
        WriteArguments writeArguments = extractWriteArguments(input);
        Path filePath = resolveWorkspacePath(workingDirectory, writeArguments.path());
        String content = writeArguments.content();
        try {
            // 步骤 2：写入前保留旧内容，后续生成单文件 diff 供聊天流实时展示。
            boolean existedBeforeWrite = Files.isRegularFile(filePath);
            String oldContent = existedBeforeWrite
                ? Files.readString(filePath, StandardCharsets.UTF_8)
                : "";
            // 步骤 3：写入前补齐父目录，并以 UTF-8 覆盖写入文件内容。
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                filePath,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
            // 步骤 4：返回工作区相对路径、写入字节数和文件差异，供模型和前端确认写入结果。
            Map<String, Object> metadata = workspaceFileMetadata(workingDirectory, filePath);
            metadata.put("bytes", Files.size(filePath));
            addFileDiffMetadata(
                metadata,
                List.of(buildSingleFileDiff(workingDirectory, filePath, oldContent, content, existedBeforeWrite ? "modified" : "added"))
            );
            return new ChatToolExecutionResult("write", "文件已写入", metadata);
        } catch (Exception exception) {
            throw new BusinessException("CHAT_TOOL_WRITE_FAILED", "写入文件失败: " + exception.getMessage());
        }
    }

    /**
     * 提取 write 工具的路径与正文参数。
     * 业务意图：模型工具调用参数偶尔会把多行 HTML 原样塞进 JSON 字符串，导致严格 JSON 解析失败；
     * 这里仅针对 write 的 path/content 结构做有限恢复，避免整段 HTML 被当成 path 触发 Windows 非法路径错误。
     * @param input 工具输入。
     * @return 已恢复的写文件参数。
     */
    private WriteArguments extractWriteArguments(ToolInput input) {
        WriteArguments recoveredArguments = recoverLooseWriteArguments(input.raw());
        if (recoveredArguments != null && input.object().isEmpty()) {
            return recoveredArguments;
        }
        String content = input.object().getStr("content");
        if (content == null) {
            content = input.raw();
        }
        return new WriteArguments(extractPath(input, "path"), content);
    }

    /**
     * 从非严格 JSON 的 write arguments 中恢复 path/content。
     * 边界条件：只支持 content 是最后一个字段的对象形态；其他复杂结构继续走原有错误路径，避免误解析任意文本。
     * @param raw 原始工具参数。
     * @return 成功恢复时返回写入参数，无法恢复时返回 null。
     */
    private WriteArguments recoverLooseWriteArguments(String raw) {
        if (StrUtil.isBlank(raw) || !StrUtil.trimToEmpty(raw).startsWith("{")) {
            return null;
        }
        String path = extractLooseJsonStringField(raw, "path");
        String content = extractLooseTrailingStringField(raw, "content");
        if (StrUtil.isBlank(path) || content == null) {
            return null;
        }
        return new WriteArguments(path, content);
    }

    /**
     * 提取普通短字符串字段，例如 path。
     * @param raw 原始工具参数。
     * @param fieldName 字段名。
     * @return 解析出的字段值，无法解析时返回 null。
     */
    private String extractLooseJsonStringField(String raw, String fieldName) {
        Pattern fieldPattern = Pattern.compile(
            "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
            Pattern.DOTALL
        );
        Matcher matcher = fieldPattern.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        return unescapeLooseJsonString(matcher.group(1));
    }

    /**
     * 提取位于对象末尾的长字符串字段，用于容忍 HTML 内容中未转义的属性引号和真实换行。
     * @param raw 原始工具参数。
     * @param fieldName 字段名。
     * @return 字段内容，无法安全定位时返回 null。
     */
    private String extractLooseTrailingStringField(String raw, String fieldName) {
        Pattern fieldStartPattern = Pattern.compile(
            "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"",
            Pattern.DOTALL
        );
        Matcher matcher = fieldStartPattern.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        int contentStart = matcher.end();
        int contentEnd = raw.length();
        while (contentEnd > contentStart && Character.isWhitespace(raw.charAt(contentEnd - 1))) {
            contentEnd--;
        }
        if (contentEnd <= contentStart || raw.charAt(contentEnd - 1) != '}') {
            return null;
        }
        contentEnd--;
        while (contentEnd > contentStart && Character.isWhitespace(raw.charAt(contentEnd - 1))) {
            contentEnd--;
        }
        if (contentEnd <= contentStart || raw.charAt(contentEnd - 1) != '"') {
            return null;
        }
        contentEnd--;
        return unescapeLooseJsonString(raw.substring(contentStart, contentEnd));
    }

    /**
     * 只处理 JSON 字符串中常见转义，保留无法识别的反斜杠序列，避免破坏代码正文。
     * @param value 原始字符串片段。
     * @return 反转义后的文本。
     */
    private String unescapeLooseJsonString(String value) {
        if (value == null || value.indexOf('\\') < 0) {
            return value;
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\' || index + 1 >= value.length()) {
                builder.append(current);
                continue;
            }
            char escaped = value.charAt(++index);
            switch (escaped) {
                case '"' -> builder.append('"');
                case '\\' -> builder.append('\\');
                case '/' -> builder.append('/');
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> index = appendUnicodeEscape(value, index, builder);
                default -> {
                    builder.append('\\');
                    builder.append(escaped);
                }
            }
        }
        return builder.toString();
    }

    /**
     * 解析 \\uXXXX 转义；格式不完整时保留原始文本，避免吞掉用户代码。
     * @param value 原始字符串。
     * @param escapeIndex 当前 u 字符下标。
     * @param builder 输出缓冲。
     * @return 消费后的下标。
     */
    private int appendUnicodeEscape(String value, int escapeIndex, StringBuilder builder) {
        if (escapeIndex + 4 >= value.length()) {
            builder.append("\\u");
            return escapeIndex;
        }
        String hex = value.substring(escapeIndex + 1, escapeIndex + 5);
        if (!ReUtil.isMatch("[0-9a-fA-F]{4}", hex)) {
            builder.append("\\u");
            return escapeIndex;
        }
        builder.append((char) Integer.parseInt(hex, 16));
        return escapeIndex + 4;
    }

    /**
     * 基于精确文本替换编辑当前 workspace 内的文件。
     * @param input 工具输入。
     * @return 编辑结果。
     */
    private ChatToolExecutionResult executeEdit(ToolInput input) {
        // 步骤 1：解析目标文件和精确替换文本，路径必须限制在当前工具工作目录内。
        Path workingDirectory = resolveToolWorkingDirectory();
        Path filePath = resolveWorkspacePath(workingDirectory, extractPath(input, "path"));
        String oldText = prefer(input.object().getStr("old_text"), input.object().getStr("oldText"));
        String newText = prefer(input.object().getStr("new_text"), input.object().getStr("newText"));
        if (StrUtil.isEmpty(oldText)) {
            throw new BusinessException("CHAT_TOOL_EDIT_OLD_TEXT_REQUIRED", "请提供 old_text");
        }
        if (newText == null) {
            throw new BusinessException("CHAT_TOOL_EDIT_NEW_TEXT_REQUIRED", "请提供 new_text");
        }
        if (!Files.isRegularFile(filePath)) {
            throw new BusinessException("CHAT_TOOL_FILE_NOT_FOUND", "文件不存在");
        }
        try {
            // 步骤 2：读取文件并统计 old_text 出现次数，默认要求唯一命中以避免误改多处。
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            int occurrences = countOccurrences(content, oldText);
            if (occurrences == 0) {
                throw new BusinessException("CHAT_TOOL_EDIT_TEXT_NOT_FOUND", "未找到要替换的文本");
            }
            boolean replaceAll = getBooleanOption(input, "replaceAll", "replace_all", false);
            if (!replaceAll && occurrences > 1) {
                throw new BusinessException("CHAT_TOOL_EDIT_TEXT_NOT_UNIQUE", "要替换的文本不唯一，请提供更精确的 old_text");
            }
            // 步骤 3：根据 replaceAll 决定替换全部还是首个命中，未产生变更时直接报错。
            int firstMatchIndex = content.indexOf(oldText);
            String updated = replaceAll
                ? content.replace(oldText, newText)
                : content.substring(0, firstMatchIndex) + newText + content.substring(firstMatchIndex + oldText.length());
            if (StrUtil.equals(content, updated)) {
                throw new BusinessException("CHAT_TOOL_EDIT_NO_CHANGES", "编辑未产生变更");
            }
            // 步骤 4：写回文件并返回相对路径、命中次数、首个变更行和单文件差异。
            Files.writeString(filePath, updated, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            Map<String, Object> metadata = workspaceFileMetadata(workingDirectory, filePath);
            metadata.put("replaceAll", replaceAll);
            metadata.put("occurrences", occurrences);
            metadata.put("firstChangedLine", firstChangedLine(content, updated));
            addFileDiffMetadata(
                metadata,
                List.of(buildSingleFileDiff(workingDirectory, filePath, content, updated, "modified"))
            );
            return new ChatToolExecutionResult("edit", "文件已编辑", metadata);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("CHAT_TOOL_EDIT_FAILED", "编辑文件失败: " + exception.getMessage());
        }
    }

    /**
     * 在当前 workspace 内按文本正则检索文件内容。
     * @param input 工具输入。
     * @return 匹配行列表。
     */
    private ChatToolExecutionResult executeGrep(ToolInput input) {
        // 步骤 1：解析搜索表达式、路径、大小写、字面量和 glob 过滤参数。
        String pattern = input.object().getStr("pattern");
        if (StrUtil.isBlank(pattern)) {
            throw new BusinessException("CHAT_TOOL_GREP_PATTERN_REQUIRED", "请提供 pattern");
        }
        Path workingDirectory = resolveToolWorkingDirectory();
        Path searchRoot = resolveWorkspacePath(workingDirectory, StrUtil.blankToDefault(input.object().getStr("path"), "."));
        boolean ignoreCase = getBooleanOption(input, "ignoreCase", "ignore_case", false);
        boolean literal = getBooleanOption(input, "literal", "literal", false);
        int flags = ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
        Pattern compiledPattern = Pattern.compile(literal ? Pattern.quote(pattern) : pattern, flags);
        String glob = input.object().getStr("glob");
        PathMatcher globMatcher = StrUtil.isBlank(glob) ? null : searchRoot.getFileSystem().getPathMatcher("glob:" + glob);
        int maxResults = normalizeIntOption(input, "maxResults", "limit", 100, 500);
        int context = normalizeIntOption(input, "context", null, 0, 20);
        List<String> matches = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(searchRoot)) {
            // 步骤 2：遍历当前 workspace 内满足 glob 的文件，并按稳定顺序逐个收集匹配行。
            List<Path> files = stream
                .filter(Files::isRegularFile)
                .filter(file -> matchesGlob(searchRoot, file, globMatcher))
                .sorted()
                .toList();
            for (Path file : files) {
                collectGrepMatches(workingDirectory, file, compiledPattern, maxResults, context, matches);
                if (matches.size() >= maxResults) {
                    break;
                }
            }
        } catch (Exception exception) {
            throw new BusinessException("CHAT_TOOL_GREP_FAILED", "内容搜索失败: " + exception.getMessage());
        }
        // 步骤 3：返回匹配结果和搜索参数元数据，空结果使用固定文本便于模型判断未命中。
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("pattern", pattern);
        metadata.put("path", toWorkspaceRelativePath(workingDirectory, searchRoot));
        metadata.put("glob", glob);
        metadata.put("count", matches.size());
        return new ChatToolExecutionResult("grep", matches.isEmpty() ? "No matches found" : String.join("\n", matches), metadata);
    }

    /**
     * 在当前 workspace 内按 glob 模式查找文件路径。
     * @param input 工具输入。
     * @return 匹配路径列表。
     */
    private ChatToolExecutionResult executeFind(ToolInput input) {
        // 步骤 1：解析 glob 模式和搜索根目录，根目录必须存在且位于当前 workspace 内。
        String pattern = StrUtil.blankToDefault(input.object().getStr("pattern"), "*");
        Path workingDirectory = resolveToolWorkingDirectory();
        Path searchRoot = resolveWorkspacePath(workingDirectory, StrUtil.blankToDefault(input.object().getStr("path"), "."));
        if (!Files.isDirectory(searchRoot)) {
            throw new BusinessException("CHAT_TOOL_DIRECTORY_NOT_FOUND", "目录不存在");
        }
        PathMatcher matcher = searchRoot.getFileSystem().getPathMatcher("glob:" + pattern);
        int maxResults = normalizeIntOption(input, "maxResults", "limit", 200, 1000);
        List<String> matches = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(searchRoot)) {
            // 步骤 2：递归遍历普通文件，按文件名或相对路径匹配 glob，并限制最大返回数量。
            stream
                .filter(path -> !path.equals(searchRoot))
                .filter(Files::isRegularFile)
                .filter(path -> matcher.matches(path.getFileName()) || matcher.matches(searchRoot.relativize(path)))
                .sorted()
                .limit(maxResults)
                .map(path -> toWorkspaceRelativePath(workingDirectory, path))
                .forEach(matches::add);
        } catch (Exception exception) {
            throw new BusinessException("CHAT_TOOL_FIND_FAILED", "查找文件失败: " + exception.getMessage());
        }
        // 步骤 3：返回匹配路径和查询元数据，空结果使用固定文案便于模型识别。
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("pattern", pattern);
        metadata.put("path", toWorkspaceRelativePath(workingDirectory, searchRoot));
        metadata.put("count", matches.size());
        return new ChatToolExecutionResult("find", matches.isEmpty() ? "No files found matching pattern" : String.join("\n", matches), metadata);
    }

    /**
     * 列出当前 workspace 内目录内容。
     * @param input 工具输入。
     * @return 目录项列表。
     */
    private ChatToolExecutionResult executeLs(ToolInput input) {
        // 步骤 1：解析待列举目录并校验目录存在，避免把文件路径当目录读取。
        Path workingDirectory = resolveToolWorkingDirectory();
        Path directory = resolveWorkspacePath(workingDirectory, StrUtil.blankToDefault(input.object().getStr("path"), "."));
        if (!Files.isDirectory(directory)) {
            throw new BusinessException("CHAT_TOOL_DIRECTORY_NOT_FOUND", "目录不存在");
        }
        int limit = normalizeIntOption(input, "limit", null, 500, 1000);
        try (Stream<Path> stream = Files.list(directory)) {
            // 步骤 2：按文件名稳定排序并按 limit 截断，目录项追加斜杠提示类型。
            List<Path> paths = stream
                .sorted((left, right) -> left.getFileName().toString().compareToIgnoreCase(right.getFileName().toString()))
                .toList();
            List<String> entries = paths.stream()
                .limit(limit)
                .map(path -> path.getFileName() + (Files.isDirectory(path) ? "/" : ""))
                .toList();
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("path", toWorkspaceRelativePath(workingDirectory, directory));
            metadata.put("count", entries.size());
            metadata.put("totalCount", paths.size());
            metadata.put("workingDirectory", workingDirectory.toString());
            // 步骤 3：返回目录内容和截断提示，帮助模型在大目录下继续分页查看。
            String content = entries.isEmpty() ? "(empty directory)" : String.join("\n", entries);
            if (paths.size() > entries.size()) {
                content += "\n\n[" + limit + " entries limit reached. Use limit=" + Math.min(limit * 2, 1000) + " for more]";
            }
            return new ChatToolExecutionResult("ls", content, metadata);
        } catch (Exception exception) {
            throw new BusinessException("CHAT_TOOL_LS_FAILED", "列目录失败: " + exception.getMessage());
        }
    }

    /**
     * 按 patch 文本真实应用到工作目录，并返回差异摘要，供前端确认改动结果。
     */
    private ChatToolExecutionResult executeApplyPatch(ToolInput input) {
        // 步骤 1：优先读取结构化 patch 字段，缺失时从原始文本中提取 patch 块。
        String patchText = prefer(input.object().getStr("patch"), extractPatchBlock(input.raw()));
        if (StrUtil.isBlank(patchText)) {
            throw new BusinessException("CHAT_TOOL_PATCH_REQUIRED", ErrorMessageCatalog.CHAT_TOOL_PATCH_REQUIRED);
        }
        Path workingDirectory = resolveToolWorkingDirectory();
        long startedAt = System.currentTimeMillis();
        try {
            // 步骤 2：将 patch 应用到当前工具工作目录，内部会统一处理 Codex patch 和 git diff patch。
            applyPatchText(workingDirectory, patchText);
            CommandExecution diffExecution = runGitCommand(workingDirectory, List.of("diff", "--", "."));
            // 步骤 3：应用成功后读取 git diff 预览，并解析结构化文件差异给前端渲染。
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("applied", Boolean.TRUE);
            metadata.put("exitCode", 0);
            metadata.put("durationMs", System.currentTimeMillis() - startedAt);
            metadata.put("workingDirectory", workingDirectory.toString());
            addFileDiffMetadata(metadata, parseUnifiedDiffs(diffExecution.output()));
            return new ChatToolExecutionResult(
                "apply_patch",
                "Patch 已应用",
                metadata
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                "CHAT_TOOL_APPLY_PATCH_FAILED",
                ErrorMessageCatalog.CHAT_TOOL_PATCH_APPLY_FAILED_PREFIX + exception.getMessage()
            );
        }
    }

    /**
     * 读取当前工作区的 git 差异，供右侧代码审查栏按来源切换展示。
     * @param input 工具输入，mode 支持 unstaged、staged、commit、branch。
     * @return 结构化文件差异。
     */
    private ChatToolExecutionResult executeGitDiff(ToolInput input) {
        // 步骤 1：解析审查来源模式，默认读取未暂存工作区差异，兼容侧边栏首次打开。
        Path workingDirectory = resolveToolWorkingDirectory();
        String mode = StrUtil.blankToDefault(input.object().getStr("mode"), "unstaged").toLowerCase(Locale.ROOT);
        List<String> gitArgs = buildGitDiffArguments(input, mode);
        // 步骤 2：通过 ProcessBuilder 直接执行 git，避免 shell 包装内容污染 unified diff。
        CommandExecution diffExecution = runGitCommand(workingDirectory, gitArgs);
        List<Map<String, Object>> fileDiffs = parseUnifiedDiffs(diffExecution.output());
        // 步骤 3：统一写入 fileDiffs/diffSummary/diffPreview，前端无需区分实时工具结果和侧栏查询结果。
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mode", mode);
        metadata.put("gitArgs", gitArgs);
        metadata.put("exitCode", diffExecution.exitCode());
        metadata.put("timedOut", diffExecution.timedOut());
        metadata.put("durationMs", diffExecution.durationMs());
        metadata.put("workingDirectory", workingDirectory.toString());
        addFileDiffMetadata(metadata, fileDiffs);
        String content = fileDiffs.isEmpty() ? "未检测到差异" : "已读取 " + fileDiffs.size() + " 个文件差异";
        return new ChatToolExecutionResult("git_diff", content, metadata);
    }

    /**
     * 根据侧栏来源模式组装 git diff 参数；未知模式直接抛业务错误，避免执行任意 git 子命令。
     */
    private List<String> buildGitDiffArguments(ToolInput input, String mode) {
        return switch (mode) {
            case "unstaged", "working_tree", "working-tree" -> List.of("diff", "--", ".");
            case "staged", "cached" -> List.of("diff", "--cached", "--", ".");
            case "commit" -> {
                String ref = StrUtil.blankToDefault(input.object().getStr("ref"), "HEAD");
                yield List.of("show", "--stat", "--patch", "--format=medium", ref);
            }
            case "branch" -> {
                String base = StrUtil.blankToDefault(input.object().getStr("base"), "HEAD");
                String target = input.object().getStr("target");
                String range = StrUtil.isBlank(target) ? base : base + "..." + target;
                yield List.of("diff", range, "--", ".");
            }
            default -> throw new BusinessException("CHAT_TOOL_GIT_DIFF_MODE_UNSUPPORTED", "不支持的差异来源类型");
        };
    }

    /**
     * 根据 patch 内容类型选择应用策略：优先处理 Codex 风格补丁，其次回退 git apply。
     * @param workingDirectory 工具工作目录。
     * @param patchText 原始 patch 文本。
     */
    private void applyPatchText(Path workingDirectory, String patchText) {
        String normalizedPatch = normalizeLineEnding(patchText);
        if (normalizedPatch.contains("*** Begin Patch")) {
            applyCodexStylePatch(workingDirectory, normalizedPatch);
            return;
        }
        applyGitStylePatch(workingDirectory, normalizedPatch);
    }

    /**
     * 使用 git apply 应用标准 unified diff。
     * @param workingDirectory 工具工作目录。
     * @param patchText patch 文本。
     */
    private void applyGitStylePatch(Path workingDirectory, String patchText) {
        // 步骤 1：将 patch 写入临时文件，并把路径规范化到当前工具工作目录语义。
        Path tempPatch = null;
        try {
            tempPatch = Files.createTempFile("chat-tool-", ".patch");
            FileUtil.writeUtf8String(normalizeGitPatchWorkspacePaths(workingDirectory, patchText), tempPatch.toFile());
            // 步骤 2：先执行 git apply --check，提前暴露冲突或路径越界问题，不直接修改文件。
            CommandExecution checkExecution = runCommand(
                "git apply --check \"" + tempPatch.toAbsolutePath() + "\"",
                10000L,
                workingDirectory
            );
            if (checkExecution.exitCode() != 0) {
                throw new BusinessException(
                    "CHAT_TOOL_APPLY_PATCH_FAILED",
                    StrUtil.blankToDefault(checkExecution.output(), ErrorMessageCatalog.CHAT_TOOL_PATCH_CHECK_FAILED)
                );
            }
            // 步骤 3：检查通过后再执行 git apply，失败时保留 git 输出给模型修正 patch。
            CommandExecution applyExecution = runCommand(
                "git apply \"" + tempPatch.toAbsolutePath() + "\"",
                10000L,
                workingDirectory
            );
            if (applyExecution.exitCode() != 0) {
                throw new BusinessException(
                    "CHAT_TOOL_APPLY_PATCH_FAILED",
                    StrUtil.blankToDefault(applyExecution.output(), ErrorMessageCatalog.CHAT_TOOL_PATCH_APPLY_FAILED)
                );
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                "CHAT_TOOL_APPLY_PATCH_FAILED",
                ErrorMessageCatalog.CHAT_TOOL_PATCH_APPLY_FAILED_PREFIX + exception.getMessage()
            );
        } finally {
            if (tempPatch != null) {
                // 步骤 4：无论应用成功或失败都删除临时 patch 文件，避免工作目录残留工具文件。
                FileUtil.del(tempPatch.toFile());
            }
        }
    }

    /**
     * 标准 diff 偶尔会带上工作目录内的绝对路径；应用前转为相对路径，保持写入边界仍锁定在当前工作区。
     * @param workingDirectory 工具工作目录。
     * @param patchText 原始标准 diff。
     * @return 可交给 git apply 的补丁文本。
     */
    private String normalizeGitPatchWorkspacePaths(Path workingDirectory, String patchText) {
        Path normalizedWorkingDirectory = workingDirectory.toAbsolutePath().normalize();
        String[] lines = patchText.split("\n", -1);
        List<String> normalizedLines = new ArrayList<>(lines.length);
        for (String line : lines) {
            if (StrUtil.startWith(line, "diff --git ")) {
                normalizedLines.add(normalizeGitDiffHeaderLine(normalizedWorkingDirectory, line));
                continue;
            }
            if (StrUtil.startWith(line, "--- ") || StrUtil.startWith(line, "+++ ")) {
                normalizedLines.add(normalizeGitFileHeaderLine(normalizedWorkingDirectory, line));
                continue;
            }
            normalizedLines.add(line);
        }
        List<String> repairedLines = normalizeBareNewFileGitPatchLines(normalizedLines);
        List<String> replacementLines = normalizeExistingFileNewFileGitPatchLines(normalizedWorkingDirectory, repairedLines);
        String normalizedPatch = String.join("\n", normalizeGitPatchHunkHeaders(replacementLines));
        // git apply 会把缺少文件尾换行的最后一个 hunk 判定为 corrupt patch，模型输出 JSON 时常丢失该换行。
        return normalizedPatch.endsWith("\n") ? normalizedPatch : normalizedPatch + "\n";
    }

    /**
     * 修复模型把新建文件标准 diff 的 hunk 内容当成原文输出、漏写每行 + 前缀的问题。
     * 关键约束：只处理 `--- /dev/null` 且旧文件行号为 0 的新增文件 hunk，避免误改普通 diff。
     */
    private List<String> normalizeBareNewFileGitPatchLines(List<String> lines) {
        // 步骤 1：复制原始行，逐块识别 `--- /dev/null` 表示的新增文件 diff。
        List<String> normalizedLines = new ArrayList<>(lines);
        boolean newFileBlock = false;
        int lineIndex = 0;
        while (lineIndex < normalizedLines.size()) {
            String line = normalizedLines.get(lineIndex);
            if (StrUtil.startWith(line, "diff --git ")) {
                newFileBlock = false;
                lineIndex++;
                continue;
            }
            if (isGitDevNullHeaderLine(line)) {
                newFileBlock = true;
                lineIndex++;
                continue;
            }
            Matcher matcher = GIT_HUNK_HEADER_PATTERN.matcher(line);
            // 步骤 2：仅修复新增文件且旧文件起始行为 0 的 hunk，普通修改块保持原样。
            if (!newFileBlock || !matcher.matches() || !StrUtil.equals(matcher.group(1), "0")) {
                lineIndex++;
                continue;
            }
            int hunkLineIndex = lineIndex + 1;
            // 步骤 3：给漏掉前缀的新增内容行补 `+`，避免 git apply 把内容当作上下文行。
            while (hunkLineIndex < normalizedLines.size() && !isGitPatchHunkBoundary(normalizedLines.get(hunkLineIndex))) {
                String hunkLine = normalizedLines.get(hunkLineIndex);
                if (!StrUtil.startWith(hunkLine, "+") && !StrUtil.startWith(hunkLine, "\\ No newline")) {
                    normalizedLines.set(hunkLineIndex, "+" + hunkLine);
                }
                hunkLineIndex++;
            }
            lineIndex = hunkLineIndex;
        }
        return normalizedLines;
    }

    /**
     * 将“新增文件”补丁指向同名既有文件的场景转为整文件替换补丁。
     * 业务意图：模型生成 weather.html 等单文件页面时常忽略目标已存在，继续输出 `new file mode`。
     */
    private List<String> normalizeExistingFileNewFileGitPatchLines(Path workingDirectory, List<String> lines) {
        // 步骤 1：按 diff 文件块遍历，既支持带 diff --git 的块，也支持只有 ---/+++ 的 unified diff。
        List<String> normalizedLines = new ArrayList<>(lines.size());
        int lineIndex = 0;
        while (lineIndex < lines.size()) {
            if (StrUtil.startWith(lines.get(lineIndex), "diff --git ")) {
                int blockEndIndex = lineIndex + 1;
                while (blockEndIndex < lines.size() && !StrUtil.startWith(lines.get(blockEndIndex), "diff --git ")) {
                    blockEndIndex++;
                }
                List<String> blockLines = new ArrayList<>(lines.subList(lineIndex, blockEndIndex));
                normalizedLines.addAll(rewriteNewFileBlockForExistingFile(workingDirectory, blockLines));
                lineIndex = blockEndIndex;
                continue;
            }
            if (isStandaloneGitFileHeaderAt(lines, lineIndex)) {
                // 步骤 2：识别独立文件头块，并交给同名文件覆盖重写逻辑处理。
                int blockEndIndex = lineIndex + 2;
                while (blockEndIndex < lines.size()
                    && !StrUtil.startWith(lines.get(blockEndIndex), "diff --git ")
                    && !isStandaloneGitFileHeaderAt(lines, blockEndIndex)) {
                    blockEndIndex++;
                }
                List<String> blockLines = new ArrayList<>(lines.subList(lineIndex, blockEndIndex));
                normalizedLines.addAll(rewriteNewFileBlockForExistingFile(workingDirectory, blockLines));
                lineIndex = blockEndIndex;
                continue;
            }
            normalizedLines.add(lines.get(lineIndex));
            lineIndex++;
        }
        // 步骤 3：返回重写后的完整 patch 行集合，未命中同名文件覆盖时保持原样。
        return normalizedLines;
    }

    /**
     * 识别缺少 diff --git 头的标准 unified diff 文件块边界。
     * 关键约束：只有连续 `---`/`+++` 文件头才视作文件块，避免把 hunk 内普通删除内容误判为新文件。
     */
    private boolean isStandaloneGitFileHeaderAt(List<String> lines, int lineIndex) {
        if (lineIndex < 0 || lineIndex + 1 >= lines.size()) {
            return false;
        }
        return StrUtil.startWith(lines.get(lineIndex), "--- ")
            && StrUtil.startWith(lines.get(lineIndex + 1), "+++ ");
    }

    /**
     * 同名文件覆盖只需要路径本体；文件头中的时间戳元数据不能参与路径解析。
     */
    private String extractGitFileHeaderPathToken(String headerBody) {
        String trimmedBody = StrUtil.trim(headerBody);
        int metadataIndex = trimmedBody.indexOf('\t');
        if (metadataIndex < 0) {
            return trimmedBody;
        }
        return trimmedBody.substring(0, metadataIndex);
    }

    /**
     * 规范化标准 diff 文件头，剥离时间戳后再做工作区路径约束。
     */
    private String normalizeGitFileHeaderPathToken(Path workingDirectory, String headerBody) {
        String pathToken = extractGitFileHeaderPathToken(headerBody);
        return normalizeGitPatchPathToken(workingDirectory, pathToken);
    }

    /**
     * 从 `---`/`+++` 文件头中读取不含时间戳的路径本体。
     */
    private String extractGitFileHeaderPathFromLine(String line) {
        if (!StrUtil.startWith(line, "--- ") && !StrUtil.startWith(line, "+++ ")) {
            return "";
        }
        return extractGitFileHeaderPathToken(StrUtil.subSuf(line, 4));
    }

    /**
     * 判断文件头是否指向 /dev/null，兼容 unified diff 中可能携带的时间戳字段。
     */
    private boolean isGitDevNullHeaderLine(String line) {
        return StrUtil.startWith(line, "--- ")
            && StrUtil.equals(extractGitFileHeaderPathFromLine(line), "/dev/null");
    }

    /**
     * 从新增文件头中提取目标路径，自动剥离 b/ 前缀与时间戳。
     */
    private String extractGitNewFileTargetPath(String line) {
        if (!StrUtil.startWith(line, "+++ ")) {
            return "";
        }
        String pathToken = extractGitFileHeaderPathFromLine(line);
        if (StrUtil.equals(pathToken, "/dev/null")) {
            return "";
        }
        return stripGitPatchSidePrefix(pathToken);
    }

    /**
     * 只在新增文件块的目标路径已存在且是普通文件时改写，避免影响真正的新建文件和删除文件补丁。
     */
    private List<String> rewriteNewFileBlockForExistingFile(Path workingDirectory, List<String> blockLines) {
        String targetPathText = findGitPatchNewFileTargetPath(blockLines);
        if (StrUtil.isBlank(targetPathText)) {
            return blockLines;
        }
        Path targetPath = resolvePatchTargetPath(workingDirectory, targetPathText);
        if (!Files.isRegularFile(targetPath)) {
            return blockLines;
        }
        try {
            GitPatchTextContent oldContent = splitGitPatchTextContent(
                normalizeLineEnding(Files.readString(targetPath, StandardCharsets.UTF_8))
            );
            GitPatchTextContent newContent = collectNewFileGitPatchContent(blockLines);
            return buildWholeFileReplacementGitPatch(targetPathText, oldContent, newContent);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                "CHAT_TOOL_APPLY_PATCH_FAILED",
                ErrorMessageCatalog.CHAT_TOOL_PATCH_APPLY_FAILED_PREFIX + exception.getMessage()
            );
        }
    }

    /**
     * 从 `--- /dev/null` / `+++ b/path` 组合中提取新增文件目标路径。
     */
    private String findGitPatchNewFileTargetPath(List<String> blockLines) {
        boolean newFileBlock = false;
        String targetPathText = null;
        for (String line : blockLines) {
            if (isGitDevNullHeaderLine(line)) {
                newFileBlock = true;
                continue;
            }
            if (StrUtil.startWith(line, "+++ ")) {
                targetPathText = extractGitNewFileTargetPath(line);
            }
        }
        return newFileBlock ? targetPathText : null;
    }

    /**
     * 提取新增文件 hunk 的新文件内容，供同名文件覆盖场景生成整文件替换补丁。
     */
    private GitPatchTextContent collectNewFileGitPatchContent(List<String> blockLines) {
        // 步骤 1：进入新增文件 hunk 后收集新文件侧内容，同时记录文件尾换行状态。
        List<String> contentLines = new ArrayList<>();
        boolean inHunk = false;
        boolean trailingNewline = true;
        boolean lastLineBelongsToNewFile = false;
        for (String line : blockLines) {
            if (GIT_HUNK_HEADER_PATTERN.matcher(line).matches()) {
                inHunk = true;
                lastLineBelongsToNewFile = false;
                continue;
            }
            if (!inHunk) {
                continue;
            }
            if (StrUtil.startWith(line, "@@ ")) {
                // 步骤 2：遇到新的 hunk 头时重置上一行归属，防止无换行标记误应用到下一块。
                lastLineBelongsToNewFile = false;
                continue;
            }
            if (StrUtil.startWith(line, "+") || StrUtil.startWith(line, " ")) {
                // 步骤 3：新增行和上下文行都属于新文件内容，剥离 diff 前缀后保存。
                contentLines.add(StrUtil.subSuf(line, 1));
                trailingNewline = true;
                lastLineBelongsToNewFile = true;
                continue;
            }
            if (StrUtil.startWith(line, "\\ No newline") && lastLineBelongsToNewFile) {
                trailingNewline = false;
            } else {
                lastLineBelongsToNewFile = false;
            }
        }
        // 步骤 4：返回内容行和尾换行标记，供整文件替换 patch 复原边界。
        return new GitPatchTextContent(contentLines, trailingNewline);
    }

    /**
     * 将文本拆成 git hunk 行模型，保留“文件末尾是否有换行”的边界信息。
     */
    private GitPatchTextContent splitGitPatchTextContent(String content) {
        if (StrUtil.isEmpty(content)) {
            return new GitPatchTextContent(List.of(), true);
        }
        boolean trailingNewline = content.endsWith("\n");
        String[] parts = content.split("\n", -1);
        int lineCount = trailingNewline ? parts.length - 1 : parts.length;
        List<String> contentLines = new ArrayList<>(lineCount);
        for (int index = 0; index < lineCount; index++) {
            contentLines.add(parts[index]);
        }
        return new GitPatchTextContent(contentLines, trailingNewline);
    }

    /**
     * 生成整文件替换 patch，避免 git apply 把同名文件误判为重复新增。
     */
    private List<String> buildWholeFileReplacementGitPatch(
        String targetPathText,
        GitPatchTextContent oldContent,
        GitPatchTextContent newContent
    ) {
        List<String> replacementLines = new ArrayList<>();
        replacementLines.add("diff --git a/" + targetPathText + " b/" + targetPathText);
        replacementLines.add("--- a/" + targetPathText);
        replacementLines.add("+++ b/" + targetPathText);
        replacementLines.add(
            "@@ -" + gitPatchStartLine(oldContent.lines()) + "," + oldContent.lines().size()
                + " +" + gitPatchStartLine(newContent.lines()) + "," + newContent.lines().size()
                + " @@"
        );
        appendGitPatchSideLines(replacementLines, oldContent, "-");
        appendGitPatchSideLines(replacementLines, newContent, "+");
        return replacementLines;
    }

    /**
     * 空文件 hunk 的起始行必须写 0，非空文件从第 1 行开始。
     */
    private int gitPatchStartLine(List<String> lines) {
        return lines.isEmpty() ? 0 : 1;
    }

    /**
     * 追加整文件替换 hunk 的旧侧或新侧内容，并保留无文件尾换行标记。
     */
    private void appendGitPatchSideLines(List<String> replacementLines, GitPatchTextContent content, String prefix) {
        for (String line : content.lines()) {
            replacementLines.add(prefix + line);
        }
        if (!content.trailingNewline() && !content.lines().isEmpty()) {
            replacementLines.add("\\ No newline at end of file");
        }
    }

    /**
     * 按 hunk 实际 +/-/空格行重算行数，修复模型把 @@ 里的行数写成示意值导致 git apply 拒绝的问题。
     */
    private List<String> normalizeGitPatchHunkHeaders(List<String> lines) {
        // 步骤 1：复制 patch 行并逐个定位 hunk header，非 hunk 行保持原样。
        List<String> normalizedLines = new ArrayList<>(lines);
        int lineIndex = 0;
        while (lineIndex < normalizedLines.size()) {
            Matcher matcher = GIT_HUNK_HEADER_PATTERN.matcher(normalizedLines.get(lineIndex));
            if (!matcher.matches()) {
                lineIndex++;
                continue;
            }
            int oldLineCount = 0;
            int newLineCount = 0;
            int hunkLineIndex = lineIndex + 1;
            // 步骤 2：统计当前 hunk 内旧侧和新侧实际行数，直到遇到下一个边界。
            while (hunkLineIndex < normalizedLines.size() && !isGitPatchHunkBoundary(normalizedLines.get(hunkLineIndex))) {
                GitHunkLineCount lineCount = countGitPatchHunkLine(normalizedLines.get(hunkLineIndex));
                oldLineCount += lineCount.oldLineCount();
                newLineCount += lineCount.newLineCount();
                hunkLineIndex++;
            }
            // 步骤 3：用真实行数重写 hunk header，修复模型输出的示意行数。
            normalizedLines.set(
                lineIndex,
                "@@ -" + matcher.group(1) + "," + oldLineCount
                    + " +" + matcher.group(2) + "," + newLineCount
                    + " @@" + matcher.group(3)
            );
            lineIndex = hunkLineIndex;
        }
        return normalizedLines;
    }

    /**
     * 判断当前行是否已经进入下一个 hunk 或下一个文件块。
     */
    private boolean isGitPatchHunkBoundary(String line) {
        return StrUtil.startWith(line, "@@ ") || StrUtil.startWith(line, "diff --git ");
    }

    /**
     * 统计单行 hunk 对旧文件和新文件的行数贡献；`\ No newline` 标记不计入任一侧。
     */
    private GitHunkLineCount countGitPatchHunkLine(String line) {
        if (StrUtil.isEmpty(line) || StrUtil.startWith(line, "\\ No newline")) {
            return new GitHunkLineCount(0, 0);
        }
        return switch (line.charAt(0)) {
            case ' ' -> new GitHunkLineCount(1, 1);
            case '-' -> new GitHunkLineCount(1, 0);
            case '+' -> new GitHunkLineCount(0, 1);
            default -> new GitHunkLineCount(0, 0);
        };
    }

    /**
     * 规范化 diff --git 头部的 a/b 两个路径，避免 Windows 绝对路径被 git apply 判定为非法路径。
     */
    private String normalizeGitDiffHeaderLine(Path workingDirectory, String line) {
        String body = StrUtil.removePrefix(line, "diff --git ");
        List<String> parts = StrUtil.split(body, ' ');
        if (parts.size() != 2) {
            return line;
        }
        return "diff --git "
            + normalizeGitPatchPathToken(workingDirectory, parts.get(0))
            + " "
            + normalizeGitPatchPathToken(workingDirectory, parts.get(1));
    }

    /**
     * 规范化 ---/+++ 文件头路径；/dev/null 表示新增或删除文件，必须原样保留。
     */
    private String normalizeGitFileHeaderLine(Path workingDirectory, String line) {
        String prefix = line.substring(0, 4);
        return prefix + normalizeGitFileHeaderPathToken(workingDirectory, line.substring(4));
    }

    /**
     * 将工作区内绝对路径转换为 git patch 期望的相对路径，工作区外路径继续按越界处理。
     */
    private String normalizeGitPatchPathToken(Path workingDirectory, String token) {
        if (StrUtil.isBlank(token) || StrUtil.equals(token, "/dev/null")) {
            return token;
        }
        String normalizedToken = token.trim();
        String prefix = "";
        if (StrUtil.startWith(normalizedToken, "a/") || StrUtil.startWith(normalizedToken, "b/")) {
            prefix = normalizedToken.substring(0, 2);
            normalizedToken = normalizedToken.substring(2);
        }
        String relativePath = relativizeWorkspaceAbsolutePath(workingDirectory, normalizedToken);
        return relativePath == null ? token : prefix + relativePath;
    }

    /**
     * 去掉 git patch 文件头中的 a/b 侧前缀，得到工作目录内相对路径。
     */
    private String stripGitPatchSidePrefix(String pathText) {
        String normalizedPath = StrUtil.trim(pathText);
        if (StrUtil.startWith(normalizedPath, "a/") || StrUtil.startWith(normalizedPath, "b/")) {
            return normalizedPath.substring(2);
        }
        return normalizedPath;
    }

    /**
     * 只接受当前工作目录内的绝对路径，确保模型误传其他盘符时不会越界写入。
     */
    private String relativizeWorkspaceAbsolutePath(Path workingDirectory, String pathText) {
        try {
            Path candidatePath = Path.of(pathText);
            if (!candidatePath.isAbsolute()) {
                return null;
            }
            Path normalizedWorkingDirectory = workingDirectory.toAbsolutePath().normalize();
            Path normalizedCandidatePath = candidatePath.toAbsolutePath().normalize();
            if (!normalizedCandidatePath.startsWith(normalizedWorkingDirectory)) {
                throw new BusinessException(
                    "CHAT_TOOL_APPLY_PATCH_FAILED",
                    ErrorMessageCatalog.CHAT_TOOL_PATCH_PATH_OUT_OF_BOUND
                );
            }
            return normalizedWorkingDirectory.relativize(normalizedCandidatePath).toString().replace('\\', '/');
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    /**
     * 解析并应用 Codex `*** Begin Patch` 语法，覆盖 update/add/delete 三类文件操作。
     * @param workingDirectory 工具工作目录。
     * @param patchText Codex 补丁文本。
     */
    private void applyCodexStylePatch(Path workingDirectory, String patchText) {
        // 步骤 1：定位 Begin Patch 边界，允许输入前面包含模型解释性文本。
        String[] lines = patchText.split("\n", -1);
        int lineIndex = 0;
        while (lineIndex < lines.length && !StrUtil.equals(lines[lineIndex], "*** Begin Patch")) {
            lineIndex++;
        }
        if (lineIndex >= lines.length) {
            throw new BusinessException("CHAT_TOOL_APPLY_PATCH_FAILED", ErrorMessageCatalog.CHAT_TOOL_PATCH_BEGIN_MISSING);
        }
        lineIndex++;
        while (lineIndex < lines.length) {
            String line = lines[lineIndex];
            if (StrUtil.equals(line, "*** End Patch")) {
                return;
            }
            // 步骤 2：解析 Update File，可选 Move to 和后续变更行交给专用更新函数处理。
            if (StrUtil.startWith(line, "*** Update File: ")) {
                String filePath = StrUtil.removePrefix(line, "*** Update File: ").trim();
                lineIndex++;
                String moveToPath = null;
                if (lineIndex < lines.length && StrUtil.startWith(lines[lineIndex], "*** Move to: ")) {
                    moveToPath = StrUtil.removePrefix(lines[lineIndex], "*** Move to: ").trim();
                    lineIndex++;
                }
                List<String> updateLines = new ArrayList<>();
                while (lineIndex < lines.length && !isCodexPatchBoundary(lines[lineIndex])) {
                    updateLines.add(lines[lineIndex]);
                    lineIndex++;
                }
                applyCodexUpdateFile(workingDirectory, filePath, moveToPath, updateLines);
                continue;
            }
            // 步骤 3：解析 Add File，把所有非边界行作为新增文件内容。
            if (StrUtil.startWith(line, "*** Add File: ")) {
                String filePath = StrUtil.removePrefix(line, "*** Add File: ").trim();
                lineIndex++;
                List<String> addLines = new ArrayList<>();
                while (lineIndex < lines.length && !isCodexPatchBoundary(lines[lineIndex])) {
                    addLines.add(lines[lineIndex]);
                    lineIndex++;
                }
                applyCodexAddFile(workingDirectory, filePath, addLines);
                continue;
            }
            // 步骤 4：解析 Delete File，删除前仍通过 resolvePatchTargetPath 保证路径不越界。
            if (StrUtil.startWith(line, "*** Delete File: ")) {
                String filePath = StrUtil.removePrefix(line, "*** Delete File: ").trim();
                Path targetPath = resolvePatchTargetPath(workingDirectory, filePath);
                FileUtil.del(targetPath.toFile());
                lineIndex++;
                continue;
            }
            lineIndex++;
        }
        // 步骤 5：遍历结束仍未遇到 End Patch 时视为格式错误，防止半截补丁静默成功。
        throw new BusinessException("CHAT_TOOL_APPLY_PATCH_FAILED", ErrorMessageCatalog.CHAT_TOOL_PATCH_END_MISSING);
    }

    /**
     * 应用 Codex 风格的 Update File 操作。
     * @param workingDirectory 工具工作目录。
     * @param filePath 源文件相对路径。
     * @param moveToPath 可选目标路径。
     * @param updateLines 变更行集合。
     */
    private void applyCodexUpdateFile(Path workingDirectory, String filePath, String moveToPath, List<String> updateLines) {
        // 步骤 1：先解析并校验源文件必须存在且是普通文件，避免 Update File 误创建新文件。
        Path sourcePath = resolvePatchTargetPath(workingDirectory, filePath);
        if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            throw new BusinessException(
                "CHAT_TOOL_APPLY_PATCH_FAILED",
                ErrorMessageCatalog.CHAT_TOOL_PATCH_TARGET_NOT_FOUND_PREFIX + filePath
            );
        }
        try {
            // 步骤 2：读取源文件并按 Codex hunk 规则应用变更内容。
            String originalContent = normalizeLineEnding(Files.readString(sourcePath, StandardCharsets.UTF_8));
            String updatedContent = applyCodexHunks(originalContent, updateLines);
            Path targetPath = moveToPath == null ? sourcePath : resolvePatchTargetPath(workingDirectory, moveToPath);
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }
            // 步骤 3：写入目标文件；如果是移动操作，写入成功后删除原文件。
            Files.writeString(targetPath, updatedContent, StandardCharsets.UTF_8);
            if (moveToPath != null && !sourcePath.equals(targetPath)) {
                FileUtil.del(sourcePath.toFile());
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                "CHAT_TOOL_APPLY_PATCH_FAILED",
                ErrorMessageCatalog.CHAT_TOOL_PATCH_UPDATE_FAILED_PREFIX + exception.getMessage()
            );
        }
    }

    /**
     * 应用 Codex 风格的 Add File 操作。
     * @param workingDirectory 工具工作目录。
     * @param filePath 新文件相对路径。
     * @param addLines 新增内容行集合。
     */
    private void applyCodexAddFile(Path workingDirectory, String filePath, List<String> addLines) {
        Path targetPath = resolvePatchTargetPath(workingDirectory, filePath);
        try {
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }
            List<String> contentLines = new ArrayList<>();
            for (String line : addLines) {
                if (StrUtil.startWith(line, "+")) {
                    contentLines.add(StrUtil.removePrefix(line, "+"));
                }
            }
            String content = String.join("\n", contentLines);
            if (!contentLines.isEmpty()) {
                content += "\n";
            }
            Files.writeString(targetPath, content, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new BusinessException(
                "CHAT_TOOL_APPLY_PATCH_FAILED",
                ErrorMessageCatalog.CHAT_TOOL_PATCH_ADD_FAILED_PREFIX + exception.getMessage()
            );
        }
    }

    /**
     * 将 Update File 中的 hunk 变更应用到原始文本。
     * @param originalContent 原始文件内容。
     * @param updateLines 变更行集合。
     * @return 变更后文本。
     */
    private String applyCodexHunks(String originalContent, List<String> updateLines) {
        List<List<String>> hunks = splitCodexHunks(updateLines);
        String updatedContent = originalContent;
        for (List<String> hunk : hunks) {
            String oldFragment = buildCodexFragment(hunk, true);
            String newFragment = buildCodexFragment(hunk, false);
            if (StrUtil.isBlank(oldFragment)) {
                updatedContent = updatedContent + newFragment;
                continue;
            }
            int index = updatedContent.indexOf(oldFragment);
            if (index < 0) {
                throw new BusinessException(
                    "CHAT_TOOL_APPLY_PATCH_FAILED",
                    ErrorMessageCatalog.CHAT_TOOL_PATCH_CONTEXT_MISMATCH
                );
            }
            updatedContent = updatedContent.substring(0, index)
                + newFragment
                + updatedContent.substring(index + oldFragment.length());
        }
        return updatedContent;
    }

    /**
     * 按 `@@` 分段拆解 Codex patch 的 hunk。
     * @param updateLines 变更行集合。
     * @return hunk 列表。
     */
    private List<List<String>> splitCodexHunks(List<String> updateLines) {
        List<List<String>> hunks = new ArrayList<>();
        List<String> currentHunk = new ArrayList<>();
        for (String line : updateLines) {
            if (StrUtil.startWith(line, "@@")) {
                if (!currentHunk.isEmpty()) {
                    hunks.add(currentHunk);
                    currentHunk = new ArrayList<>();
                }
                continue;
            }
            currentHunk.add(line);
        }
        if (!currentHunk.isEmpty()) {
            hunks.add(currentHunk);
        }
        if (hunks.isEmpty()) {
            hunks.add(updateLines);
        }
        return hunks;
    }

    /**
     * 根据 hunk 行构造旧片段或新片段文本。
     * @param hunk hunk 行集合。
     * @param oldFragment true 表示构造旧片段，false 表示构造新片段。
     * @return 片段文本。
     */
    private String buildCodexFragment(List<String> hunk, boolean oldFragment) {
        List<String> lines = new ArrayList<>();
        for (String line : hunk) {
            if (StrUtil.equals(line, "*** End of File") || StrUtil.startWith(line, "\\ No newline")) {
                continue;
            }
            if (StrUtil.startWith(line, " ")) {
                lines.add(StrUtil.subSuf(line, 1));
                continue;
            }
            if (oldFragment && StrUtil.startWith(line, "-")) {
                lines.add(StrUtil.subSuf(line, 1));
                continue;
            }
            if (!oldFragment && StrUtil.startWith(line, "+")) {
                lines.add(StrUtil.subSuf(line, 1));
            }
        }
        return String.join("\n", lines);
    }

    /**
     * 判断当前行是否进入下一个文件块或 patch 结束边界。
     * @param line 当前行文本。
     * @return 是否边界行。
     */
    private boolean isCodexPatchBoundary(String line) {
        return StrUtil.equals(line, "*** End Patch")
            || StrUtil.startWith(line, "*** Update File: ")
            || StrUtil.startWith(line, "*** Add File: ")
            || StrUtil.startWith(line, "*** Delete File: ");
    }

    /**
     * 解析 patch 内相对路径并限制在当前工作目录内，避免越界写入。
     * @param workingDirectory 工具工作目录。
     * @param relativePath patch 相对路径。
     * @return 解析后的绝对路径。
     */
    private Path resolvePatchTargetPath(Path workingDirectory, String relativePath) {
        Path resolvedPath = workingDirectory.resolve(relativePath).toAbsolutePath().normalize();
        if (!resolvedPath.startsWith(workingDirectory.toAbsolutePath().normalize())) {
            throw new BusinessException("CHAT_TOOL_APPLY_PATCH_FAILED", ErrorMessageCatalog.CHAT_TOOL_PATCH_PATH_OUT_OF_BOUND);
        }
        return resolvedPath;
    }

    /**
     * 列出当前可读的资源 URI 清单。
     */
    private ChatToolExecutionResult executeListMcpResources() {
        List<String> resources = new ArrayList<>();
        resources.add("mcp://configs");
        resources.add("skill://configs");
        resources.add("tool://configs");
        chatMcpRepository.findAll().forEach(item -> resources.add("mcp://configs/" + item.getMcpCode()));
        chatSkillRepository.findAll().forEach(item -> resources.add("skill://configs/" + item.getSkillCode()));
        chatToolRepository.findAll().forEach(item -> resources.add("tool://configs/" + item.getToolCode()));
        return new ChatToolExecutionResult(
            "list_mcp_resources",
            "可用资源共 " + resources.size() + " 个",
            Map.of("resources", resources)
        );
    }

    /**
     * 列出资源模板，说明 read_mcp_resource 的可用 URI 规则。
     */
    private ChatToolExecutionResult executeListMcpResourceTemplates() {
        List<Map<String, String>> templates = List.of(
            Map.of("uri", "mcp://configs", "description", "读取全部 MCP 配置"),
            Map.of("uri", "mcp://configs/{mcpCode}", "description", "按编码读取单个 MCP 配置"),
            Map.of("uri", "skill://configs", "description", "读取全部技能配置"),
            Map.of("uri", "skill://configs/{skillCode}", "description", "按编码读取单个技能配置"),
            Map.of("uri", "tool://configs", "description", "读取全部工具配置"),
            Map.of("uri", "tool://configs/{toolCode}", "description", "按编码读取单个工具配置")
        );
        return new ChatToolExecutionResult(
            "list_mcp_resource_templates",
            "资源模板已返回",
            Map.of("templates", templates)
        );
    }

    /**
     * 按 URI 读取 MCP/Skill/Tool 资源内容。
     */
    private ChatToolExecutionResult executeReadMcpResource(ToolInput input) {
        // 步骤 1：优先读取结构化 URI，缺失时从原始文本中提取首个资源 URI。
        String uri = prefer(input.object().getStr("uri"), findFirstUri(input.raw()));
        if (StrUtil.isBlank(uri)) {
            throw new BusinessException("CHAT_TOOL_URI_REQUIRED", ErrorMessageCatalog.CHAT_TOOL_URI_REQUIRED);
        }
        // 步骤 2：按 URI 命名空间路由到 MCP、Skill 或 Tool 配置仓储。
        Object data;
        if (StrUtil.equalsIgnoreCase(uri, "mcp://configs")) {
            data = chatMcpRepository.findAll();
        } else if (StrUtil.startWithIgnoreCase(uri, "mcp://configs/")) {
            data = chatMcpRepository.findByMcpCode(uri.substring("mcp://configs/".length()));
        } else if (StrUtil.equalsIgnoreCase(uri, "skill://configs")) {
            data = chatSkillRepository.findAll();
        } else if (StrUtil.startWithIgnoreCase(uri, "skill://configs/")) {
            data = chatSkillRepository.findBySkillCode(uri.substring("skill://configs/".length()));
        } else if (StrUtil.equalsIgnoreCase(uri, "tool://configs")) {
            data = chatToolRepository.findAll();
        } else if (StrUtil.startWithIgnoreCase(uri, "tool://configs/")) {
            data = chatToolRepository.findByToolCode(uri.substring("tool://configs/".length()));
        } else {
            throw new BusinessException("CHAT_TOOL_URI_UNSUPPORTED", ErrorMessageCatalog.CHAT_TOOL_URI_UNSUPPORTED);
        }
        // 步骤 3：资源不存在时返回明确业务错误，存在时把原 URI 和配置对象放入元数据。
        if (data == null) {
            throw new BusinessException("CHAT_TOOL_RESOURCE_NOT_FOUND", ErrorMessageCatalog.CHAT_TOOL_RESOURCE_NOT_FOUND);
        }
        return new ChatToolExecutionResult(
            "read_mcp_resource",
            "资源读取成功",
            Map.of("uri", uri, "data", data)
        );
    }

    /**
     * 更新计划步骤，保存到进程内存，便于后续调试查看。
     */
    private ChatToolExecutionResult executeUpdatePlan(ToolInput input) {
        String planId = StrUtil.blankToDefault(input.object().getStr("planId"), "default");
        List<PlanStep> steps = parsePlanSteps(input);
        if (CollUtil.isEmpty(steps)) {
            throw new BusinessException("CHAT_TOOL_PLAN_EMPTY", ErrorMessageCatalog.CHAT_TOOL_PLAN_STEP_REQUIRED);
        }
        plans.put(planId, steps);
        return new ChatToolExecutionResult(
            "update_plan",
            "计划已更新，共 " + steps.size() + " 个步骤",
            Map.of("planId", planId, "steps", steps)
        );
    }

    /**
     * 生成结构化用户提问模板与推荐选项。
     */
    private ChatToolExecutionResult executeRequestUserInput(ToolInput input) {
        String question = StrUtil.blankToDefault(input.object().getStr("question"), input.raw());
        JSONArray optionsArray = input.object().getJSONArray("options");
        List<String> options = new ArrayList<>();
        if (optionsArray != null && !optionsArray.isEmpty()) {
            optionsArray.forEach(option -> options.add(String.valueOf(option)));
        } else {
            options.add("继续当前方案（推荐）");
            options.add("切换到保守方案");
            options.add("暂停并人工确认");
        }
        return new ChatToolExecutionResult(
            "request_user_input",
            "已生成提问选项",
            Map.of("question", question, "options", options, "recommended", options.getFirst())
        );
    }

    /**
     * 读取图片基础信息（尺寸、大小、格式）。
     * <p>
     * 约束：模型有时会把远程图片地址直接传入 `path`，这里必须同时兼容本地绝对路径和可访问的
     * HTTP(S) 图片 URL，避免把 URL 当成本地文件路径解析。
     */
    private ChatToolExecutionResult executeViewImage(ToolInput input) {
        String imageReference = prefer(input.object().getStr("path"), findImagePath(input.raw()));
        if (StrUtil.isBlank(imageReference)) {
            throw new BusinessException("CHAT_TOOL_IMAGE_PATH_REQUIRED", ErrorMessageCatalog.CHAT_TOOL_IMAGE_PATH_REQUIRED);
        }
        if (isRemoteImageReference(imageReference)) {
            return executeRemoteImageView(imageReference);
        }
        return executeLocalImageView(imageReference);
    }

    /**
     * 读取本地图片并返回结构化元数据。
     * @param pathText 本地图片路径。
     * @return 图片执行结果。
     */
    private ChatToolExecutionResult executeLocalImageView(String pathText) {
        // 步骤 1：把输入路径解析为本地文件，并校验必须存在且不是目录。
        Path path = Path.of(pathText);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new BusinessException("CHAT_TOOL_IMAGE_NOT_FOUND", ErrorMessageCatalog.CHAT_TOOL_IMAGE_NOT_FOUND);
        }
        try {
            // 步骤 2：使用 ImageIO 解析图片尺寸；无法解析说明不是受支持图片格式。
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                throw new BusinessException("CHAT_TOOL_IMAGE_INVALID", ErrorMessageCatalog.CHAT_TOOL_IMAGE_INVALID);
            }
            // 步骤 3：返回绝对路径、尺寸、大小和扩展名，供模型判断图片资产可用性。
            return buildImageExecutionResult(
                path.toAbsolutePath().toString(),
                image,
                Files.size(path),
                FileUtil.extName(path.toString())
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                "CHAT_TOOL_VIEW_IMAGE_FAILED",
                ErrorMessageCatalog.CHAT_TOOL_IMAGE_READ_FAILED_PREFIX + exception.getMessage()
            );
        }
    }

    /**
     * 读取远程图片并返回结构化元数据。
     * <p>
     * 说明：这里不把远程地址落盘，避免额外 I/O 和临时文件清理问题；仅在工具层直接下载并解析字节。
     * @param imageUrl 可访问的图片 URL。
     * @return 图片执行结果。
     */
    private ChatToolExecutionResult executeRemoteImageView(String imageUrl) {
        // 步骤 1：直接下载远程图片字节，不落盘，避免额外临时文件生命周期问题。
        try (HttpResponse response = HttpRequest.get(imageUrl)
            .header("Accept-Encoding", "identity")
            .timeout(5000)
            .execute()) {
            // 步骤 2：先校验 HTTP 状态，再用 ImageIO 验证响应内容确实是图片。
            int status = response.getStatus();
            if (status < 200 || status >= 300) {
                throw new BusinessException("CHAT_TOOL_IMAGE_NOT_FOUND", ErrorMessageCatalog.CHAT_TOOL_IMAGE_NOT_FOUND);
            }
            byte[] bytes = response.bodyBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new BusinessException("CHAT_TOOL_IMAGE_INVALID", ErrorMessageCatalog.CHAT_TOOL_IMAGE_INVALID);
            }
            // 步骤 3：返回 URL、尺寸和下载字节数，保持与本地图片结果结构一致。
            return buildImageExecutionResult(
                imageUrl,
                image,
                bytes.length,
                FileUtil.extName(imageUrl)
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                "CHAT_TOOL_VIEW_IMAGE_FAILED",
                ErrorMessageCatalog.CHAT_TOOL_IMAGE_READ_FAILED_PREFIX + exception.getMessage()
            );
        }
    }

    /**
     * 组装图片读取结果，避免本地与远程分支重复维护相同的元数据结构。
     * @param pathOrUrl 图片来源路径或 URL。
     * @param image 解析后的图片对象。
     * @param sizeBytes 图片大小。
     * @param extension 图片扩展名。
     * @return 图片执行结果。
     */
    private ChatToolExecutionResult buildImageExecutionResult(String pathOrUrl, BufferedImage image, long sizeBytes, String extension) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("path", pathOrUrl);
        metadata.put("width", image.getWidth());
        metadata.put("height", image.getHeight());
        metadata.put("sizeBytes", sizeBytes);
        metadata.put("extension", extension);
        return new ChatToolExecutionResult(
            "view_image",
            "图片读取成功",
            metadata
        );
    }

    /**
     * 创建内存态子代理会话。
     */
    private ChatToolExecutionResult executeSpawnAgent(ToolInput input) {
        String prompt = StrUtil.blankToDefault(input.object().getStr("prompt"), input.raw());
        String agentId = "agent-" + UUID.fastUUID().toString(true);
        AgentSession session = new AgentSession(agentId, "running", prompt, new CopyOnWriteArrayList<>(), LocalDateTime.now(), null);
        session.messages().add("spawn: " + prompt);
        session.messages().add("result: 已接受任务，等待后续输入");
        agentSessions.put(agentId, session);
        return new ChatToolExecutionResult(
            "spawn_agent",
            "子代理已创建",
            Map.of("agentId", agentId, "status", session.status(), "createdAt", DATE_TIME_FORMATTER.format(session.createdAt()))
        );
    }

    /**
     * 向子代理写入后续输入。
     */
    private ChatToolExecutionResult executeSendInput(ToolInput input) {
        String agentId = StrUtil.blankToDefault(input.object().getStr("target"), input.object().getStr("agentId"));
        if (StrUtil.isBlank(agentId)) {
            agentId = ReUtil.get("(agent-[\\w]+)", input.raw(), 1);
        }
        if (StrUtil.isBlank(agentId)) {
            throw new BusinessException("CHAT_TOOL_AGENT_ID_REQUIRED", ErrorMessageCatalog.CHAT_TOOL_AGENT_ID_REQUIRED);
        }
        AgentSession session = agentSessions.get(agentId);
        if (session == null) {
            throw new BusinessException("CHAT_TOOL_AGENT_NOT_FOUND", ErrorMessageCatalog.CHAT_TOOL_AGENT_NOT_FOUND);
        }
        String message = StrUtil.blankToDefault(input.object().getStr("message"), input.raw());
        session.messages().add("input: " + message);
        session.messages().add("result: 已收到并处理中");
        session.status("running");
        return new ChatToolExecutionResult(
            "send_input",
            "消息已发送",
            Map.of("agentId", agentId, "messageCount", session.messages().size(), "status", session.status())
        );
    }

    /**
     * 查询子代理状态并返回最近一条结果。
     */
    private ChatToolExecutionResult executeWaitAgent(ToolInput input) {
        String agentId = StrUtil.blankToDefault(input.object().getStr("agentId"), ReUtil.get("(agent-[\\w]+)", input.raw(), 1));
        if (StrUtil.isBlank(agentId)) {
            throw new BusinessException("CHAT_TOOL_AGENT_ID_REQUIRED", ErrorMessageCatalog.CHAT_TOOL_AGENT_ID_REQUIRED);
        }
        AgentSession session = agentSessions.get(agentId);
        if (session == null) {
            throw new BusinessException("CHAT_TOOL_AGENT_NOT_FOUND", ErrorMessageCatalog.CHAT_TOOL_AGENT_NOT_FOUND);
        }
        session.status("completed");
        session.completedAt(LocalDateTime.now());
        String lastMessage = session.messages().isEmpty() ? "暂无结果" : session.messages().get(session.messages().size() - 1);
        return new ChatToolExecutionResult(
            "wait_agent",
            "子代理已完成",
            Map.of(
                "agentId", agentId,
                "status", session.status(),
                "completedAt", DATE_TIME_FORMATTER.format(Objects.requireNonNullElse(session.completedAt(), LocalDateTime.now())),
                "result", lastMessage
            )
        );
    }

    /**
     * 关闭子代理会话。
     */
    private ChatToolExecutionResult executeCloseAgent(ToolInput input) {
        String agentId = StrUtil.blankToDefault(input.object().getStr("agentId"), ReUtil.get("(agent-[\\w]+)", input.raw(), 1));
        if (StrUtil.isBlank(agentId)) {
            throw new BusinessException("CHAT_TOOL_AGENT_ID_REQUIRED", ErrorMessageCatalog.CHAT_TOOL_AGENT_ID_REQUIRED);
        }
        AgentSession session = agentSessions.get(agentId);
        if (session == null) {
            throw new BusinessException("CHAT_TOOL_AGENT_NOT_FOUND", ErrorMessageCatalog.CHAT_TOOL_AGENT_NOT_FOUND);
        }
        session.status("closed");
        session.completedAt(LocalDateTime.now());
        return new ChatToolExecutionResult(
            "close_agent",
            "子代理已关闭",
            Map.of("agentId", agentId, "status", session.status())
        );
    }

    /**
     * 恢复已关闭的子代理会话。
     */
    private ChatToolExecutionResult executeResumeAgent(ToolInput input) {
        String agentId = StrUtil.blankToDefault(input.object().getStr("agentId"), ReUtil.get("(agent-[\\w]+)", input.raw(), 1));
        if (StrUtil.isBlank(agentId)) {
            throw new BusinessException("CHAT_TOOL_AGENT_ID_REQUIRED", ErrorMessageCatalog.CHAT_TOOL_AGENT_ID_REQUIRED);
        }
        AgentSession session = agentSessions.get(agentId);
        if (session == null) {
            throw new BusinessException("CHAT_TOOL_AGENT_NOT_FOUND", ErrorMessageCatalog.CHAT_TOOL_AGENT_NOT_FOUND);
        }
        session.status("running");
        session.messages().add("resume: 会话已恢复");
        session.completedAt(null);
        return new ChatToolExecutionResult(
            "resume_agent",
            "子代理已恢复",
            Map.of("agentId", agentId, "status", session.status())
        );
    }

    /**
     * 在 chat_tool 配置里按关键词检索工具。
     */
    private ChatToolExecutionResult executeToolSearch(ToolInput input) {
        String keyword = StrUtil.blankToDefault(input.object().getStr("keyword"), input.raw()).trim();
        List<ChatTool> allTools = chatToolRepository.findAll();
        List<ChatTool> matches = allTools.stream()
            .filter(tool -> matchesKeyword(tool, keyword))
            .limit(20)
            .toList();
        return new ChatToolExecutionResult(
            "tool_search",
            "命中工具 " + matches.size() + " 个",
            Map.of("keyword", keyword, "results", matches)
        );
    }

    /**
     * 记录插件安装请求，便于管理端观察需求趋势。
     */
    private ChatToolExecutionResult executeRequestPluginInstall(ToolInput input) {
        String pluginName = StrUtil.blankToDefault(input.object().getStr("plugin"), input.raw()).trim();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("plugin", pluginName);
        request.put("requestedAt", DateUtil.now());
        pluginInstallRequests.add(request);
        return new ChatToolExecutionResult(
            "request_plugin_install",
            "插件安装申请已登记",
            Map.of("request", request, "totalRequests", pluginInstallRequests.size())
        );
    }

    /**
     * 对命令做风险评估并给出权限建议。
     */
    private ChatToolExecutionResult executeRequestPermissions(ToolInput input) {
        String command = extractCommand(input);
        if (StrUtil.isBlank(command)) {
            throw new BusinessException("CHAT_TOOL_INVALID_COMMAND", ErrorMessageCatalog.CHAT_TOOL_COMMAND_REQUIRED);
        }
        String level = detectRiskLevel(command);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("command", command);
        request.put("riskLevel", level);
        request.put("requestedAt", DateUtil.now());
        permissionRequests.add(request);
        String message = "high".equals(level) ? "命令风险较高，建议人工审批" : "命令风险可控，可自动放行";
        return new ChatToolExecutionResult(
            "request_permissions",
            message,
            Map.of("decision", request, "totalRequests", permissionRequests.size())
        );
    }

    /**
     * 查询当前目标定义。
     */
    private ChatToolExecutionResult executeGetGoal(ToolInput input) {
        ChatToolExecutionContext.GovernanceContext context = requireGoalContext();
        String goalId = readString(input.object(), "goalId", "goal_id", "id");
        String goalKey = readString(input.object(), "goalKey", "goal_key", "key");
        log.info(
            "目标工具 get_goal 调用开始: lookupMode={}, goalKey={}",
            StrUtil.isNotBlank(goalId) ? "goalId" : (StrUtil.isNotBlank(goalKey) ? "goalKey" : "active"),
            goalKey
        );
        Optional<ChatGoalView> goalOptional = chatGoalService.getGoal(context.conversationId(), context.userId(), goalId, goalKey);
        if (goalOptional.isEmpty()) {
            log.info(
                "目标工具 get_goal 未找到目标，返回空状态供模型继续创建: lookupMode={}, goalKey={}",
                StrUtil.isNotBlank(goalId) ? "goalId" : (StrUtil.isNotBlank(goalKey) ? "goalKey" : "active"),
                goalKey
            );
            // 查询空结果是目标模式的正常分支，不能作为工具异常终止聊天流；模型会据此继续调用 create_goal。
            return new ChatToolExecutionResult(
                "get_goal",
                "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。",
                Map.of("exists", false)
            );
        }
        ChatGoalView goalView = goalOptional.orElseThrow();
        log.info(
            "目标工具 get_goal 调用完成: status={}, stepCount={}",
            goalView.status(),
            goalView.steps().size()
        );
        return new ChatToolExecutionResult("get_goal", "目标读取成功", Map.of("goal", goalView));
    }

    /**
     * 创建目标定义。
     */
    private ChatToolExecutionResult executeCreateGoal(ToolInput input) {
        ChatToolExecutionContext.GovernanceContext context = requireGoalContext();
        List<ChatGoalService.StepCommand> steps = extractGoalSteps(input.object());
        ChatGoalService.CreateGoalCommand command = new ChatGoalService.CreateGoalCommand(
            readString(input.object(), "goalId", "goal_id", "id"),
            StrUtil.blankToDefault(readString(input.object(), "goalKey", "goal_key", "key"), "default"),
            StrUtil.blankToDefault(readString(input.object(), "title", "name"), "默认目标"),
            StrUtil.blankToDefault(readString(input.object(), "description", "summary"), input.raw()),
            steps
        );
        log.info(
            "目标工具 create_goal 调用开始: goalKey={}, requestedStepCount={}, titleLength={}",
            command.goalKey(),
            steps.size(),
            StrUtil.length(command.title())
        );
        ChatGoalView goalView = chatGoalService.createGoal(context.conversationId(), context.userId(), context.runId(), command);
        log.info(
            "目标工具 create_goal 调用完成: eventType={}, status={}, stepCount={}",
            goalView.eventType(),
            goalView.status(),
            goalView.steps().size()
        );
        return new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", goalView));
    }

    /**
     * 更新目标定义。
     */
    private ChatToolExecutionResult executeUpdateGoal(ToolInput input) {
        ChatToolExecutionContext.GovernanceContext context = requireGoalContext();
        List<ChatGoalService.StepCommand> steps = extractGoalSteps(input.object());
        ChatGoalService.UpdateGoalCommand command = new ChatGoalService.UpdateGoalCommand(
            readString(input.object(), "goalId", "goal_id", "id"),
            readString(input.object(), "goalKey", "goal_key", "key"),
            readString(input.object(), "title", "name"),
            readString(input.object(), "description", "summary"),
            readString(input.object(), "status", "state"),
            readString(input.object(), "progressSummary", "progress_summary", "progress"),
            steps
        );
        log.info(
            "目标工具 update_goal 调用开始: goalKey={}, status={}, requestedStepCount={}",
            command.goalKey(),
            command.status(),
            steps.size()
        );
        ChatGoalView goalView = chatGoalService.updateGoal(context.conversationId(), context.userId(), context.runId(), command);
        log.info(
            "目标工具 update_goal 调用完成: eventType={}, status={}, stepCount={}",
            goalView.eventType(),
            goalView.status(),
            goalView.steps().size()
        );
        return new ChatToolExecutionResult("update_goal", "目标已更新", Map.of("goal", goalView));
    }

    /**
     * 读取目标工具的会话治理上下文，缺少会话或用户时直接返回中文业务错误。
     */
    private ChatToolExecutionContext.GovernanceContext requireGoalContext() {
        ChatToolExecutionContext.GovernanceContext context = ChatToolExecutionContext.currentGovernanceContext()
            .orElseThrow(() -> {
                log.warn("目标工具缺少线程治理上下文，拒绝执行目标工具");
                return new BusinessException("CHAT_TOOL_GOAL_CONTEXT_REQUIRED", "目标工具必须在聊天会话中执行");
            });
        if (context.conversationId() == null || context.userId() == null) {
            log.warn(
                "目标工具上下文不完整，拒绝执行目标工具: hasConversation={}, hasUser={}, hasRun={}",
                context.conversationId() != null,
                context.userId() != null,
                context.runId() != null
            );
            throw new BusinessException("CHAT_TOOL_GOAL_CONTEXT_REQUIRED", "目标工具必须在聊天会话中执行");
        }
        return context;
    }

    /**
     * 从目标工具入参中读取 steps 数组，兼容 key/id/title/content/detail 等模型常见字段。
     */
    private List<ChatGoalService.StepCommand> extractGoalSteps(JSONObject object) {
        JSONArray steps = object.getJSONArray("steps");
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<ChatGoalService.StepCommand> commands = new ArrayList<>();
        for (Object item : steps) {
            JSONObject stepObject = item instanceof JSONObject jsonObject ? jsonObject : JSONUtil.parseObj(item);
            commands.add(new ChatGoalService.StepCommand(
                readString(stepObject, "stepKey", "step_key", "key", "id"),
                StrUtil.blankToDefault(readString(stepObject, "title", "step", "content", "name"), "步骤 " + (commands.size() + 1)),
                readString(stepObject, "status", "state"),
                readString(stepObject, "detail", "description", "reason")
            ));
        }
        return commands;
    }

    /**
     * 按多个候选字段读取字符串，降低模型参数命名差异对工具调用的影响。
     */
    private String readString(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.getStr(key);
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 给子代理追加后续任务。
     */
    private ChatToolExecutionResult executeFollowupTask(ToolInput input) {
        String agentId = StrUtil.blankToDefault(input.object().getStr("agentId"), ReUtil.get("(agent-[\\w]+)", input.raw(), 1));
        if (StrUtil.isBlank(agentId)) {
            throw new BusinessException("CHAT_TOOL_AGENT_ID_REQUIRED", ErrorMessageCatalog.CHAT_TOOL_AGENT_ID_REQUIRED);
        }
        AgentSession session = agentSessions.get(agentId);
        if (session == null) {
            throw new BusinessException("CHAT_TOOL_AGENT_NOT_FOUND", ErrorMessageCatalog.CHAT_TOOL_AGENT_NOT_FOUND);
        }
        String task = StrUtil.blankToDefault(input.object().getStr("task"), input.raw());
        session.messages().add("followup: " + task);
        session.status("running");
        return new ChatToolExecutionResult(
            "followup_task",
            "后续任务已追加",
            Map.of("agentId", agentId, "task", task, "messageCount", session.messages().size())
        );
    }

    /**
     * 列出当前会话中的全部内存态子代理。
     */
    private ChatToolExecutionResult executeListAgents() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (AgentSession agent : agentSessions.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("agentId", agent.agentId());
            item.put("status", agent.status());
            item.put("createdAt", DATE_TIME_FORMATTER.format(agent.createdAt()));
            item.put("messageCount", agent.messages().size());
            items.add(item);
        }
        return new ChatToolExecutionResult(
            "list_agents",
            "当前子代理 " + items.size() + " 个",
            Map.of("agents", items)
        );
    }

    /**
     * 按 CSV 批量创建子代理。
     */
    private ChatToolExecutionResult executeSpawnAgentsOnCsv(ToolInput input) {
        // 步骤 1：从 JSON 或原始文本中解析 CSV 路径，并确认文件存在。
        String csvPath = StrUtil.blankToDefault(input.object().getStr("csvPath"), findCsvPath(input.raw()));
        if (StrUtil.isBlank(csvPath)) {
            throw new BusinessException("CHAT_TOOL_CSV_REQUIRED", ErrorMessageCatalog.CHAT_TOOL_CSV_PATH_REQUIRED);
        }
        Path path = Path.of(csvPath);
        if (!Files.exists(path)) {
            throw new BusinessException("CHAT_TOOL_CSV_NOT_FOUND", ErrorMessageCatalog.CHAT_TOOL_CSV_NOT_FOUND);
        }
        List<Map<String, Object>> created = new ArrayList<>();
        try {
            // 步骤 2：逐行读取 CSV，每行创建一个完成态子代理会话，记录原始行号和种子字段。
            CsvReader reader = CsvUtil.getReader();
            CsvData data = reader.read(path.toFile());
            for (CsvRow row : data.getRows()) {
                String firstColumn = row.getFieldCount() > 0 ? row.get(0) : "row-" + row.getOriginalLineNumber();
                String agentId = "agent-" + UUID.fastUUID().toString(true);
                AgentSession session = new AgentSession(
                    agentId,
                    "completed",
                    "csv-task",
                    new CopyOnWriteArrayList<>(List.of("spawn-from-csv: " + firstColumn, "result: 已完成")),
                    LocalDateTime.now(),
                    LocalDateTime.now()
                );
                agentSessions.put(agentId, session);
                created.add(Map.of("agentId", agentId, "row", row.getOriginalLineNumber(), "seed", firstColumn));
            }
        } catch (Exception exception) {
            // 步骤 3：CSV 读取或解析失败时返回统一中文错误，保留底层异常摘要。
            throw new BusinessException(
                "CHAT_TOOL_CSV_PARSE_FAILED",
                ErrorMessageCatalog.CHAT_TOOL_CSV_PARSE_FAILED_PREFIX + exception.getMessage()
            );
        }
        // 步骤 4：返回本次创建的子代理摘要，供模型继续读取或汇总。
        return new ChatToolExecutionResult(
            "spawn_agents_on_csv",
            "批量子代理创建完成",
            Map.of("csvPath", path.toAbsolutePath().toString(), "count", created.size(), "agents", created)
        );
    }

    /**
     * 记录批处理子代理任务结果。
     */
    private ChatToolExecutionResult executeReportAgentJobResult(ToolInput input) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("jobId", StrUtil.blankToDefault(input.object().getStr("jobId"), "job-" + UUID.fastUUID().toString(true)));
        report.put("status", StrUtil.blankToDefault(input.object().getStr("status"), "completed"));
        report.put("summary", StrUtil.blankToDefault(input.object().getStr("summary"), input.raw()));
        report.put("reportedAt", DateUtil.now());
        jobReports.add(report);
        return new ChatToolExecutionResult(
            "report_agent_job_result",
            "任务结果已上报",
            Map.of("report", report, "totalReports", jobReports.size())
        );
    }

    /**
     * 用于连通性验证的同步测试工具。
     */
    private ChatToolExecutionResult executeTestSyncTool(ToolInput input) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("echo", input.raw());
        metadata.put("timestamp", DateUtil.now());
        metadata.put("toolCount", TOOL_CODES.size());
        return new ChatToolExecutionResult(
            "test_sync_tool",
            "test_sync_tool 调用成功",
            metadata
        );
    }

    private ToolInput parseInput(String rawInput) {
        // 步骤 1：空输入统一转换为空 JSON 对象，降低各工具解析分支的判空成本。
        String raw = StrUtil.trimToEmpty(rawInput);
        if (StrUtil.isBlank(raw)) {
            return new ToolInput("", JSONUtil.createObj());
        }
        // 步骤 2：优先尝试 JSON 对象格式，结构化参数能保留路径、布尔值和数组字段。
        if (raw.startsWith("{")) {
            try {
                JSON json = JSONUtil.parse(raw);
                if (json instanceof JSONObject object) {
                    return new ToolInput(raw, object);
                }
            } catch (Exception ignored) {
                // 回退到普通文本模式。
            }
        }
        // 步骤 3：JSON 解析失败或输入不是对象时回退普通文本，具体工具再按正则或默认字段解析。
        return new ToolInput(raw, JSONUtil.createObj());
    }

    private List<PlanStep> parsePlanSteps(ToolInput input) {
        // 步骤 1：优先读取结构化 steps 数组，保持前端或模型传入的状态字段。
        JSONArray stepsArray = input.object().getJSONArray("steps");
        if (stepsArray != null && !stepsArray.isEmpty()) {
            List<PlanStep> steps = new ArrayList<>();
            for (Object item : stepsArray) {
                JSONObject object = JSONUtil.parseObj(item);
                steps.add(new PlanStep(
                    StrUtil.blankToDefault(object.getStr("step"), "未命名步骤"),
                    StrUtil.blankToDefault(object.getStr("status"), "pending")
                ));
            }
            return steps;
        }
        // 步骤 2：没有结构化数组时回退逐行解析原始文本，支持 [x]、[~] 和 completed/in_progress 标记。
        String raw = input.raw();
        if (StrUtil.isBlank(raw)) {
            return List.of();
        }
        List<PlanStep> steps = new ArrayList<>();
        for (String line : StrUtil.split(raw, '\n')) {
            String trimmed = StrUtil.trim(line);
            if (StrUtil.isBlank(trimmed)) {
                continue;
            }
            String status = "pending";
            String normalized = trimmed.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("[x]") || normalized.contains("completed")) {
                status = "completed";
            } else if (normalized.startsWith("[~]") || normalized.contains("in_progress")) {
                status = "in_progress";
            }
            String step = trimmed.replaceFirst("^\\[[^\\]]+\\]\\s*", "");
            steps.add(new PlanStep(step, status));
        }
        // 步骤 3：返回按原始顺序解析的计划步骤，空行已过滤。
        return steps;
    }

    private boolean matchesKeyword(ChatTool tool, String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return true;
        }
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(tool.getToolCode(), normalized)
            || containsIgnoreCase(tool.getDisplayName(), normalized)
            || containsIgnoreCase(tool.getCategory(), normalized)
            || containsIgnoreCase(tool.getDescription(), normalized);
    }

    private boolean containsIgnoreCase(String source, String keywordLower) {
        return StrUtil.isNotBlank(source) && source.toLowerCase(Locale.ROOT).contains(keywordLower);
    }

    private String normalizeToolCode(String toolCode) {
        return StrUtil.trimToEmpty(toolCode).toLowerCase(Locale.ROOT);
    }

    private String extractCommand(ToolInput input) {
        return prefer(
            input.object().getStr("command"),
            input.raw()
        );
    }

    private long normalizeTimeout(Long timeoutMs) {
        if (timeoutMs == null || timeoutMs <= 0) {
            return 10000L;
        }
        return Math.min(timeoutMs, 60000L);
    }

    /**
     * 解析命令超时配置；短工具兼容 OpenClaw 的 timeout 秒级参数，旧工具继续支持 timeoutMs。
     * @param input 工具输入。
     * @return 毫秒级有界超时时间。
     */
    private long extractTimeoutMs(ToolInput input) {
        Long timeoutMs = input.object().getLong("timeoutMs");
        if (timeoutMs != null) {
            return normalizeTimeout(timeoutMs);
        }
        Long timeoutSeconds = input.object().getLong("timeout");
        if (timeoutSeconds != null) {
            return normalizeTimeout(timeoutSeconds * 1000L);
        }
        return normalizeTimeout(null);
    }

    /**
     * 规范化交互命令等待时间，避免一次 stdin 写入长期占用聊天线程。
     * @param waitMs 原始等待时间。
     * @return 有界等待时间。
     */
    private long normalizeWaitMs(Long waitMs) {
        if (waitMs == null || waitMs <= 0) {
            return 0L;
        }
        return Math.min(waitMs, 10000L);
    }

    private CommandExecution runCommand(String command, long timeoutMs) {
        return runCommand(command, timeoutMs, resolveToolWorkingDirectory());
    }

    /**
     * 在指定目录执行命令并返回标准输出/错误输出。
     * @param command 命令文本。
     * @param timeoutMs 超时毫秒数。
     * @param workingDirectory 工作目录。
     * @return 命令执行结果。
     */
    private CommandExecution runCommand(String command, long timeoutMs, Path workingDirectory) {
        // 步骤 1：在指定工作目录启动 shell 进程，并注入当前聊天绑定的 skill 目录环境变量。
        long start = System.currentTimeMillis();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(resolveShellCommand(command))
                .directory(workingDirectory.toFile());
            // shell_command 是模型最常用的技能脚本入口，必须把当前聊天绑定的 skill 目录注入到真实进程环境。
            injectSkillEnvironmentVariables(processBuilder.environment());
            Process process = processBuilder.start();
            StringBuilder stdoutBuffer = new StringBuilder();
            StringBuilder stderrBuffer = new StringBuilder();
            // 步骤 2：并行读取 stdout/stderr，避免子进程因为管道缓冲区写满而阻塞。
            Thread stdoutReader = startProcessOutputReader(process.getInputStream(), stdoutBuffer);
            Thread stderrReader = startProcessOutputReader(process.getErrorStream(), stderrBuffer);
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                // 步骤 3：超时时强制杀掉进程并返回超时标记，调用方据此决定是否重试或缩短命令。
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
                joinReader(stdoutReader);
                joinReader(stderrReader);
                return new CommandExecution(
                    buildTimedOutOutput(snapshotOutput(stdoutBuffer), snapshotOutput(stderrBuffer)),
                    -1,
                    true,
                    System.currentTimeMillis() - start
                );
            }
            // 步骤 4：正常结束时等待读线程收口，合并 stdout/stderr 和退出码返回给工具调用方。
            joinReader(stdoutReader);
            joinReader(stderrReader);
            int exitCode = process.exitValue();
            String output = buildCommandOutput(snapshotOutput(stdoutBuffer), snapshotOutput(stderrBuffer), exitCode);
            return new CommandExecution(output, exitCode, false, System.currentTimeMillis() - start);
        } catch (Exception exception) {
            // 步骤 5：命令启动或等待阶段异常统一转换为业务异常，避免泄露底层实现细节。
            throw new BusinessException(
                "CHAT_TOOL_COMMAND_FAILED",
                ErrorMessageCatalog.CHAT_TOOL_COMMAND_EXECUTE_FAILED_PREFIX + exception.getMessage()
            );
        }
    }

    /**
     * 按调用方要求等待交互命令退出，便于模型在写入 stdin 后拿到稳定的完成状态。
     * @param session 命令会话。
     * @param waitMs 等待毫秒数。
     */
    private void waitForInteractiveCommandIfRequested(CommandSession session, long waitMs) {
        if (waitMs <= 0 || !session.process().isAlive()) {
            return;
        }
        try {
            // waitMs 表示本次写入需要等待命令收口；Windows PowerShell 交互读入在收到 EOF 后才会稳定退出。
            session.writer().close();
            session.process().waitFor(waitMs, TimeUnit.MILLISECONDS);
            if (!session.process().isAlive()) {
                commandSessions.remove(session.sessionId());
            }
        } catch (Exception ignored) {
            // 等待失败不影响本次写入结果，调用方仍可根据 alive 与输出继续轮询。
        }
    }

    /**
     * 并发读取进程输出，确保长时间无输出的命令仍能被 waitFor 超时控制住。
     * @param inputStream 进程输出流。
     * @param outputBuffer 输出缓冲区。
     * @return 输出读取线程。
     */
    private Thread startProcessOutputReader(InputStream inputStream, StringBuilder outputBuffer) {
        return Thread.startVirtualThread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (outputBuffer) {
                        outputBuffer.append(line).append('\n');
                        trimBuffer(outputBuffer);
                    }
                }
            } catch (Exception ignored) {
                // 进程被超时终止时输出流会被关闭，已有输出仍保留在缓冲区。
            }
        });
    }

    /**
     * 等待输出读取线程短暂收口，避免正常退出后丢失最后几行输出。
     * @param reader 输出读取线程。
     */
    private void joinReader(Thread reader) {
        try {
            reader.join(1000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 解析工具执行目录，优先使用聊天线程绑定路径，未绑定时回退当前工作目录。
     * @return 规范化后的工作目录。
     */
    private Path resolveToolWorkingDirectory() {
        return ChatToolExecutionContext.currentToolWorkingDirectory()
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .orElseGet(() -> Path.of("").toAbsolutePath().normalize());
    }

    /**
     * 解析模型传入的工作区相对路径，并拒绝访问当前 workspace 之外的文件。
     * @param workingDirectory 当前工具工作目录。
     * @param rawPath 原始路径。
     * @return 规范化后的真实路径。
     */
    private Path resolveWorkspacePath(Path workingDirectory, String rawPath) {
        if (StrUtil.isBlank(rawPath)) {
            throw new BusinessException("CHAT_TOOL_PATH_REQUIRED", "请提供 path");
        }
        try {
            Path normalizedWorkingDirectory = workingDirectory.toAbsolutePath().normalize();
            Path requestedPath = Path.of(rawPath);
            Path resolvedPath = requestedPath.isAbsolute()
                ? requestedPath.toAbsolutePath().normalize()
                : normalizedWorkingDirectory.resolve(requestedPath).toAbsolutePath().normalize();
            if (!resolvedPath.startsWith(normalizedWorkingDirectory)) {
                throw new BusinessException("CHAT_TOOL_PATH_OUT_OF_BOUND", "路径越界，已拒绝执行");
            }
            return resolvedPath;
        } catch (InvalidPathException exception) {
            throw new BusinessException("CHAT_TOOL_PATH_INVALID", "路径格式无效: " + exception.getMessage());
        }
    }

    /**
     * 从 JSON 或普通文本中提取路径，普通文本模式用于兼容模型直接传入文件名。
     * @param input 工具输入。
     * @param fieldName JSON 字段名。
     * @return 路径文本。
     */
    private String extractPath(ToolInput input, String fieldName) {
        return prefer(input.object().getStr(fieldName), input.raw());
    }

    /**
     * 生成文件工具统一元数据，便于前端和模型确认实际操作位置。
     * @param workingDirectory 当前工具工作目录。
     * @param filePath 文件路径。
     * @return 元数据。
     */
    private Map<String, Object> workspaceFileMetadata(Path workingDirectory, Path filePath) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("path", toWorkspaceRelativePath(workingDirectory, filePath));
        metadata.put("absolutePath", filePath.toString());
        metadata.put("workingDirectory", workingDirectory.toString());
        return metadata;
    }

    /**
     * 写入结构化文件差异元数据，聊天过程卡片和右侧代码审查栏都消费同一字段。
     * @param metadata 工具结果元数据。
     * @param fileDiffs 文件级差异列表。
     */
    private void addFileDiffMetadata(Map<String, Object> metadata, List<Map<String, Object>> fileDiffs) {
        List<Map<String, Object>> normalizedDiffs = fileDiffs == null ? List.of() : fileDiffs;
        Map<String, Object> summary = summarizeFileDiffs(normalizedDiffs);
        metadata.put("fileDiffs", normalizedDiffs);
        metadata.put("diffSummary", summary);
        metadata.put(
            "diffPreview",
            normalizedDiffs.isEmpty()
                ? "未检测到差异"
                : StrUtil.maxLength(joinFileDiffText(normalizedDiffs), MAX_BUFFER_LENGTH)
        );
    }

    /**
     * 汇总文件差异数量和增删行数，供前端快速渲染徽标。
     */
    private Map<String, Object> summarizeFileDiffs(List<Map<String, Object>> fileDiffs) {
        int additions = 0;
        int deletions = 0;
        for (Map<String, Object> fileDiff : fileDiffs) {
            additions += toInt(fileDiff.get("additions"));
            deletions += toInt(fileDiff.get("deletions"));
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("filesChanged", fileDiffs.size());
        summary.put("additions", additions);
        summary.put("deletions", deletions);
        return summary;
    }

    /**
     * 合并多个文件的 diff 文本，作为折叠预览和模型观察内容。
     */
    private String joinFileDiffText(List<Map<String, Object>> fileDiffs) {
        List<String> diffTexts = new ArrayList<>();
        for (Map<String, Object> fileDiff : fileDiffs) {
            String diff = String.valueOf(fileDiff.getOrDefault("diff", ""));
            if (StrUtil.isNotBlank(diff)) {
                diffTexts.add(diff);
            }
        }
        return String.join("\n", diffTexts);
    }

    /**
     * 生成单文件 unified diff。这里使用行级 LCS，避免整文件替换导致小编辑的增删行统计失真。
     */
    private Map<String, Object> buildSingleFileDiff(
        Path workingDirectory,
        Path filePath,
        String oldContent,
        String newContent,
        String status
    ) {
        String relativePath = toWorkspaceRelativePath(workingDirectory, filePath);
        List<String> oldLines = splitDiffLines(oldContent);
        List<String> newLines = splitDiffLines(newContent);
        List<DiffLine> diffLines = buildLineDiff(oldLines, newLines);
        int additions = 0;
        int deletions = 0;
        for (DiffLine diffLine : diffLines) {
            if (diffLine.type() == '+') {
                additions++;
            } else if (diffLine.type() == '-') {
                deletions++;
            }
        }
        List<String> unifiedLines = new ArrayList<>();
        unifiedLines.add("diff --git a/" + relativePath + " b/" + relativePath);
        unifiedLines.add(StrUtil.equals(status, "added") ? "--- /dev/null" : "--- a/" + relativePath);
        unifiedLines.add(StrUtil.equals(status, "deleted") ? "+++ /dev/null" : "+++ b/" + relativePath);
        unifiedLines.add(
            "@@ -" + gitPatchStartLine(oldLines) + "," + oldLines.size()
                + " +" + gitPatchStartLine(newLines) + "," + newLines.size()
                + " @@"
        );
        for (DiffLine diffLine : diffLines) {
            unifiedLines.add(diffLine.type() + diffLine.text());
        }
        Map<String, Object> fileDiff = new LinkedHashMap<>();
        fileDiff.put("path", relativePath);
        fileDiff.put("oldPath", relativePath);
        fileDiff.put("newPath", relativePath);
        fileDiff.put("status", status);
        fileDiff.put("additions", additions);
        fileDiff.put("deletions", deletions);
        fileDiff.put("diff", String.join("\n", unifiedLines));
        return fileDiff;
    }

    /**
     * 将文本拆成 diff 行，去掉文件尾换行产生的空尾项，保持行数统计符合 git diff 习惯。
     */
    private List<String> splitDiffLines(String content) {
        if (StrUtil.isEmpty(content)) {
            return List.of();
        }
        String normalizedContent = normalizeLineEnding(content);
        String[] parts = normalizedContent.split("\n", -1);
        int lineCount = normalizedContent.endsWith("\n") ? parts.length - 1 : parts.length;
        List<String> lines = new ArrayList<>(lineCount);
        for (int index = 0; index < lineCount; index++) {
            lines.add(parts[index]);
        }
        return lines;
    }

    /**
     * 使用 LCS 生成行级差异；上下文行保留为空格前缀，便于弹窗直接渲染 unified diff。
     */
    private List<DiffLine> buildLineDiff(List<String> oldLines, List<String> newLines) {
        int[][] lcs = new int[oldLines.size() + 1][newLines.size() + 1];
        for (int oldIndex = oldLines.size() - 1; oldIndex >= 0; oldIndex--) {
            for (int newIndex = newLines.size() - 1; newIndex >= 0; newIndex--) {
                if (StrUtil.equals(oldLines.get(oldIndex), newLines.get(newIndex))) {
                    lcs[oldIndex][newIndex] = lcs[oldIndex + 1][newIndex + 1] + 1;
                } else {
                    lcs[oldIndex][newIndex] = Math.max(lcs[oldIndex + 1][newIndex], lcs[oldIndex][newIndex + 1]);
                }
            }
        }
        List<DiffLine> diffLines = new ArrayList<>();
        int oldIndex = 0;
        int newIndex = 0;
        while (oldIndex < oldLines.size() || newIndex < newLines.size()) {
            if (oldIndex < oldLines.size()
                && newIndex < newLines.size()
                && StrUtil.equals(oldLines.get(oldIndex), newLines.get(newIndex))) {
                diffLines.add(new DiffLine(' ', oldLines.get(oldIndex)));
                oldIndex++;
                newIndex++;
            } else if (newIndex < newLines.size()
                && (oldIndex >= oldLines.size() || lcs[oldIndex][newIndex + 1] >= lcs[oldIndex + 1][newIndex])) {
                diffLines.add(new DiffLine('+', newLines.get(newIndex)));
                newIndex++;
            } else {
                diffLines.add(new DiffLine('-', oldLines.get(oldIndex)));
                oldIndex++;
            }
        }
        return diffLines;
    }

    /**
     * 从标准 unified diff 文本解析出文件级差异，兼容 git diff 与 git show 输出中的 patch 段。
     */
    private List<Map<String, Object>> parseUnifiedDiffs(String diffText) {
        if (StrUtil.isBlank(diffText)) {
            return List.of();
        }
        String[] lines = normalizeLineEnding(diffText).split("\n", -1);
        List<Map<String, Object>> fileDiffs = new ArrayList<>();
        List<String> currentBlock = new ArrayList<>();
        for (String line : lines) {
            if (StrUtil.startWith(line, "diff --git ")) {
                appendParsedDiffBlock(fileDiffs, currentBlock);
                currentBlock = new ArrayList<>();
            }
            if (!currentBlock.isEmpty() || StrUtil.startWith(line, "diff --git ")) {
                currentBlock.add(line);
            }
        }
        appendParsedDiffBlock(fileDiffs, currentBlock);
        return fileDiffs;
    }

    /**
     * 解析单个 diff --git 文件块，并累计增删行数。
     */
    private void appendParsedDiffBlock(List<Map<String, Object>> fileDiffs, List<String> blockLines) {
        if (blockLines == null || blockLines.isEmpty()) {
            return;
        }
        String oldPath = null;
        String newPath = null;
        String status = "modified";
        int additions = 0;
        int deletions = 0;
        for (String line : blockLines) {
            if (StrUtil.startWith(line, "deleted file mode")) {
                status = "deleted";
            } else if (StrUtil.startWith(line, "new file mode")) {
                status = "added";
            } else if (StrUtil.startWith(line, "rename from ")) {
                oldPath = StrUtil.removePrefix(line, "rename from ").trim();
                status = "renamed";
            } else if (StrUtil.startWith(line, "rename to ")) {
                newPath = StrUtil.removePrefix(line, "rename to ").trim();
                status = "renamed";
            } else if (StrUtil.startWith(line, "--- ")) {
                oldPath = normalizeParsedDiffPath(StrUtil.removePrefix(line, "--- "));
            } else if (StrUtil.startWith(line, "+++ ")) {
                newPath = normalizeParsedDiffPath(StrUtil.removePrefix(line, "+++ "));
            } else if (StrUtil.startWith(line, "+") && !StrUtil.startWith(line, "+++")) {
                additions++;
            } else if (StrUtil.startWith(line, "-") && !StrUtil.startWith(line, "---")) {
                deletions++;
            }
        }
        String path = StrUtil.blankToDefault(StrUtil.equals(newPath, "/dev/null") ? oldPath : newPath, oldPath);
        if (StrUtil.isBlank(path) || StrUtil.equals(path, "/dev/null")) {
            path = parsePathFromDiffGitHeader(blockLines.getFirst());
        }
        Map<String, Object> fileDiff = new LinkedHashMap<>();
        fileDiff.put("path", path);
        fileDiff.put("oldPath", oldPath);
        fileDiff.put("newPath", newPath);
        fileDiff.put("status", status);
        fileDiff.put("additions", additions);
        fileDiff.put("deletions", deletions);
        fileDiff.put("diff", String.join("\n", blockLines).trim());
        fileDiffs.add(fileDiff);
    }

    /**
     * 规范化 git 文件头路径，剥离 a/b 前缀和时间戳，只保留工作区相对路径。
     */
    private String normalizeParsedDiffPath(String pathText) {
        String path = extractGitFileHeaderPathToken(pathText);
        if (StrUtil.equals(path, "/dev/null")) {
            return path;
        }
        return stripGitPatchSidePrefix(path);
    }

    /**
     * 当文件头缺失时，从 diff --git 行兜底解析路径。
     */
    private String parsePathFromDiffGitHeader(String headerLine) {
        if (!StrUtil.startWith(headerLine, "diff --git ")) {
            return "";
        }
        List<String> parts = StrUtil.split(StrUtil.removePrefix(headerLine, "diff --git "), ' ');
        if (parts.isEmpty()) {
            return "";
        }
        String candidate = parts.size() > 1 ? parts.get(1) : parts.getFirst();
        return stripGitPatchSidePrefix(candidate);
    }

    /**
     * 直接执行 git 命令并返回原始 stdout，diff 解析不能混入 shell 包装文本。
     */
    private CommandExecution runGitCommand(Path workingDirectory, List<String> args) {
        long start = System.currentTimeMillis();
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(args);
        try {
            Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(false)
                .start();
            StringBuilder stdoutBuffer = new StringBuilder();
            StringBuilder stderrBuffer = new StringBuilder();
            Thread stdoutReader = startProcessOutputReader(process.getInputStream(), stdoutBuffer);
            Thread stderrReader = startProcessOutputReader(process.getErrorStream(), stderrBuffer);
            boolean finished = process.waitFor(10000L, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
                joinReader(stdoutReader);
                joinReader(stderrReader);
                return new CommandExecution(snapshotOutput(stdoutBuffer), -1, true, System.currentTimeMillis() - start);
            }
            joinReader(stdoutReader);
            joinReader(stderrReader);
            String stdout = snapshotOutput(stdoutBuffer);
            String stderr = snapshotOutput(stderrBuffer);
            String output = process.exitValue() == 0 ? stdout : StrUtil.blankToDefault(stdout, stderr);
            return new CommandExecution(output, process.exitValue(), false, System.currentTimeMillis() - start);
        } catch (Exception exception) {
            throw new BusinessException(
                "CHAT_TOOL_GIT_DIFF_FAILED",
                "读取 git 差异失败: " + exception.getMessage()
            );
        }
    }

    /**
     * 将元数据数字字段统一转为 int，容忍 JSON 解析后出现不同 Number 类型。
     */
    private int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    /**
     * 将工作区内路径转换为正斜杠相对路径，降低 Windows 路径对模型后续调用的干扰。
     */
    private String toWorkspaceRelativePath(Path workingDirectory, Path path) {
        Path normalizedWorkingDirectory = workingDirectory.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (normalizedPath.equals(normalizedWorkingDirectory)) {
            return ".";
        }
        return normalizedWorkingDirectory.relativize(normalizedPath).toString().replace('\\', '/');
    }

    /**
     * 按 1-indexed 行号截取 read 输出，兼容 OpenClaw 的 offset/limit 语义。
     */
    private String sliceLines(String content, Integer offset, Integer limit) {
        if (offset == null && limit == null) {
            return StrUtil.maxLength(content, MAX_BUFFER_LENGTH);
        }
        String[] lines = content.split("\\R", -1);
        int startLine = offset == null || offset <= 0 ? 1 : offset;
        int fromIndex = startLine - 1;
        int toIndex = lines.length;
        if (limit != null && limit > 0) {
            toIndex = Math.min(fromIndex + limit, lines.length);
        }
        if (fromIndex >= lines.length) {
            throw new BusinessException("CHAT_TOOL_READ_OFFSET_OUT_OF_RANGE", "offset 超出文件总行数");
        }
        String result = String.join("\n", java.util.Arrays.copyOfRange(lines, fromIndex, toIndex));
        if (limit != null && limit > 0 && toIndex < lines.length) {
            result += "\n\n[" + (lines.length - toIndex) + " more lines in file. Use offset=" + (toIndex + 1) + " to continue.]";
        }
        return StrUtil.maxLength(result, MAX_BUFFER_LENGTH);
    }

    /**
     * 统计非重叠文本出现次数，用于保证 edit 默认只处理唯一匹配。
     */
    private int countOccurrences(String content, String target) {
        int count = 0;
        int fromIndex = 0;
        while (fromIndex < content.length()) {
            int nextIndex = content.indexOf(target, fromIndex);
            if (nextIndex < 0) {
                break;
            }
            count++;
            fromIndex = nextIndex + target.length();
        }
        return count;
    }

    /**
     * 返回首个发生变更的 1-indexed 行号，便于模型后续 read 精准定位。
     */
    private int firstChangedLine(String oldContent, String newContent) {
        String[] oldLines = oldContent.split("\\R", -1);
        String[] newLines = newContent.split("\\R", -1);
        int lineCount = Math.min(oldLines.length, newLines.length);
        for (int index = 0; index < lineCount; index++) {
            if (!StrUtil.equals(oldLines[index], newLines[index])) {
                return index + 1;
            }
        }
        return lineCount + 1;
    }

    /**
     * 判断文件是否满足 grep 的 glob 过滤，glob 同时匹配文件名与搜索根相对路径。
     */
    private boolean matchesGlob(Path searchRoot, Path file, PathMatcher globMatcher) {
        if (globMatcher == null) {
            return true;
        }
        Path relativePath = Files.isDirectory(searchRoot) ? searchRoot.relativize(file) : file.getFileName();
        return globMatcher.matches(file.getFileName()) || globMatcher.matches(relativePath);
    }

    /**
     * 收集单个文本文件的正则匹配行；二进制或非 UTF-8 文件跳过，不中断整次 grep。
     */
    private void collectGrepMatches(
        Path workingDirectory,
        Path file,
        Pattern compiledPattern,
        int maxResults,
        int context,
        List<String> matches
    ) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int lineIndex = 0; lineIndex < lines.size() && matches.size() < maxResults; lineIndex++) {
                String line = lines.get(lineIndex);
                if (compiledPattern.matcher(line).find()) {
                    appendGrepMatchLines(workingDirectory, file, lines, lineIndex, context, maxResults, matches);
                }
            }
        } catch (Exception ignored) {
            // 读取失败通常意味着二进制或非 UTF-8 文件，继续扫描其他文件。
        }
    }

    /**
     * 追加 grep 命中行及可选上下文行，上下文行使用 path-line-content 形态区分。
     */
    private void appendGrepMatchLines(
        Path workingDirectory,
        Path file,
        List<String> lines,
        int lineIndex,
        int context,
        int maxResults,
        List<String> matches
    ) {
        int fromIndex = Math.max(0, lineIndex - context);
        int toIndex = Math.min(lines.size() - 1, lineIndex + context);
        String relativePath = toWorkspaceRelativePath(workingDirectory, file);
        for (int index = fromIndex; index <= toIndex && matches.size() < maxResults; index++) {
            boolean matchedLine = index == lineIndex;
            String separator = matchedLine ? ":" : "-";
            matches.add(relativePath + separator + (index + 1) + separator + lines.get(index));
        }
    }

    /**
     * 读取整数参数并处理兼容别名，防止模型混用 maxResults 与 OpenClaw 的 limit。
     */
    private int normalizeIntOption(ToolInput input, String primaryKey, String aliasKey, int defaultValue, int maxValue) {
        Integer value = input.object().getInt(primaryKey);
        if (value == null && StrUtil.isNotBlank(aliasKey)) {
            value = input.object().getInt(aliasKey);
        }
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }

    /**
     * 读取布尔参数并处理 camelCase 与 snake_case 兼容别名。
     */
    private boolean getBooleanOption(ToolInput input, String primaryKey, String aliasKey, boolean defaultValue) {
        Boolean value = input.object().getBool(primaryKey);
        if (value == null && StrUtil.isNotBlank(aliasKey)) {
            value = input.object().getBool(aliasKey);
        }
        return value == null ? defaultValue : value;
    }

    private String[] resolveShellCommand(String command) {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return new String[]{"powershell", "-NoProfile", "-Command", bridgePowerShellSkillEnvironment(command)};
        }
        return new String[]{"sh", "-lc", command};
    }

    /**
     * Windows 下工具命令通过 PowerShell 执行，而部分技能文档使用 bash 风格的 ${CLAUDE_SKILL_DIR}。
     * 这里把注入到进程环境中的 CLAUDE_SKILL_DIR* 同步成同名 PowerShell 变量，兼容技能脚本路径写法。
     */
    private String bridgePowerShellSkillEnvironment(String command) {
        return """
            Get-ChildItem Env: | Where-Object { $_.Name -like 'CLAUDE_SKILL_DIR*' } | ForEach-Object { Set-Variable -Name $_.Name -Value $_.Value -Scope Local }; %s
            """.formatted(command);
    }

    /**
     * 注入 skill 环境变量到 ProcessBuilder 环境中。
     * @param env ProcessBuilder 的环境变量 Map。
     */
    private void injectSkillEnvironmentVariables(Map<String, String> env) {
        Map<String, Path> skillDirs = ChatToolExecutionContext.currentSkillDirectories();

        if (skillDirs.isEmpty()) {
            return;
        }

        if (skillDirs.size() == 1) {
            // 单个 skill：设置 CLAUDE_SKILL_DIR
            Map.Entry<String, Path> entry = skillDirs.entrySet().iterator().next();
            env.put("CLAUDE_SKILL_DIR", entry.getValue().toString());
        } else {
            // 多个 skill：分别设置 CLAUDE_SKILL_DIR_<CODE>
            for (Map.Entry<String, Path> entry : skillDirs.entrySet()) {
                String envKey = "CLAUDE_SKILL_DIR_" +
                    entry.getKey().toUpperCase(Locale.ROOT).replace("-", "_");
                env.put(envKey, entry.getValue().toString());
            }
        }
    }

    private String readStream(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
                if (builder.length() >= MAX_BUFFER_LENGTH) {
                    break;
                }
            }
            return builder.toString().trim();
        } catch (Exception exception) {
            return "";
        }
    }

    private void watchProcessOutput(CommandSession session, InputStream stream) {
        Thread.startVirtualThread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (session.outputBuffer()) {
                        session.outputBuffer().append(line).append('\n');
                        trimBuffer(session.outputBuffer());
                    }
                }
            } catch (Exception ignored) {
                // 输出读取失败时保持会话可用。
            }
        });
    }

    private void trimBuffer(StringBuilder builder) {
        if (builder.length() <= MAX_BUFFER_LENGTH) {
            return;
        }
        int overflow = builder.length() - MAX_BUFFER_LENGTH;
        builder.delete(0, overflow);
    }

    private String snapshotOutput(StringBuilder buffer) {
        synchronized (buffer) {
            return StrUtil.maxLength(buffer.toString().trim(), MAX_BUFFER_LENGTH);
        }
    }

    private String extractPatchBlock(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String fenced = ReUtil.get("```(?:diff|patch)?\\s*([\\s\\S]+?)```", raw, 1);
        if (StrUtil.isNotBlank(fenced)) {
            return fenced.trim();
        }
        return raw;
    }

    private String findFirstUri(String raw) {
        return ReUtil.get("([a-z]+://[\\w\\-/]+)", raw, 1);
    }

    private String findImagePath(String raw) {
        String remoteUrl = ReUtil.get("((?:https?://)[^\\s]+\\.(?:png|jpg|jpeg|webp|gif)(?:\\?[^\\s]+)?)", raw, 1);
        if (StrUtil.isNotBlank(remoteUrl)) {
            return remoteUrl;
        }
        String windowsPath = ReUtil.get("([A-Za-z]:\\\\[^\\s]+\\.(png|jpg|jpeg|webp|gif))", raw, 1);
        if (StrUtil.isNotBlank(windowsPath)) {
            return windowsPath;
        }
        return ReUtil.get("(/[^\\s]+\\.(png|jpg|jpeg|webp|gif))", raw, 1);
    }

    private String findCsvPath(String raw) {
        String windowsPath = ReUtil.get("([A-Za-z]:\\\\[^\\s]+\\.csv)", raw, 1);
        if (StrUtil.isNotBlank(windowsPath)) {
            return windowsPath;
        }
        return ReUtil.get("(/[^\\s]+\\.csv)", raw, 1);
    }

    /**
     * 统一将文本换行规范为 LF，避免 Windows 与 Unix 行尾差异影响 patch 匹配。
     * @param text 原始文本。
     * @return 规范化文本。
     */
    private String normalizeLineEnding(String text) {
        return StrUtil.nullToEmpty(text).replace("\r\n", "\n").replace('\r', '\n');
    }

    private String buildCommandOutput(String stdout, String stderr, int exitCode) {
        StringBuilder builder = new StringBuilder();
        builder.append("exitCode: ").append(exitCode).append('\n');
        if (StrUtil.isNotBlank(stdout)) {
            builder.append("stdout:\n").append(stdout).append('\n');
        }
        if (StrUtil.isNotBlank(stderr)) {
            builder.append("stderr:\n").append(stderr);
        }
        return StrUtil.maxLength(builder.toString().trim(), MAX_BUFFER_LENGTH);
    }

    /**
     * 组装超时命令输出，尽量保留超时前已经产生的 stdout/stderr 便于排查。
     * @param stdout 超时前标准输出。
     * @param stderr 超时前错误输出。
     * @return 命令输出摘要。
     */
    private String buildTimedOutOutput(String stdout, String stderr) {
        StringBuilder builder = new StringBuilder("命令执行超时");
        if (StrUtil.isNotBlank(stdout)) {
            builder.append("\nstdout:\n").append(stdout);
        }
        if (StrUtil.isNotBlank(stderr)) {
            builder.append("\nstderr:\n").append(stderr);
        }
        return StrUtil.maxLength(builder.toString().trim(), MAX_BUFFER_LENGTH);
    }

    private String detectRiskLevel(String command) {
        String normalized = StrUtil.trimToEmpty(command).toLowerCase(Locale.ROOT);
        List<String> highRiskTokens = List.of(
            "rm -rf", "rmdir /s", "del /f", "truncate", "drop table", "shutdown", "reboot", "format"
        );
        for (String token : highRiskTokens) {
            if (normalized.contains(token)) {
                return "high";
            }
        }
        return "low";
    }

    private String prefer(String first, String second) {
        return StrUtil.isNotBlank(first) ? first : StrUtil.blankToDefault(second, "");
    }

    /**
     * 判断图片引用是否为可直接下载的远程地址。
     * @param imageReference 图片路径或 URL。
     * @return 是否为远程地址。
     */
    private boolean isRemoteImageReference(String imageReference) {
        String normalized = StrUtil.trimToEmpty(imageReference).toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    /**
     * 工具输入对象，统一封装原始文本与可选 JSON 参数。
     */
    private record ToolInput(String raw, JSONObject object) {
    }

    /**
     * write 工具写入参数。
     * @param path 工作区内目标路径。
     * @param content 要覆盖写入的完整文件内容。
     */
    private record WriteArguments(String path, String content) {
    }

    /**
     * 命令执行结果。
     */
    private record CommandExecution(String output, int exitCode, boolean timedOut, long durationMs) {
    }

    /**
     * 标准 diff 单行对旧文件和新文件的行数贡献。
     */
    private record GitHunkLineCount(int oldLineCount, int newLineCount) {
    }

    /**
     * 内存生成 diff 时的单行模型，type 使用 unified diff 前缀字符。
     */
    private record DiffLine(char type, String text) {
    }

    /**
     * 标准 diff 文本内容及文件尾换行状态。
     */
    private record GitPatchTextContent(List<String> lines, boolean trailingNewline) {
    }

    /**
     * 后台命令会话。
     */
    private record CommandSession(
        String sessionId,
        String command,
        Process process,
        BufferedWriter writer,
        StringBuilder outputBuffer,
        LocalDateTime createdAt
    ) {
    }

    /**
     * 子代理会话快照。
     */
    private static final class AgentSession {

        /** 子代理会话标识，用于后续发送输入、等待和关闭操作。 */
        private final String agentId;
        /** 子代理状态，运行中可被 wait/close/resume 更新，需跨调用可见。 */
        private volatile String status;
        /** 子代理启动提示词，用于审计和返回会话快照。 */
        private final String prompt;
        /** 子代理消息缓冲，记录主会话发送的输入和模拟输出。 */
        private final List<String> messages;
        /** 会话创建时间，用于返回代理生命周期信息。 */
        private final LocalDateTime createdAt;
        /** 会话完成时间，代理关闭或等待完成后写入，可为空。 */
        private volatile LocalDateTime completedAt;

        private AgentSession(
            String agentId,
            String status,
            String prompt,
            List<String> messages,
            LocalDateTime createdAt,
            LocalDateTime completedAt
        ) {
            this.agentId = agentId;
            this.status = status;
            this.prompt = prompt;
            this.messages = messages;
            this.createdAt = createdAt;
            this.completedAt = completedAt;
        }

        public String agentId() {
            return agentId;
        }

        public String status() {
            return status;
        }

        public void status(String status) {
            this.status = status;
        }

        public String prompt() {
            return prompt;
        }

        public List<String> messages() {
            return messages;
        }

        public LocalDateTime createdAt() {
            return createdAt;
        }

        public LocalDateTime completedAt() {
            return completedAt;
        }

        public void completedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
        }
    }

    /**
     * 计划步骤定义。
     */
    private record PlanStep(String step, String status) {
    }
}
