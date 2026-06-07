package com.codingx.mcp.application.service;

import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.mcp.application.executor.ChatMcpToolExecutor;
import com.codingx.mcp.application.executor.ChatMcpToolRegistry;
import com.codingx.mcp.application.executor.ChatMcpToolResult;
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

    /**
     * MCP 工具注册表，用于确认配置项是否已经接入真实执行器。
     */
    private final ChatMcpToolRegistry chatMcpToolRegistry;

    /**
     * MCP 配置仓储，用于读取管理端维护的配置态。
     */
    private final ChatMcpRepository chatMcpRepository;

    /**
     * 返回当前后端可用的 MCP 工具清单。
     *
     * @return MCP 工具视图列表。
     */
    public List<McpToolHealthView> listTools() {
        // 步骤 1：先把运行期执行器按 toolId 建立索引，后续逐条配置可快速判断接入状态。
        Map<String, ChatMcpToolExecutor> executorMap = chatMcpToolRegistry.all().stream()
            .collect(Collectors.toMap(ChatMcpToolExecutor::toolId, Function.identity(), (left, right) -> left));
        // 步骤 2：按管理端配置排序返回健康视图，未接入执行器的配置也要展示失败状态。
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
        // 步骤 1：记录探测开始时间，用于最终健康视图展示耗时。
        long startedAt = System.currentTimeMillis();
        // 步骤 2：探测必须基于未删除配置，避免直接探测历史配置或脏 ID。
        ChatMcp configuredMcp = chatMcpRepository.findByMcpCode(toolId);
        if (configuredMcp == null || configuredMcp.getDeleted() != null && configuredMcp.getDeleted() == 1) {
            throw new BusinessException("CHAT_MCP_NOT_FOUND", ErrorMessageCatalog.CHAT_MCP_CONFIG_NOT_FOUND);
        }
        // 步骤 3：注册表没有执行器时会抛出未接入异常，管理端据此定位配置问题。
        ChatMcpToolExecutor executor = chatMcpToolRegistry.require(toolId);
        String message;
        String status;
        String statusLabel;
        boolean ok;
        try {
            // 步骤 4：使用固定样例问题探测真实执行器，空响应视为不可用。
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
            // 步骤 5：执行异常不会继续向外抛出，而是转换为失败健康视图供管理端展示。
            ok = false;
            status = "failed";
            statusLabel = "异常";
            message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? toolId + " 探测失败"
                : exception.getMessage();
        }

        // 步骤 6：把探测结果合并回配置视图，保留配置态字段和最新探测状态。
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
        // 步骤 1：同时考虑配置启用状态和执行器接入状态，避免启用但无执行器的能力被误认为可用。
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
        // 步骤 2：返回管理端可直接展示的健康视图，默认耗时为 0，真实探测会覆盖。
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
        String toolId, // MCP 工具标识，来自配置表 mcp_code。
        String displayName, // MCP 展示名称。
        String category, // MCP 分类，用于管理端筛选和用户侧分组。
        String source, // MCP 来源类型。
        String status, // 健康状态编码，例如 healthy、degraded、failed。
        String statusLabel, // 健康状态中文文案。
        boolean ok, // 是否可用，只有启用且执行器探测成功时为 true。
        String message, // 管理端展示的状态说明或失败原因。
        String description, // MCP 能力说明。
        String sampleQuestion, // 健康探测使用的样例问题。
        String checkedAt, // 最近探测时间，格式为 yyyy-MM-dd HH:mm:ss。
        Long durationMs // 最近探测耗时，单位毫秒。
    ) {
    }
}

