package com.codingx.tool.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.tool.domain.model.ChatTool;
import com.codingx.tool.domain.repository.ChatToolRepository;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.exception.NotFoundException;
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
 * 提供管理端工具配置管理能力。
 */
@Service
@RequiredArgsConstructor
public class AdminChatToolService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChatToolRepository chatToolRepository;
    private final ChatToolRegistry chatToolRegistry;
    private final ChatToolExecutionService chatToolExecutionService;

    /**
     * 查询全部工具配置。
     * @return 工具配置列表。
     */
    public List<ChatTool> listAll() {
        return chatToolRepository.findAll();
    }

    /**
     * 创建工具配置。
     * @param request 请求参数。
     * @return 新增后的工具配置。
     */
    public ChatTool create(ChatTool request) {
        validateRequired(request);
        String normalizedToolCode = request.getToolCode().trim();
        if (chatToolRepository.existsByToolCode(normalizedToolCode, null)) {
            throw new BusinessException("CHAT_TOOL_DUPLICATE_CODE", ErrorMessageCatalog.CHAT_TOOL_DUPLICATE_CODE);
        }
        LocalDateTime now = LocalDateTime.now();
        ChatTool persisted = request.toBuilder()
            .id(IdUtil.getSnowflakeNextId())
            .toolCode(normalizedToolCode)
            .displayName(request.getDisplayName().trim())
            .sourceType(StrUtil.blankToDefault(request.getSourceType(), "codex-cli"))
            .enabled(request.getEnabled() == null ? 1 : request.getEnabled())
            .sortNo(request.getSortNo() == null ? 0 : request.getSortNo())
            .createdAt(now)
            .updatedAt(now)
            .deleted(0)
            .build();
        chatToolRepository.save(persisted);
        return persisted;
    }

    /**
     * 更新工具配置。
     * @param id 主键。
     * @param request 请求参数。
     * @return 更新后的工具配置。
     */
    public ChatTool update(Long id, ChatTool request) {
        ChatTool existing = chatToolRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_TOOL_CONFIG_NOT_FOUND);
        }
        validateRequired(request);
        String normalizedToolCode = request.getToolCode().trim();
        if (chatToolRepository.existsByToolCode(normalizedToolCode, id)) {
            throw new BusinessException("CHAT_TOOL_DUPLICATE_CODE", ErrorMessageCatalog.CHAT_TOOL_DUPLICATE_CODE);
        }
        ChatTool persisted = request.toBuilder()
            .id(id)
            .toolCode(normalizedToolCode)
            .displayName(request.getDisplayName().trim())
            .sourceType(StrUtil.blankToDefault(request.getSourceType(), existing.getSourceType()))
            .enabled(request.getEnabled() == null ? existing.getEnabled() : request.getEnabled())
            .sortNo(request.getSortNo() == null ? existing.getSortNo() : request.getSortNo())
            .createdAt(existing.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .deleted(existing.getDeleted())
            .build();
        chatToolRepository.save(persisted);
        return persisted;
    }

    /**
     * 删除工具配置。
     * @param id 主键。
     */
    public void delete(Long id) {
        ChatTool existing = chatToolRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_TOOL_CONFIG_NOT_FOUND);
        }
        chatToolRepository.softDeleteById(id);
    }

    /**
     * 返回管理端工具可见性视图，包含配置态与执行器接入态。
     * @return 工具健康视图列表。
     */
    public List<ToolHealthView> listToolHealthViews() {
        Map<String, ChatToolExecutor> executorMap = chatToolRegistry.allToolCodes().stream()
            .collect(Collectors.toMap(Function.identity(), chatToolRegistry::require, (left, right) -> left));
        return chatToolRepository.findAll().stream()
            .sorted(Comparator.comparing(ChatTool::getSortNo, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChatTool::getToolCode, Comparator.nullsLast(String::compareToIgnoreCase)))
            .map(tool -> toHealthView(tool, executorMap.get(normalizeToolCode(tool.getToolCode()))))
            .toList();
    }

    /**
     * 对指定工具执行一次探测，返回探测状态。
     * @param toolCode 工具编码。
     * @return 探测结果。
     */
    public ToolHealthView pingTool(String toolCode) {
        ChatTool configuredTool = requireConfiguredTool(toolCode);
        if (!isEnabled(configuredTool)) {
            return toHealthView(configuredTool, null).toBuilder()
                .ok(false)
                .status("degraded")
                .statusLabel("禁用")
                .message(configuredTool.getToolCode() + " 已禁用，未执行探测")
                .checkedAt(DATE_TIME_FORMATTER.format(LocalDateTime.now()))
                .durationMs(0L)
                .build();
        }
        long startedAt = System.currentTimeMillis();
        String normalizedCode = normalizeToolCode(configuredTool.getToolCode());
        try {
            ChatToolExecutionResult result = chatToolExecutionService.execute(normalizedCode, sampleQuestionFor(normalizedCode));
            long durationMs = Math.max(1, System.currentTimeMillis() - startedAt);
            String content = StrUtil.blankToDefault(result.content(), "");
            String message = StrUtil.isBlank(content)
                ? normalizedCode + " 调用成功"
                : StrUtil.maxLength(content, 120);
            return toHealthView(configuredTool, chatToolRegistry.require(normalizedCode)).toBuilder()
                .ok(true)
                .status("healthy")
                .statusLabel("可用")
                .message(message)
                .checkedAt(DATE_TIME_FORMATTER.format(LocalDateTime.now()))
                .durationMs(durationMs)
                .build();
        } catch (Exception exception) {
            long durationMs = Math.max(1, System.currentTimeMillis() - startedAt);
            return toHealthView(configuredTool, null).toBuilder()
                .ok(false)
                .status("failed")
                .statusLabel("异常")
                .message(StrUtil.blankToDefault(exception.getMessage(), configuredTool.getToolCode() + " 探测失败"))
                .checkedAt(DATE_TIME_FORMATTER.format(LocalDateTime.now()))
                .durationMs(durationMs)
                .build();
        }
    }

    /**
     * 调用指定工具并返回执行结果。
     * @param toolCode 工具编码。
     * @param question 调用参数。
     * @return 执行结果。
     */
    public ToolInvokeView invokeTool(String toolCode, String question) {
        ChatTool configuredTool = requireConfiguredTool(toolCode);
        if (!isEnabled(configuredTool)) {
            throw new BusinessException("CHAT_TOOL_DISABLED", ErrorMessageCatalog.CHAT_TOOL_DISABLED);
        }
        long startedAt = System.currentTimeMillis();
        String normalizedCode = normalizeToolCode(configuredTool.getToolCode());
        ChatToolExecutionResult result = chatToolExecutionService.execute(normalizedCode, StrUtil.blankToDefault(question, sampleQuestionFor(normalizedCode)));
        long durationMs = Math.max(1, System.currentTimeMillis() - startedAt);
        return ToolInvokeView.builder()
            .toolCode(configuredTool.getToolCode())
            .displayName(configuredTool.getDisplayName())
            .ok(true)
            .status("success")
            .statusLabel("成功")
            .message("工具调用成功")
            .requestQuestion(StrUtil.blankToDefault(question, sampleQuestionFor(normalizedCode)))
            .content(StrUtil.blankToDefault(result.content(), ""))
            .metadata(result.metadata() == null ? Map.of() : result.metadata())
            .durationMs(durationMs)
            .checkedAt(DATE_TIME_FORMATTER.format(LocalDateTime.now()))
            .build();
    }

    /**
     * 返回工具默认探测样例问题。
     * @param toolCode 工具编码。
     * @return 样例问题。
     */
    public String sampleQuestionFor(String toolCode) {
        return switch (normalizeToolCode(toolCode)) {
            case "shell_command" -> "command=date";
            case "exec_command" -> "command=echo hello";
            case "write_stdin" -> "sessionId=cmd-xxx text=hello";
            case "apply_patch" -> "patch=*** Begin Patch\\n*** End Patch";
            case "list_mcp_resources" -> "请列出资源";
            case "list_mcp_resource_templates" -> "请列出模板";
            case "read_mcp_resource" -> "uri=tool://configs";
            case "update_plan" -> "{\"planId\":\"default\",\"steps\":[{\"step\":\"确认需求\",\"status\":\"in_progress\"}]}";
            case "request_user_input" -> "{\"question\":\"请选择发布策略\",\"options\":[\"渐进发布\",\"全量发布\",\"暂停发布\"]}";
            case "view_image" -> "path=D:\\\\code\\\\CodingX\\\\logs\\\\sample.png";
            case "spawn_agent" -> "请分析当前任务拆分";
            case "send_input", "send_message" -> "agentId=agent-xxx message=继续执行";
            case "wait_agent" -> "agentId=agent-xxx";
            case "close_agent" -> "agentId=agent-xxx";
            case "resume_agent" -> "agentId=agent-xxx";
            case "tool_search" -> "keyword=agent";
            case "request_plugin_install" -> "plugin=browser-use";
            case "request_permissions" -> "command=git pull";
            case "get_goal" -> "goalId=default";
            case "create_goal" -> "{\"goalId\":\"default\",\"title\":\"上线准备\",\"description\":\"完成工具联调\"}";
            case "update_goal" -> "{\"goalId\":\"default\",\"status\":\"completed\"}";
            case "followup_task" -> "{\"agentId\":\"agent-xxx\",\"task\":\"补充接口测试\"}";
            case "list_agents" -> "列出代理";
            case "spawn_agents_on_csv" -> "csvPath=D:\\\\code\\\\CodingX\\\\logs\\\\agents.csv";
            case "report_agent_job_result" -> "{\"jobId\":\"job-1\",\"status\":\"completed\",\"summary\":\"全部成功\"}";
            case "test_sync_tool" -> "ping";
            default -> "请返回当前工具状态";
        };
    }

    private void validateRequired(ChatTool request) {
        if (request == null) {
            throw new BusinessException("CHAT_TOOL_INVALID", ErrorMessageCatalog.CHAT_TOOL_CONFIG_REQUIRED);
        }
        if (StrUtil.isBlank(request.getToolCode())) {
            throw new BusinessException("CHAT_TOOL_INVALID", ErrorMessageCatalog.CHAT_TOOL_CODE_REQUIRED);
        }
        if (StrUtil.isBlank(request.getDisplayName())) {
            throw new BusinessException("CHAT_TOOL_INVALID", ErrorMessageCatalog.CHAT_TOOL_NAME_REQUIRED);
        }
    }

    /**
     * 读取并校验工具配置是否存在。
     * @param toolCode 工具编码。
     * @return 工具配置。
     */
    private ChatTool requireConfiguredTool(String toolCode) {
        ChatTool tool = chatToolRepository.findByToolCode(StrUtil.trimToEmpty(toolCode));
        if (tool == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_TOOL_CONFIG_NOT_FOUND);
        }
        return tool;
    }

    /**
     * 将配置映射为健康视图。
     * @param tool 配置对象。
     * @param executor 执行器。
     * @return 健康视图。
     */
    private ToolHealthView toHealthView(ChatTool tool, ChatToolExecutor executor) {
        boolean enabled = isEnabled(tool);
        boolean hasExecutor = executor != null;
        String status;
        String statusLabel;
        String message;
        if (!enabled) {
            status = "degraded";
            statusLabel = "禁用";
            message = tool.getToolCode() + " 已禁用";
        } else if (hasExecutor) {
            status = "healthy";
            statusLabel = "可用";
            message = tool.getToolCode() + " 已接入内置执行器";
        } else {
            status = "failed";
            statusLabel = "未接入";
            message = tool.getToolCode() + " 缺少执行器";
        }
        return ToolHealthView.builder()
            .toolCode(tool.getToolCode())
            .displayName(tool.getDisplayName())
            .category(tool.getCategory())
            .source(tool.getSourceType())
            .description(tool.getDescription())
            .sampleQuestion(sampleQuestionFor(tool.getToolCode()))
            .ok(enabled && hasExecutor)
            .status(status)
            .statusLabel(statusLabel)
            .message(message)
            .checkedAt(DATE_TIME_FORMATTER.format(LocalDateTime.now()))
            .durationMs(0L)
            .build();
    }

    private boolean isEnabled(ChatTool tool) {
        return tool != null && tool.getEnabled() != null && tool.getEnabled() == 1;
    }

    private String normalizeToolCode(String toolCode) {
        return StrUtil.trimToEmpty(toolCode).toLowerCase();
    }

    /**
     * 工具健康视图。
     *
     * @param toolCode 工具编码。
     * @param displayName 展示名。
     * @param category 分类。
     * @param source 来源。
     * @param status 状态码。
     * @param statusLabel 状态文案。
     * @param ok 是否可用。
     * @param message 状态说明。
     * @param description 描述。
     * @param sampleQuestion 样例问题。
     * @param checkedAt 探测时间。
     * @param durationMs 探测耗时。
     */
    @Builder(toBuilder = true)
    public record ToolHealthView(
        String toolCode,
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

    /**
     * 工具调用结果视图。
     *
     * @param toolCode 工具编码。
     * @param displayName 工具名称。
     * @param ok 是否成功。
     * @param status 状态码。
     * @param statusLabel 状态文案。
     * @param message 状态说明。
     * @param requestQuestion 请求参数。
     * @param content 返回内容。
     * @param metadata 附加元数据。
     * @param checkedAt 调用时间。
     * @param durationMs 耗时。
     */
    @Builder
    public record ToolInvokeView(
        String toolCode,
        String displayName,
        boolean ok,
        String status,
        String statusLabel,
        String message,
        String requestQuestion,
        String content,
        Map<String, Object> metadata,
        String checkedAt,
        Long durationMs
    ) {
    }
}

