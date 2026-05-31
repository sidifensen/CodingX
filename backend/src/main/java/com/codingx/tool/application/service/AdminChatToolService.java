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

    /**
     * 工具配置仓储，承接管理端配置的查询、保存和逻辑删除。
     */
    private final ChatToolRepository chatToolRepository;

    /**
     * 工具注册表，负责判断数据库配置是否已经接入真实执行器。
     */
    private final ChatToolRegistry chatToolRegistry;

    /**
     * 工具执行服务，负责管理端探测和手动调用工具。
     */
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
        // 步骤 1：先校验必填字段和工具编码唯一性，避免保存不可调用或重复的配置。
        validateRequired(request);
        String normalizedToolCode = request.getToolCode().trim();
        if (chatToolRepository.existsByToolCode(normalizedToolCode, null)) {
            throw new BusinessException("CHAT_TOOL_DUPLICATE_CODE", ErrorMessageCatalog.CHAT_TOOL_DUPLICATE_CODE);
        }
        // 步骤 2：补齐默认来源、启用状态、排序和审计字段后落库。
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
        // 步骤 1：先读取旧配置，缺失时返回统一不存在错误。
        ChatTool existing = chatToolRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_TOOL_CONFIG_NOT_FOUND);
        }
        // 步骤 2：校验新编码与必填项，允许当前记录复用自己的工具编码。
        validateRequired(request);
        String normalizedToolCode = request.getToolCode().trim();
        if (chatToolRepository.existsByToolCode(normalizedToolCode, id)) {
            throw new BusinessException("CHAT_TOOL_DUPLICATE_CODE", ErrorMessageCatalog.CHAT_TOOL_DUPLICATE_CODE);
        }
        // 步骤 3：空值字段沿用旧配置，避免局部更新把来源、启用状态或排序误清空。
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
        // 步骤 1：探测前先读取配置；配置不存在或禁用时不进入执行器。
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
            // 步骤 2：使用固定样例问题探测真实执行器，成功时截断返回内容作为健康说明。
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
            // 步骤 3：探测异常只影响当前工具健康状态，返回失败视图而不是中断整个管理端页面。
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
        // 步骤 1：管理端手动调用仍需确认工具存在且启用，避免绕过配置开关。
        ChatTool configuredTool = requireConfiguredTool(toolCode);
        if (!isEnabled(configuredTool)) {
            throw new BusinessException("CHAT_TOOL_DISABLED", ErrorMessageCatalog.CHAT_TOOL_DISABLED);
        }
        // 步骤 2：调用内容为空时回退样例问题，并记录执行耗时供管理端排障。
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
        String toolCode, // 工具编码，来自配置表。
        String displayName, // 工具展示名称。
        String category, // 工具分类，用于管理端分组。
        String source, // 工具来源类型。
        String status, // 健康状态编码，例如 healthy、degraded、failed。
        String statusLabel, // 健康状态中文文案。
        boolean ok, // 是否可用，只有启用且存在执行器时为 true。
        String message, // 管理端展示的健康说明或失败原因。
        String description, // 工具能力描述。
        String sampleQuestion, // 探测该工具时使用的样例问题。
        String checkedAt, // 最近一次探测时间，格式为 yyyy-MM-dd HH:mm:ss。
        Long durationMs // 最近一次探测耗时，单位毫秒。
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
        String toolCode, // 工具编码，来自配置表。
        String displayName, // 工具展示名称。
        boolean ok, // 本次调用是否成功。
        String status, // 调用状态编码，成功时为 success。
        String statusLabel, // 调用状态中文文案。
        String message, // 管理端展示的调用说明。
        String requestQuestion, // 实际传给执行器的调用参数。
        String content, // 执行器返回的文本内容。
        Map<String, Object> metadata, // 执行器返回的结构化附加信息，缺省为空 Map。
        String checkedAt, // 调用完成时间，格式为 yyyy-MM-dd HH:mm:ss。
        Long durationMs // 本次调用耗时，单位毫秒。
    ) {
    }
}

