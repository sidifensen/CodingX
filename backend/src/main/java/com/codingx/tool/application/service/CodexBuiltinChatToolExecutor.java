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
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 提供 Codex CLI 同名内置工具在本服务内的执行能力。
 * <p>
 * 说明：此执行器覆盖 chat_tool 预置的工具编码，所有能力均在后端进程内完成，
 * 不依赖 MCP 协议转发，便于管理端做可见性、探测与调试调用。
 */
@Component
@RequiredArgsConstructor
public class CodexBuiltinChatToolExecutor implements ChatToolExecutor {

    private static final List<String> TOOL_CODES = List.of(
        "shell_command", "apply_patch", "list_mcp_resources", "list_mcp_resource_templates", "read_mcp_resource",
        "update_plan", "request_user_input", "view_image", "spawn_agent", "send_input", "wait_agent", "close_agent",
        "resume_agent", "tool_search", "request_plugin_install", "request_permissions", "exec_command", "write_stdin",
        "get_goal", "create_goal", "update_goal", "send_message", "followup_task", "list_agents", "spawn_agents_on_csv",
        "report_agent_job_result", "test_sync_tool"
    );
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_BUFFER_LENGTH = 12000;

    private final ChatToolRepository chatToolRepository;
    private final ChatMcpRepository chatMcpRepository;
    private final ChatSkillRepository chatSkillRepository;

    private final Map<String, AgentSession> agentSessions = new ConcurrentHashMap<>();
    private final Map<String, CommandSession> commandSessions = new ConcurrentHashMap<>();
    private final Map<String, GoalState> goals = new ConcurrentHashMap<>();
    private final Map<String, List<PlanStep>> plans = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> pluginInstallRequests = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> permissionRequests = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> jobReports = new CopyOnWriteArrayList<>();
    private static final List<String> UNSUPPORTED_CODEX_RUNTIME_TOOLS = List.of(
        "list_mcp_resources", "list_mcp_resource_templates", "read_mcp_resource",
        "request_user_input", "spawn_agent", "send_input", "send_message", "wait_agent", "close_agent",
        "resume_agent", "request_plugin_install", "request_permissions", "get_goal", "create_goal",
        "update_goal", "followup_task", "list_agents", "spawn_agents_on_csv", "report_agent_job_result"
    );

    @Override
    public List<String> toolCodes() {
        return TOOL_CODES;
    }

    @Override
    public ChatToolExecutionResult execute(String toolCode, String question) {
        String normalizedCode = normalizeToolCode(toolCode);
        ToolInput input = parseInput(question);
        if (UNSUPPORTED_CODEX_RUNTIME_TOOLS.contains(normalizedCode)) {
            throw unsupportedCodexRuntimeTool(normalizedCode);
        }
        return switch (normalizedCode) {
            case "shell_command" -> executeShellCommand(input);
            case "exec_command" -> executeExecCommand(input);
            case "write_stdin" -> executeWriteStdin(input);
            case "apply_patch" -> executeApplyPatch(input);
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
        long timeoutMs = normalizeTimeout(input.object().getLong("timeoutMs", 10000L));
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
     * 启动后台命令会话，返回 sessionId 供 write_stdin 使用。
     */
    private ChatToolExecutionResult executeExecCommand(ToolInput input) {
        String command = extractCommand(input);
        if (StrUtil.isBlank(command)) {
            throw new BusinessException("CHAT_TOOL_INVALID_COMMAND", ErrorMessageCatalog.CHAT_TOOL_COMMAND_REQUIRED);
        }
        try {
            Process process = new ProcessBuilder(resolveShellCommand(command))
                .directory(resolveToolWorkingDirectory().toFile())
                .start();
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
            watchProcessOutput(session, process.getInputStream());
            watchProcessOutput(session, process.getErrorStream());
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
        String sessionId = prefer(input.object().getStr("sessionId"), ReUtil.get("sessionId[:=]\\s*([\\w-]+)", input.raw(), 1));
        if (StrUtil.isBlank(sessionId)) {
            throw new BusinessException("CHAT_TOOL_SESSION_REQUIRED", ErrorMessageCatalog.CHAT_TOOL_SESSION_ID_REQUIRED);
        }
        CommandSession session = commandSessions.get(sessionId);
        if (session == null) {
            throw new BusinessException("CHAT_TOOL_SESSION_NOT_FOUND", ErrorMessageCatalog.CHAT_TOOL_SESSION_NOT_FOUND);
        }
        String text = prefer(input.object().getStr("text"), ReUtil.get("text[:=]\\s*(.+)", input.raw(), 1));
        if (StrUtil.isBlank(text)) {
            throw new BusinessException("CHAT_TOOL_TEXT_REQUIRED", ErrorMessageCatalog.CHAT_TOOL_TEXT_REQUIRED);
        }
        try {
            session.writer().write(text);
            session.writer().newLine();
            session.writer().flush();
            waitForInteractiveCommandIfRequested(session, normalizeWaitMs(input.object().getLong("waitMs", 0L)));
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
     * 按 patch 文本真实应用到工作目录，并返回差异摘要，供前端确认改动结果。
     */
    private ChatToolExecutionResult executeApplyPatch(ToolInput input) {
        String patchText = prefer(input.object().getStr("patch"), extractPatchBlock(input.raw()));
        if (StrUtil.isBlank(patchText)) {
            throw new BusinessException("CHAT_TOOL_PATCH_REQUIRED", ErrorMessageCatalog.CHAT_TOOL_PATCH_REQUIRED);
        }
        Path workingDirectory = resolveToolWorkingDirectory();
        long startedAt = System.currentTimeMillis();
        try {
            applyPatchText(workingDirectory, patchText);
            CommandExecution diffExecution = runCommand(
                "git diff -- .",
                10000L,
                workingDirectory
            );
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("applied", Boolean.TRUE);
            metadata.put("exitCode", 0);
            metadata.put("durationMs", System.currentTimeMillis() - startedAt);
            metadata.put("workingDirectory", workingDirectory.toString());
            metadata.put("diffPreview", StrUtil.blankToDefault(diffExecution.output(), "未检测到差异"));
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
        Path tempPatch = null;
        try {
            tempPatch = Files.createTempFile("chat-tool-", ".patch");
            FileUtil.writeUtf8String(patchText, tempPatch.toFile());
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
                FileUtil.del(tempPatch.toFile());
            }
        }
    }

    /**
     * 解析并应用 Codex `*** Begin Patch` 语法，覆盖 update/add/delete 三类文件操作。
     * @param workingDirectory 工具工作目录。
     * @param patchText Codex 补丁文本。
     */
    private void applyCodexStylePatch(Path workingDirectory, String patchText) {
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
            if (StrUtil.startWith(line, "*** Delete File: ")) {
                String filePath = StrUtil.removePrefix(line, "*** Delete File: ").trim();
                Path targetPath = resolvePatchTargetPath(workingDirectory, filePath);
                FileUtil.del(targetPath.toFile());
                lineIndex++;
                continue;
            }
            lineIndex++;
        }
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
        Path sourcePath = resolvePatchTargetPath(workingDirectory, filePath);
        if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            throw new BusinessException(
                "CHAT_TOOL_APPLY_PATCH_FAILED",
                ErrorMessageCatalog.CHAT_TOOL_PATCH_TARGET_NOT_FOUND_PREFIX + filePath
            );
        }
        try {
            String originalContent = normalizeLineEnding(Files.readString(sourcePath, StandardCharsets.UTF_8));
            String updatedContent = applyCodexHunks(originalContent, updateLines);
            Path targetPath = moveToPath == null ? sourcePath : resolvePatchTargetPath(workingDirectory, moveToPath);
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }
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
        String uri = prefer(input.object().getStr("uri"), findFirstUri(input.raw()));
        if (StrUtil.isBlank(uri)) {
            throw new BusinessException("CHAT_TOOL_URI_REQUIRED", ErrorMessageCatalog.CHAT_TOOL_URI_REQUIRED);
        }
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
        Path path = Path.of(pathText);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new BusinessException("CHAT_TOOL_IMAGE_NOT_FOUND", ErrorMessageCatalog.CHAT_TOOL_IMAGE_NOT_FOUND);
        }
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                throw new BusinessException("CHAT_TOOL_IMAGE_INVALID", ErrorMessageCatalog.CHAT_TOOL_IMAGE_INVALID);
            }
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
        try (HttpResponse response = HttpRequest.get(imageUrl)
            .header("Accept-Encoding", "identity")
            .timeout(5000)
            .execute()) {
            int status = response.getStatus();
            if (status < 200 || status >= 300) {
                throw new BusinessException("CHAT_TOOL_IMAGE_NOT_FOUND", ErrorMessageCatalog.CHAT_TOOL_IMAGE_NOT_FOUND);
            }
            byte[] bytes = response.bodyBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new BusinessException("CHAT_TOOL_IMAGE_INVALID", ErrorMessageCatalog.CHAT_TOOL_IMAGE_INVALID);
            }
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
        String goalId = StrUtil.blankToDefault(input.object().getStr("goalId"), "default");
        GoalState goalState = goals.get(goalId);
        if (goalState == null) {
            throw new BusinessException("CHAT_TOOL_GOAL_NOT_FOUND", ErrorMessageCatalog.CHAT_TOOL_GOAL_NOT_FOUND);
        }
        return new ChatToolExecutionResult("get_goal", "目标读取成功", Map.of("goal", goalState));
    }

    /**
     * 创建目标定义。
     */
    private ChatToolExecutionResult executeCreateGoal(ToolInput input) {
        String goalId = StrUtil.blankToDefault(input.object().getStr("goalId"), "default");
        String title = StrUtil.blankToDefault(input.object().getStr("title"), "默认目标");
        String description = StrUtil.blankToDefault(input.object().getStr("description"), input.raw());
        GoalState goalState = new GoalState(goalId, title, description, "active", LocalDateTime.now());
        goals.put(goalId, goalState);
        return new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", goalState));
    }

    /**
     * 更新目标定义。
     */
    private ChatToolExecutionResult executeUpdateGoal(ToolInput input) {
        String goalId = StrUtil.blankToDefault(input.object().getStr("goalId"), "default");
        GoalState existing = goals.get(goalId);
        if (existing == null) {
            throw new BusinessException("CHAT_TOOL_GOAL_NOT_FOUND", ErrorMessageCatalog.CHAT_TOOL_GOAL_NOT_FOUND);
        }
        String title = StrUtil.blankToDefault(input.object().getStr("title"), existing.title());
        String description = StrUtil.blankToDefault(input.object().getStr("description"), existing.description());
        String status = StrUtil.blankToDefault(input.object().getStr("status"), existing.status());
        GoalState updated = new GoalState(goalId, title, description, status, LocalDateTime.now());
        goals.put(goalId, updated);
        return new ChatToolExecutionResult("update_goal", "目标已更新", Map.of("goal", updated));
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
            throw new BusinessException(
                "CHAT_TOOL_CSV_PARSE_FAILED",
                ErrorMessageCatalog.CHAT_TOOL_CSV_PARSE_FAILED_PREFIX + exception.getMessage()
            );
        }
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
        String raw = StrUtil.trimToEmpty(rawInput);
        if (StrUtil.isBlank(raw)) {
            return new ToolInput("", JSONUtil.createObj());
        }
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
        return new ToolInput(raw, JSONUtil.createObj());
    }

    private List<PlanStep> parsePlanSteps(ToolInput input) {
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
        long start = System.currentTimeMillis();
        try {
            Process process = new ProcessBuilder(resolveShellCommand(command))
                .directory(workingDirectory.toFile())
                .start();
            StringBuilder stdoutBuffer = new StringBuilder();
            StringBuilder stderrBuffer = new StringBuilder();
            Thread stdoutReader = startProcessOutputReader(process.getInputStream(), stdoutBuffer);
            Thread stderrReader = startProcessOutputReader(process.getErrorStream(), stderrBuffer);
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
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
            joinReader(stdoutReader);
            joinReader(stderrReader);
            int exitCode = process.exitValue();
            String output = buildCommandOutput(snapshotOutput(stdoutBuffer), snapshotOutput(stderrBuffer), exitCode);
            return new CommandExecution(output, exitCode, false, System.currentTimeMillis() - start);
        } catch (Exception exception) {
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

    private String[] resolveShellCommand(String command) {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return new String[]{"powershell", "-NoProfile", "-Command", command};
        }
        return new String[]{"sh", "-lc", command};
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
     * 对依赖真实 Codex session 的工具返回明确不可用错误，避免用内存假状态误导模型。
     * @param toolCode 工具编码。
     * @return 业务异常。
     */
    private BusinessException unsupportedCodexRuntimeTool(String toolCode) {
        return new BusinessException(
            "CHAT_TOOL_CODEX_RUNTIME_UNAVAILABLE",
            toolCode + " 暂未接入真实 Codex 运行时，不能在 Java 后端返回假成功"
        );
    }

    /**
     * 工具输入对象，统一封装原始文本与可选 JSON 参数。
     */
    private record ToolInput(String raw, JSONObject object) {
    }

    /**
     * 命令执行结果。
     */
    private record CommandExecution(String output, int exitCode, boolean timedOut, long durationMs) {
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

        private final String agentId;
        private volatile String status;
        private final String prompt;
        private final List<String> messages;
        private final LocalDateTime createdAt;
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
     * 目标定义状态。
     */
    private record GoalState(
        String goalId,
        String title,
        String description,
        String status,
        LocalDateTime updatedAt
    ) {
    }

    /**
     * 计划步骤定义。
     */
    private record PlanStep(String step, String status) {
    }
}

