package com.codingx.tool.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.mcp.application.service.McpServerRuntimeService;
import com.codingx.tool.domain.model.ChatTool;
import com.codingx.tool.domain.repository.ChatToolRepository;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 将后端真实可执行工具转换为模型可见 schema。
 * 关键约束：数据库配置只决定展示与启用态，最终是否暴露必须以 Java 执行器注册表为准。
 */
@Service
public class ChatToolSpecService {

    private static final List<String> MODEL_VISIBLE_LOCAL_TOOLS = List.of(
        "read",
        "write",
        "edit",
        "bash",
        "grep",
        "find",
        "ls",
        "update_plan",
        "view_image",
        "tool_search",
        "test_sync_tool"
    );
    private static final String SHELL_COMMAND_MODEL_GUIDANCE = """
        当前后端按服务端操作系统选择命令解释器；Windows 环境使用 Windows PowerShell（powershell -NoProfile -Command），不是 Bash。请使用 PowerShell 语法，避免 mkdir -p、cat <<EOF、heredoc、&& 串联和 < 输入重定向等 Bash 写法；< 是 PowerShell 保留字符，未正确引用会直接触发语法错误。创建目录用 New-Item -ItemType Directory -Force -Path <path>；不要使用 shell_command 创建或编辑多行 HTML/XML/代码文件，请改用 apply_patch，必要时才用 Set-Content -Encoding UTF8 写入 PowerShell here-string。
        """.trim();
    private static final String EXEC_COMMAND_MODEL_GUIDANCE = """
        后台命令使用与 shell_command 相同的命令解释器；Windows 环境是 Windows PowerShell，请避免 Bash 专属语法，交互输入通过 write_stdin 写入。
        """.trim();
    private static final String APPLY_PATCH_MODEL_GUIDANCE = """
        补丁路径必须基于当前工具工作目录；优先使用相对路径，例如 diary/index.html。不要编造 C:\\workspace 等虚拟根目录；如果已经从工具结果拿到工作目录内绝对路径，也必须确认它属于当前工作目录。
        """.trim();
    private static final String WORKSPACE_PATH_GUIDANCE = """
        路径必须位于当前工具工作目录内；优先使用相对路径，不要访问 .. 或工作区外绝对路径。
        """.trim();

    /** 工具配置仓储，用于读取数据库中启用的工具展示和排序配置。 */
    private final ChatToolRepository chatToolRepository;
    /** 工具执行器注册表，用于确认配置工具在后端确实存在执行实现。 */
    private final ChatToolRegistry chatToolRegistry;
    /** 本地工具别名服务，用于追加 Claude Code 风格模型可见工具名。 */
    private final LocalToolAliasService localToolAliasService;
    /** 外部 MCP 运行时，用于把已发现 MCP 工具合并进模型可见清单。 */
    private final McpServerRuntimeService mcpServerRuntimeService;

    /**
     * Spring 生产构造器，合并本地工具和外部 MCP 动态工具。
     * @param chatToolRepository 工具配置仓储。
     * @param chatToolRegistry 工具执行器注册表。
     * @param localToolAliasService 本地工具别名服务。
     * @param mcpServerRuntimeService 外部 MCP 运行时。
     */
    @Autowired
    public ChatToolSpecService(
        ChatToolRepository chatToolRepository,
        ChatToolRegistry chatToolRegistry,
        LocalToolAliasService localToolAliasService,
        McpServerRuntimeService mcpServerRuntimeService
    ) {
        this.chatToolRepository = chatToolRepository;
        this.chatToolRegistry = chatToolRegistry;
        this.localToolAliasService = localToolAliasService;
        this.mcpServerRuntimeService = mcpServerRuntimeService;
    }

    /**
     * 兼容旧测试构造器，未注入外部 MCP 时仅返回本地工具。
     * @param chatToolRepository 工具配置仓储。
     * @param chatToolRegistry 工具执行器注册表。
     * @param localToolAliasService 本地工具别名服务。
     */
    public ChatToolSpecService(
        ChatToolRepository chatToolRepository,
        ChatToolRegistry chatToolRegistry,
        LocalToolAliasService localToolAliasService
    ) {
        this(chatToolRepository, chatToolRegistry, localToolAliasService, null);
    }

    /**
     * 列出当前可暴露给模型的本地工具 schema。
     * @return 工具 schema 列表。
     */
    public List<ChatToolSpec> listModelVisibleToolSpecs() {
        List<ChatTool> enabledTools = chatToolRepository.findAll().stream()
            .filter(this::isEnabled)
            .filter(tool -> MODEL_VISIBLE_LOCAL_TOOLS.contains(normalizeToolCode(tool.getToolCode())))
            .filter(tool -> chatToolRegistry.hasExecutor(tool.getToolCode()))
            .sorted(Comparator.comparing(ChatTool::getSortNo, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChatTool::getToolCode, Comparator.nullsLast(String::compareToIgnoreCase)))
            .toList();
        List<ChatToolSpec> specs = new ArrayList<>(enabledTools.stream()
            .map(tool -> toSpec(normalizeToolCode(tool.getToolCode()), normalizeToolCode(tool.getToolCode()), tool))
            .toList());
        Map<String, ChatTool> toolByCanonicalCode = enabledTools.stream()
            .collect(Collectors.toMap(
                tool -> normalizeToolCode(tool.getToolCode()),
                Function.identity(),
                (first, ignored) -> first,
                LinkedHashMap::new
            ));
        for (Map.Entry<String, String> aliasEntry : localToolAliasService.aliasMappings().entrySet()) {
            ChatTool canonicalTool = toolByCanonicalCode.get(aliasEntry.getValue());
            if (canonicalTool == null) {
                continue;
            }
            specs.add(toSpec(aliasEntry.getKey(), aliasEntry.getValue(), canonicalTool));
        }
        if (mcpServerRuntimeService != null) {
            specs.addAll(mcpServerRuntimeService.listDiscoveredToolSpecs());
        }
        return specs.stream()
            .filter(spec -> StrUtil.isNotBlank(spec.name()))
            .toList();
    }

    /**
     * 将工具配置转换为 OpenAI function 参数 schema。
     * @param tool 工具配置。
     * @return 模型工具定义。
     */
    private ChatToolSpec toSpec(String visibleToolCode, String canonicalToolCode, ChatTool tool) {
        return new ChatToolSpec(
            visibleToolCode,
            descriptionFor(visibleToolCode, canonicalToolCode, tool),
            parametersFor(canonicalToolCode),
            canonicalToolCode
        );
    }

    /**
     * 按工具编码生成参数 schema；复杂工具只保留必要字段，降低模型误用概率。
     * @param toolCode 工具编码。
     * @return JSON Schema。
     */
    private Map<String, Object> parametersFor(String toolCode) {
        return switch (toolCode) {
            case "read" -> objectSchema(
                Map.of(
                    "path", stringSchema("要读取的文件路径。" + WORKSPACE_PATH_GUIDANCE),
                    "offset", numberSchema("可选，从第几行开始读取，1 表示第一行；0 会兼容为第一行"),
                    "limit", numberSchema("可选，最多读取多少行")
                ),
                List.of("path")
            );
            case "write" -> objectSchema(
                Map.of(
                    "path", stringSchema("要写入的文件路径。" + WORKSPACE_PATH_GUIDANCE),
                    "content", stringSchema("完整文件内容；写入会覆盖目标文件，必要时自动创建父目录")
                ),
                List.of("path", "content")
            );
            case "edit" -> objectSchema(
                Map.of(
                    "path", stringSchema("要编辑的文件路径。" + WORKSPACE_PATH_GUIDANCE),
                    "old_text", stringSchema("文件中必须精确且唯一存在的原文本；兼容 oldText"),
                    "new_text", stringSchema("替换后的新文本；兼容 newText"),
                    "replace_all", Map.of("type", "boolean", "description", "是否替换所有匹配项，默认 false；兼容 replaceAll")
                ),
                List.of("path", "old_text", "new_text")
            );
            case "bash" -> objectSchema(
                Map.of(
                    "command", stringSchema("要在当前本地工作区执行的命令。" + SHELL_COMMAND_MODEL_GUIDANCE),
                    "timeout", numberSchema("可选，命令超时时间，单位秒，最大 60；兼容 timeoutMs 毫秒参数")
                ),
                List.of("command")
            );
            case "grep" -> objectSchema(
                Map.of(
                    "pattern", stringSchema("搜索模式，默认按 Java 正则匹配；literal=true 时按字面量匹配"),
                    "path", stringSchema("可选，搜索目录或文件路径。" + WORKSPACE_PATH_GUIDANCE),
                    "glob", stringSchema("可选，文件 glob 过滤，例如 *.java"),
                    "ignore_case", Map.of("type", "boolean", "description", "是否忽略大小写；兼容 ignoreCase"),
                    "literal", Map.of("type", "boolean", "description", "是否把 pattern 当作字面量"),
                    "context", numberSchema("可选，展示命中行前后的上下文行数，最大 20"),
                    "limit", numberSchema("可选，最大返回条数，默认 100，最大 500；兼容 maxResults")
                ),
                List.of("pattern")
            );
            case "find" -> objectSchema(
                Map.of(
                    "pattern", stringSchema("glob 模式，例如 *.java 或 **/*.ts"),
                    "path", stringSchema("可选，搜索目录路径。" + WORKSPACE_PATH_GUIDANCE),
                    "limit", numberSchema("可选，最大返回条数，默认 200，最大 1000；兼容 maxResults")
                ),
                List.of("pattern")
            );
            case "ls" -> objectSchema(
                Map.of(
                    "path", stringSchema("可选，要列出的目录路径，默认当前工作目录。" + WORKSPACE_PATH_GUIDANCE),
                    "limit", numberSchema("可选，最大返回条目数，默认 500，最大 1000")
                ),
                List.of()
            );
            case "shell_command" -> objectSchema(
                Map.of(
                    "command", stringSchema("要在当前本地工作区执行的命令。" + SHELL_COMMAND_MODEL_GUIDANCE),
                    "timeoutMs", numberSchema("命令超时时间，单位毫秒，最大 60000")
                ),
                List.of("command")
            );
            case "exec_command" -> objectSchema(
                Map.of("command", stringSchema("要启动的后台命令。" + EXEC_COMMAND_MODEL_GUIDANCE)),
                List.of("command")
            );
            case "write_stdin" -> objectSchema(
                Map.of(
                    "sessionId", stringSchema("exec_command 返回的命令会话 ID"),
                    "text", stringSchema("要写入标准输入的文本")
                ),
                List.of("sessionId", "text")
            );
            case "apply_patch" -> objectSchema(
                Map.of("patch", stringSchema("Codex Begin Patch 或标准 unified diff 补丁文本。" + APPLY_PATCH_MODEL_GUIDANCE)),
                List.of("patch")
            );
            // view_image 既要兼容本地调试图片，也要兼容模型直接传入的远程图片 URL。
            case "view_image" -> objectSchema(
                Map.of("path", stringSchema("本地图片绝对路径或可访问的 HTTP(S) 图片 URL")),
                List.of("path")
            );
            case "tool_search" -> objectSchema(
                Map.of("keyword", stringSchema("用于检索工具配置的关键词")),
                List.of("keyword")
            );
            case "update_plan" -> objectSchema(
                Map.of(
                    "planId", stringSchema("计划标识，缺省为 default"),
                    "steps", Map.of(
                        "type", "array",
                        "description", "计划步骤列表",
                        "items", objectSchema(
                            Map.of(
                                "step", stringSchema("步骤名称"),
                                "status", stringSchema("pending、in_progress 或 completed")
                            ),
                            List.of("step", "status")
                        )
                    )
                ),
                List.of("steps")
            );
            default -> objectSchema(Map.of("input", stringSchema("工具输入文本")), List.of());
        };
    }

    /**
     * 追加模型调用时必须知道的运行时边界；数据库描述仍用于管理端展示基础信息。
     * @param toolCode 工具编码。
     * @param tool 工具配置。
     * @return 模型可见工具说明。
     */
    private String descriptionFor(String visibleToolCode, String canonicalToolCode, ChatTool tool) {
        String baseDescription = StrUtil.blankToDefault(tool.getDescription(), tool.getDisplayName());
        if (localToolAliasService.hasAliasForCanonicalCode(canonicalToolCode)
            && !StrUtil.equals(visibleToolCode, canonicalToolCode)) {
            baseDescription = baseDescription + "；模型可按 Claude Code 风格工具名 `" + visibleToolCode
                + "` 调用，后端会归一到 `" + canonicalToolCode + "` 执行。";
        }
        if (StrUtil.equals(canonicalToolCode, "shell_command")) {
            return baseDescription + "。" + SHELL_COMMAND_MODEL_GUIDANCE;
        }
        if (StrUtil.equals(canonicalToolCode, "bash")) {
            return baseDescription + "。" + SHELL_COMMAND_MODEL_GUIDANCE;
        }
        if (List.of("read", "write", "edit", "grep", "find", "ls").contains(canonicalToolCode)) {
            return baseDescription + "。" + WORKSPACE_PATH_GUIDANCE;
        }
        if (StrUtil.equals(canonicalToolCode, "exec_command")) {
            return baseDescription + "。" + EXEC_COMMAND_MODEL_GUIDANCE;
        }
        if (StrUtil.equals(canonicalToolCode, "apply_patch")) {
            return baseDescription + "。" + APPLY_PATCH_MODEL_GUIDANCE;
        }
        return baseDescription;
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(properties));
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    private Map<String, Object> stringEnumSchema(String description, List<String> values) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("description", description + "，可选值：" + String.join("、", values));
        schema.put("enum", values);
        return schema;
    }

    private Map<String, Object> arraySchema(String description, Map<String, Object> itemSchema) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("description", description);
        schema.put("items", new LinkedHashMap<>(itemSchema));
        return schema;
    }

    private Map<String, Object> numberSchema(String description) {
        return Map.of("type", "number", "description", description);
    }

    private boolean isEnabled(ChatTool tool) {
        return tool != null && tool.getEnabled() != null && tool.getEnabled() == 1;
    }

    private String normalizeToolCode(String toolCode) {
        return StrUtil.trimToEmpty(toolCode).toLowerCase(Locale.ROOT);
    }
}
