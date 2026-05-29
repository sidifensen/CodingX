package com.codingx.tool.application.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.tool.domain.model.ChatTool;
import com.codingx.tool.domain.repository.ChatToolRepository;
import java.util.List;
import java.util.Locale;
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

    private final ChatToolRepository chatToolRepository;
    private final ChatToolExecutionService chatToolExecutionService;

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
        StpUtil.checkLogin();
        String normalizedToolCode = normalizeToolCode(toolCode);
        ensureToolWhitelisted(normalizedToolCode);
        ensureToolEnabled(normalizedToolCode);
        ensureHighRiskConfirmed(normalizedToolCode, question, confirmHighRisk);
        return chatToolExecutionService.execute(normalizedToolCode, question);
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
