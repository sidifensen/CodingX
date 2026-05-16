package com.codingx.chat.application.service;

import com.codingx.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
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

    /**
     * 返回当前后端可用的 MCP 工具清单。
     *
     * @return MCP 工具视图列表。
     */
    public List<McpToolHealthView> listTools() {
        List<ChatMcpToolExecutor> executors = chatMcpToolRegistry.all();
        return executors.stream()
            .sorted(Comparator.comparing(ChatMcpToolExecutor::toolId))
            .map(this::toHealthyView)
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
        ChatMcpToolExecutor executor = chatMcpToolRegistry.require(toolId);
        String message;
        String status;
        String statusLabel;
        boolean ok;
        try {
            String sampleQuestion = sampleQuestionFor(toolId);
            ChatMcpToolResult result = executor.execute(sampleQuestion);
            if (result == null || result.content() == null || result.content().isBlank()) {
                throw new BusinessException("CHAT_MCP_EMPTY_RESPONSE", "工具返回空结果");
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
        return baseViewFor(toolId).toBuilder()
            .ok(ok)
            .status(status)
            .statusLabel(statusLabel)
            .message(message)
            .durationMs(durationMs)
            .checkedAt(LocalDateTime.now().format(DATE_TIME_FORMATTER))
            .build();
    }

    /**
     * 将执行器映射为默认可用视图。
     *
     * @param executor MCP 执行器。
     * @return 视图对象。
     */
    private McpToolHealthView toHealthyView(ChatMcpToolExecutor executor) {
        String toolId = executor.toolId();
        return baseViewFor(toolId).toBuilder()
            .ok(true)
            .status("healthy")
            .statusLabel("可用")
            .message(toolId + " 已注册")
            .checkedAt(LocalDateTime.now().format(DATE_TIME_FORMATTER))
            .durationMs(0L)
            .build();
    }

    /**
     * 根据工具标识构建基础展示信息。
     *
     * @param toolId 工具标识。
     * @return 视图对象。
     */
    private McpToolHealthView baseViewFor(String toolId) {
        return switch (toolId) {
            case "sales_query" -> McpToolHealthView.builder()
                .toolId(toolId)
                .displayName("销售查询")
                .category("销售")
                .source("内置后端")
                .description("查询销售汇总、排名、趋势与明细")
                .sampleQuestion("本月华东销售总额是多少")
                .build();
            case "ticket_query" -> McpToolHealthView.builder()
                .toolId(toolId)
                .displayName("工单查询")
                .category("工单")
                .source("内置后端")
                .description("查询工单状态、列表、优先级与解决率")
                .sampleQuestion("列出本周紧急工单")
                .build();
            case "weather_query" -> McpToolHealthView.builder()
                .toolId(toolId)
                .displayName("天气查询")
                .category("天气")
                .source("内置后端")
                .description("查询当前天气与未来预报")
                .sampleQuestion("上海未来三天天气预报")
                .build();
            default -> McpToolHealthView.builder()
                .toolId(toolId)
                .displayName(toolId)
                .category("自定义")
                .source("内置后端")
                .description("未配置描述")
                .sampleQuestion("请执行该工具")
                .build();
        };
    }

    /**
     * 生成工具探测输入，避免探测空跑。
     *
     * @param toolId 工具标识。
     * @return 探测问题。
     */
    private String sampleQuestionFor(String toolId) {
        return switch (toolId) {
            case "sales_query" -> "本月华东销售总额是多少";
            case "ticket_query" -> "列出紧急工单列表";
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
