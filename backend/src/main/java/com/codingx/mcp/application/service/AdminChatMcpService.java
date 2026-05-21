package com.codingx.mcp.application.service;

import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供管理端 MCP 工具列表与健康探测能力。
 */
@Service
@RequiredArgsConstructor
public class AdminChatMcpService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChatMcpToolRegistry chatMcpToolRegistry;
    private final ChatMcpRepository chatMcpRepository;

    /**
     * 返回当前后端可用的 MCP 工具清单。
     *
     * @return MCP 工具视图列表。
     */
    public List<McpToolHealthView> listTools() {
        Map<String, ChatMcpToolExecutor> executorMap = chatMcpToolRegistry.all().stream()
            .collect(Collectors.toMap(ChatMcpToolExecutor::toolId, Function.identity(), (left, right) -> left));
        return chatMcpRepository.findAll().stream()
            .sorted(Comparator.comparing(ChatMcp::getSortNo, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChatMcp::getMcpCode, Comparator.nullsLast(String::compareToIgnoreCase)))
            .map(mcp -> toConfiguredView(mcp, executorMap.get(mcp.getMcpCode())))
            .collect(Collectors.toList());
    }

    /**
     * 对单个工具执行一次探测调用，返回可读探测结果。
     *
     * @param toolId 工具标识。
     * @return 探测结果。
     */
    public McpToolHealthView pingTool(String toolId) {
        long startedAt = System.currentTimeMillis();
        ChatMcp configuredMcp = chatMcpRepository.findByMcpCode(toolId);
        if (configuredMcp == null || configuredMcp.getDeleted() != null && configuredMcp.getDeleted() == 1) {
            throw new BusinessException("CHAT_MCP_NOT_FOUND", ErrorMessageCatalog.CHAT_MCP_CONFIG_NOT_FOUND);
        }
        ChatMcpToolExecutor executor = chatMcpToolRegistry.require(toolId);
        String message;
        String status;
        String statusLabel;
        boolean ok;
        try {
            String sampleQuestion = sampleQuestionFor(toolId);
            ChatMcpToolResult result = executor.execute(sampleQuestion);
            if (result == null || result.content() == null || result.content().isBlank()) {
                throw new BusinessException("CHAT_MCP_EMPTY_RESPONSE", ErrorMessageCatalog.CHAT_MCP_EMPTY_RESPONSE);
            }
            ok = true;
            status = "healthy";
            statusLabel = "可用";
            message = toolId + " 可用";
        } catch (Exception exception) {
            ok = false;
            status = "failed";
            statusLabel = "异常";
            message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? toolId + " 探测失败"
                : exception.getMessage();
        }

        long durationMs = Math.max(1, System.currentTimeMillis() - startedAt);
        return toConfiguredView(configuredMcp, executor).toBuilder()
            .ok(ok)
            .status(status)
            .statusLabel(statusLabel)
            .message(message)
            .durationMs(durationMs)
            .checkedAt(LocalDateTime.now().format(DATE_TIME_FORMATTER))
            .build();
    }

    /**
     * 将 MCP 配置映射为后台状态视图。
     *
     * @param configuredMcp MCP 配置。
     * @param executor 可选执行器。
     * @return 视图对象。
     */
    private McpToolHealthView toConfiguredView(ChatMcp configuredMcp, ChatMcpToolExecutor executor) {
        String toolId = configuredMcp.getMcpCode();
        boolean enabled = configuredMcp.getEnabled() != null && configuredMcp.getEnabled() == 1;
        boolean hasExecutor = executor != null;
        String status;
        String statusLabel;
        String message;
        if (!enabled) {
            status = "degraded";
            statusLabel = "禁用";
            message = toolId + " 已禁用";
        } else if (hasExecutor) {
            status = "healthy";
            statusLabel = "可用";
            message = toolId + " 已注册";
        } else {
            status = "failed";
            statusLabel = "未接入";
            message = toolId + " 缺少执行器";
        }
        return McpToolHealthView.builder()
            .toolId(toolId)
            .displayName(configuredMcp.getDisplayName())
            .category(configuredMcp.getCategory())
            .source(configuredMcp.getSourceType())
            .description(configuredMcp.getDescription())
            .sampleQuestion(sampleQuestionFor(toolId))
            .ok(enabled && hasExecutor)
            .status(status)
            .statusLabel(statusLabel)
            .message(message)
            .checkedAt(LocalDateTime.now().format(DATE_TIME_FORMATTER))
            .durationMs(0L)
            .build();
    }

    /**
     * 生成工具探测输入，避免探测空跑。
     *
     * @param toolId 工具标识。
     * @return 探测问题。
     */
    private String sampleQuestionFor(String toolId) {
        return switch (toolId) {
            case "weather_query" -> "北京今天天气怎么样";
            case "code_search" -> "请查找 ChatController 中 sendMessage 的实现";
            default -> "请返回当前工具状态";
        };
    }

    /**
     * MCP 工具健康视图。
     *
     * @param toolId 工具标识。
     * @param displayName 展示名。
     * @param category 分类。
     * @param source 来源。
     * @param status 状态码。
     * @param statusLabel 状态文案。
     * @param ok 是否可用。
     * @param message 探测说明。
     * @param description 功能描述。
     * @param sampleQuestion 样例问题。
     * @param checkedAt 最近探测时间。
     * @param durationMs 探测耗时。
     */
    @Builder(toBuilder = true)
    public record McpToolHealthView(
        String toolId,
        String displayName,
        String category,
        String source,
        String status,
        String statusLabel,
        boolean ok,
        String message,
        String description,
        String sampleQuestion,
        String checkedAt,
        Long durationMs
    ) {
    }
}

