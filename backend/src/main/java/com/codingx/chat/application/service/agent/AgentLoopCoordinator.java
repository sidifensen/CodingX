package com.codingx.chat.application.service.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.codingx.common.support.ai.AiToolCall;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Agent Loop 确定性规则协调器。
 * 业务边界：该服务只处理轮次、安全上限、重复工具调用和完成原因，不直接调用模型或执行工具。
 */
@Service
public class AgentLoopCoordinator {

    /** Agent Loop 支持的最大安全轮次，目标模式长任务可用；普通聊天在 RuntimeSettingService 中仍先裁到 20。 */
    private static final int MAX_SUPPORTED_ROUNDS = 60;
    /** 工具轮次超过上限时返回给用户和日志的统一中文提示。 */
    private static final String MAX_ROUNDS_MESSAGE = "本地工具调用轮次超过上限，请收敛工具调用后重试";
    /** write 工具参数中的目标路径字段，去重时只依赖该字段而不依赖完整 content。 */
    private static final Pattern WRITE_PATH_FIELD_PATTERN = Pattern.compile(
        "\"path\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"",
        Pattern.DOTALL
    );

    /**
     * 将运行时配置的工具轮次裁剪到安全范围。
     * @param configuredRounds 配置中心读取的轮次。
     * @return 1 到 60 之间的安全轮次。
     */
    public int normalizeMaxRounds(int configuredRounds) {
        return Math.max(1, Math.min(configuredRounds, MAX_SUPPORTED_ROUNDS));
    }

    /**
     * 构造工具调用去重键。
     * @param toolCall 模型请求的工具调用。
     * @return 基于工具编码和参数的稳定 key。
     */
    public String deduplicateKey(AiToolCall toolCall) {
        return deduplicateKey(toolCall, null);
    }

    /**
     * 构造工具调用去重键。
     * 业务约束：write 属于覆盖写入类副作用工具，模型可能每轮微调 content；
     * 同一目标路径重复写入应被视为重复调用，避免反复覆盖文件直到工具轮次上限。
     *
     * @param toolCall 模型请求的工具调用。
     * @param workingDirectory 当前工具工作目录，可为空；用于把相对/绝对路径规整成同一 workspace key。
     * @return 基于工具编码和语义参数的稳定 key。
     */
    public String deduplicateKey(AiToolCall toolCall, Path workingDirectory) {
        if (toolCall == null) {
            return "";
        }
        String toolCode = StrUtil.blankToDefault(toolCall.toolCode(), "unknown");
        if ("write".equalsIgnoreCase(toolCode)) {
            String writePath = extractWritePath(toolCall.arguments());
            if (StrUtil.isNotBlank(writePath)) {
                return toolCode.toLowerCase(Locale.ROOT) + "\npath:" + normalizeWritePathKey(writePath, workingDirectory);
            }
        }
        return toolCode + "\n" + StrUtil.blankToDefault(toolCall.arguments(), "");
    }

    /**
     * 判断当前调用是否为 write 语义重复调用。
     * @param toolCall 模型请求的工具调用。
     * @return true 表示该工具应按目标路径做确定性收口。
     */
    public boolean isWriteToolCall(AiToolCall toolCall) {
        return toolCall != null && "write".equalsIgnoreCase(StrUtil.trimToEmpty(toolCall.toolCode()));
    }

    /**
     * 从 write arguments 中提取 path；严格 JSON 失败时只提取短 path 字段，不解析可能包含未转义引号的 content。
     * @param arguments 模型返回的工具参数。
     * @return 目标路径，无法识别时返回 null。
     */
    public String extractWritePath(String arguments) {
        if (StrUtil.isBlank(arguments)) {
            return null;
        }
        try {
            String path = JSONUtil.parseObj(arguments).getStr("path");
            if (StrUtil.isNotBlank(path)) {
                return path;
            }
        } catch (Exception ignored) {
            // 非严格 JSON 的 HTML content 不影响 path 恢复；后续只用正则读取顶层 path 字段。
        }
        Matcher matcher = WRITE_PATH_FIELD_PATTERN.matcher(arguments);
        if (!matcher.find()) {
            return null;
        }
        return unescapeJsonString(matcher.group(1));
    }

    /**
     * 将 write 的目标路径规范化为稳定 key。
     * @param pathText 模型传入路径。
     * @param workingDirectory 当前工作目录，可为空。
     * @return 规范化后的路径 key。
     */
    private String normalizeWritePathKey(String pathText, Path workingDirectory) {
        try {
            Path rawPath = Path.of(pathText);
            Path normalizedPath;
            if (workingDirectory != null) {
                Path normalizedWorkingDirectory = workingDirectory.toAbsolutePath().normalize();
                normalizedPath = rawPath.isAbsolute()
                    ? rawPath.toAbsolutePath().normalize()
                    : normalizedWorkingDirectory.resolve(rawPath).normalize();
                if (normalizedPath.startsWith(normalizedWorkingDirectory)) {
                    return normalizePathSeparators(normalizedWorkingDirectory.relativize(normalizedPath).toString());
                }
            } else {
                normalizedPath = rawPath.normalize();
            }
            return normalizePathSeparators(normalizedPath.toString());
        } catch (RuntimeException exception) {
            // 非法路径会在真实执行阶段由工具执行器返回中文错误；去重 key 只做保守回退。
            return StrUtil.trimToEmpty(pathText).replace('\\', '/');
        }
    }

    /**
     * 统一路径分隔符并去掉常见的当前目录前缀，保证 Windows/Unix 下 key 稳定。
     */
    private String normalizePathSeparators(String pathText) {
        String normalized = StrUtil.trimToEmpty(pathText).replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    /**
     * 反转义 JSON 字符串中的常见转义，足够支持 write.path 的短字符串场景。
     */
    private String unescapeJsonString(String value) {
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
                default -> {
                    builder.append('\\');
                    builder.append(escaped);
                }
            }
        }
        return builder.toString();
    }

    /**
     * 根据本轮状态解析 Agent Loop 下一步动作和结束原因。
     * @param zeroBasedRound 当前轮次，从 0 开始。
     * @param maxRounds 最大轮次。
     * @param hasToolCalls 本轮是否存在允许执行的工具调用。
     * @param toolError 本轮是否出现工具错误。
     * @param modelError 本轮是否出现模型错误。
     * @return 本轮判定结果。
     */
    public AgentLoopResult resolveRoundResult(
        int zeroBasedRound,
        int maxRounds,
        boolean hasToolCalls,
        boolean toolError,
        boolean modelError
    ) {
        // 步骤 1：模型错误和工具错误优先终止，避免继续追加误导性上下文。
        if (modelError) {
            return new AgentLoopResult(AgentLoopCompletionReason.MODEL_ERROR, false, null);
        }
        if (toolError) {
            return new AgentLoopResult(AgentLoopCompletionReason.TOOL_ERROR, false, null);
        }
        // 步骤 2：没有工具调用说明当前模型输出可作为最终回答，交由外层 flush 正文。
        if (!hasToolCalls) {
            return new AgentLoopResult(AgentLoopCompletionReason.NO_TOOL_CALL, false, null);
        }
        // 步骤 3：最后一轮仍有工具调用时停止循环，避免执行后无法继续生成最终答复。
        if (zeroBasedRound >= normalizeMaxRounds(maxRounds) - 1) {
            return new AgentLoopResult(AgentLoopCompletionReason.MAX_ROUNDS, false, MAX_ROUNDS_MESSAGE);
        }
        // 步骤 4：工具调用可以执行并回灌，外层进入下一轮模型生成。
        return new AgentLoopResult(AgentLoopCompletionReason.TOOL_CALLS_COMPLETED, true, null);
    }

    /**
     * @return 工具轮次超过上限时的统一中文提示。
     */
    public String maxRoundsMessage() {
        return MAX_ROUNDS_MESSAGE;
    }
}
