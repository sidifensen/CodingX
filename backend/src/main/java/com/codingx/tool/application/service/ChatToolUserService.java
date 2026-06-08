package com.codingx.tool.application.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.application.service.ChatWorkspaceBindingService;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.tool.domain.model.ChatTool;
import com.codingx.tool.domain.repository.ChatToolRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供用户态工具调用能力，统一收敛白名单与高风险确认策略。
 */
@Service
@RequiredArgsConstructor
public class ChatToolUserService {

    private static final Set<String> USER_ALLOWED_TOOL_CODES = Set.of(
        "read",
        "write",
        "edit",
        "bash",
        "grep",
        "find",
        "ls",
        "shell_command",
        "exec_command",
        "write_stdin",
        "apply_patch",
        "git_diff",
        "list_mcp_resources",
        "list_mcp_resource_templates",
        "read_mcp_resource",
        "view_image",
        "tool_search"
    );

    private static final Set<String> HIGH_RISK_TOOL_CODES = Set.of(
        "write",
        "edit",
        "bash",
        "shell_command",
        "exec_command",
        "write_stdin",
        "apply_patch"
    );

    private static final List<String> HIGH_RISK_COMMAND_TOKENS = List.of(
        "rm -rf",
        "rmdir /s",
        "del /f",
        "truncate",
        "drop table",
        "shutdown",
        "reboot",
        "format"
    );

    /**
     * 工具配置仓储，用于确认用户态工具是否存在且已启用。
     */
    private final ChatToolRepository chatToolRepository;

    /**
     * 工具执行服务，负责按工具编码分派到真实执行器。
     */
    private final ChatToolExecutionService chatToolExecutionService;

    /**
     * 聊天工作区绑定服务，用于让用户态工具调用继承桌面端当前选择的本地仓库目录。
     */
    private final ChatWorkspaceBindingService chatWorkspaceBindingService;

    /**
     * 调用当前登录用户允许访问的工具。
     * @param toolCode 工具编码。
     * @param question 调用载荷。
     * @return 执行结果。
     */
    public ChatToolExecutionResult invokeForCurrentUser(String toolCode, String question) {
        return invokeForCurrentUser(toolCode, question, true);
    }

    /**
     * 调用当前登录用户允许访问的工具，并对高风险命令执行确认校验。
     * @param toolCode 工具编码。
     * @param question 调用载荷。
     * @param confirmHighRisk 是否已确认高风险调用。
     * @return 执行结果。
     */
    public ChatToolExecutionResult invokeForCurrentUser(String toolCode, String question, boolean confirmHighRisk) {
        return invokeForCurrentUser(toolCode, question, confirmHighRisk, null, null);
    }

    /**
     * 调用当前登录用户允许访问的工具，并允许页面显式指定当前会话工作区。
     * @param toolCode 工具编码。
     * @param question 调用载荷。
     * @param confirmHighRisk 是否已确认高风险调用。
     * @param workspaceId 当前会话工作空间标识，可为空。
     * @param repositoryPath 当前前端分区目录，仅用于用户级绑定兜底匹配。
     * @return 执行结果。
     */
    public ChatToolExecutionResult invokeForCurrentUser(
        String toolCode,
        String question,
        boolean confirmHighRisk,
        Long workspaceId,
        String repositoryPath
    ) {
        // 步骤 1：用户态工具调用必须先确认登录态，后续权限判断都基于当前登录用户。
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();
        // 步骤 2：工具编码统一小写后依次校验白名单、启用状态和高风险确认。
        String normalizedToolCode = normalizeToolCode(toolCode);
        ensureToolWhitelisted(normalizedToolCode);
        ensureToolEnabled(normalizedToolCode);
        ensureHighRiskConfirmed(normalizedToolCode, question, confirmHighRisk);
        // 步骤 3：侧栏等用户态工具入口没有聊天流的执行上下文，需要临时绑定当前用户的本地仓库目录。
        return executeWithCurrentUserWorkspace(userId, normalizedToolCode, question, workspaceId, repositoryPath);
    }

    /**
     * 在当前用户绑定的仓库目录中执行工具，并在结束后恢复原有线程上下文。
     * @param userId 当前登录用户 ID。
     * @param normalizedToolCode 规范化工具编码。
     * @param question 调用载荷。
     * @param workspaceId 当前会话工作空间标识，可为空；存在时优先按持久化工作空间定位目录。
     * @param repositoryPath 当前页面本地目录，可为空；仅在用户级绑定一致时作为兜底。
     * @return 工具执行结果。
     */
    private ChatToolExecutionResult executeWithCurrentUserWorkspace(
        Long userId,
        String normalizedToolCode,
        String question,
        Long workspaceId,
        String repositoryPath
    ) {
        Optional<Path> previousWorkingDirectory = ChatToolExecutionContext.currentToolWorkingDirectory();
        Optional<Path> explicitWorkspacePath = findExplicitWorkspacePath(userId, workspaceId);
        Optional<Path> boundRepositoryPath = explicitWorkspacePath.isPresent()
            ? explicitWorkspacePath
            : findMatchingUserRepositoryPath(userId, repositoryPath);
        try {
            // 步骤 1：优先使用当前会话 workspaceId 对应目录，再回退用户最近绑定目录，确保 git_diff 读当前项目。
            boundRepositoryPath.ifPresent(ChatToolExecutionContext::bindToolWorkingDirectory);
            // 步骤 2：校验通过后只传递规范化编码给执行层，避免大小写差异导致注册表找不到执行器。
            return chatToolExecutionService.execute(normalizedToolCode, question);
        } finally {
            // 步骤 3：恢复调用前上下文；没有旧上下文时清理 ThreadLocal，避免 servlet 线程复用串到下次请求。
            if (previousWorkingDirectory.isPresent()) {
                ChatToolExecutionContext.bindToolWorkingDirectory(previousWorkingDirectory.get());
            } else {
                ChatToolExecutionContext.bindToolWorkingDirectory(null);
            }
        }
    }

    /**
     * 查询当前用户拥有的显式工作空间目录，避免侧栏多工作区切换后读到旧绑定。
     */
    private Optional<Path> findExplicitWorkspacePath(Long userId, Long workspaceId) {
        if (workspaceId == null) {
            return Optional.empty();
        }
        Optional<Path> workspacePath = chatWorkspaceBindingService.findRepositoryPathByWorkspaceId(workspaceId, userId);
        return workspacePath == null ? Optional.empty() : workspacePath;
    }

    /**
     * 读取用户级目录绑定；当前端传入路径时只接受与用户绑定一致的目录，避免任意路径直接进入工具上下文。
     */
    private Optional<Path> findMatchingUserRepositoryPath(Long userId, String repositoryPath) {
        Optional<Path> boundRepositoryPath = chatWorkspaceBindingService.findRepositoryPathByUserId(userId);
        if (boundRepositoryPath == null) {
            boundRepositoryPath = Optional.empty();
        }
        if (boundRepositoryPath.isEmpty() || StrUtil.isBlank(repositoryPath)) {
            return boundRepositoryPath;
        }
        Path requestedPath;
        try {
            requestedPath = Path.of(repositoryPath).toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
        Path boundPath = boundRepositoryPath.get().toAbsolutePath().normalize();
        return boundPath.equals(requestedPath) ? boundRepositoryPath : Optional.empty();
    }

    /**
     * 校验是否属于用户态白名单工具。
     * @param normalizedToolCode 规范化工具编码。
     */
    private void ensureToolWhitelisted(String normalizedToolCode) {
        if (!USER_ALLOWED_TOOL_CODES.contains(normalizedToolCode)) {
            throw new BusinessException("CHAT_TOOL_NOT_ALLOWED", ErrorMessageCatalog.CHAT_TOOL_NOT_ALLOWED);
        }
    }

    /**
     * 校验工具配置是否启用，避免调用被管理端禁用的能力。
     * @param normalizedToolCode 规范化工具编码。
     */
    private void ensureToolEnabled(String normalizedToolCode) {
        ChatTool chatTool = chatToolRepository.findByToolCode(normalizedToolCode);
        if (chatTool == null || chatTool.getEnabled() == null || chatTool.getEnabled() != 1) {
            throw new BusinessException("CHAT_TOOL_DISABLED", ErrorMessageCatalog.CHAT_TOOL_DISABLED);
        }
    }

    /**
     * 对高风险工具和命令做二次确认，防止误触发破坏性操作。
     * @param normalizedToolCode 规范化工具编码。
     * @param question 调用载荷。
     * @param confirmHighRisk 是否确认执行。
     */
    private void ensureHighRiskConfirmed(String normalizedToolCode, String question, boolean confirmHighRisk) {
        if (!isHighRiskCall(normalizedToolCode, question)) {
            return;
        }
        if (!confirmHighRisk) {
            throw new BusinessException(
                "CHAT_TOOL_HIGH_RISK_CONFIRM_REQUIRED",
                ErrorMessageCatalog.CHAT_TOOL_HIGH_RISK_CONFIRM_REQUIRED
            );
        }
    }

    /**
     * 按工具类型与命令内容识别高风险调用。
     * @param normalizedToolCode 规范化工具编码。
     * @param question 调用载荷。
     * @return 是否高风险。
     */
    private boolean isHighRiskCall(String normalizedToolCode, String question) {
        if (!HIGH_RISK_TOOL_CODES.contains(normalizedToolCode)) {
            return false;
        }
        if ("write".equals(normalizedToolCode) || "edit".equals(normalizedToolCode) || "apply_patch".equals(normalizedToolCode)) {
            return true;
        }
        String normalizedQuestion = extractRiskDetectionText(question).toLowerCase(Locale.ROOT);
        if (StrUtil.isBlank(normalizedQuestion)) {
            return true;
        }
        for (String token : HIGH_RISK_COMMAND_TOKENS) {
            if (normalizedQuestion.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取高风险识别文本，优先读取 JSON 内 command 字段，避免请求壳干扰命令判断。
     * @param question 原始调用载荷。
     * @return 规范化后的检测文本。
     */
    private String extractRiskDetectionText(String question) {
        String rawQuestion = StrUtil.trimToEmpty(question);
        if (!rawQuestion.startsWith("{")) {
            return rawQuestion;
        }
        try {
            JSONObject object = JSONUtil.parseObj(rawQuestion);
            return StrUtil.blankToDefault(object.getStr("command"), rawQuestion);
        } catch (Exception ignored) {
            return rawQuestion;
        }
    }

    /**
     * 规范化工具编码，统一以小写匹配配置表与白名单。
     * @param toolCode 原始工具编码。
     * @return 规范化编码。
     */
    private String normalizeToolCode(String toolCode) {
        return StrUtil.trimToEmpty(toolCode).toLowerCase(Locale.ROOT);
    }
}
