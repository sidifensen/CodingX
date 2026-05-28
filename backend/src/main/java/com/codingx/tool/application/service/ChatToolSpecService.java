package com.codingx.tool.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.tool.domain.model.ChatTool;
import com.codingx.tool.domain.repository.ChatToolRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 将后端真实可执行工具转换为模型可见 schema。
 * 关键约束：数据库配置只决定展示与启用态，最终是否暴露必须以 Java 执行器注册表为准。
 */
@Service
@RequiredArgsConstructor
public class ChatToolSpecService {

    private static final List<String> MODEL_VISIBLE_LOCAL_TOOLS = List.of(
        "shell_command",
        "exec_command",
        "write_stdin",
        "apply_patch",
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

    private final ChatToolRepository chatToolRepository;
    private final ChatToolRegistry chatToolRegistry;

    /**
     * 列出当前可暴露给模型的本地工具 schema。
     * @return 工具 schema 列表。
     */
    public List<ChatToolSpec> listModelVisibleToolSpecs() {
        return chatToolRepository.findAll().stream()
            .filter(this::isEnabled)
            .filter(tool -> MODEL_VISIBLE_LOCAL_TOOLS.contains(normalizeToolCode(tool.getToolCode())))
            .filter(tool -> chatToolRegistry.hasExecutor(tool.getToolCode()))
            .sorted(Comparator.comparing(ChatTool::getSortNo, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChatTool::getToolCode, Comparator.nullsLast(String::compareToIgnoreCase)))
            .map(this::toSpec)
            .toList();
    }

    /**
     * 将工具配置转换为 OpenAI function 参数 schema。
     * @param tool 工具配置。
     * @return 模型工具定义。
     */
    private ChatToolSpec toSpec(ChatTool tool) {
        String toolCode = normalizeToolCode(tool.getToolCode());
        return new ChatToolSpec(
            toolCode,
            descriptionFor(toolCode, tool),
            parametersFor(toolCode)
        );
    }

    /**
     * 按工具编码生成参数 schema；复杂工具只保留必要字段，降低模型误用概率。
     * @param toolCode 工具编码。
     * @return JSON Schema。
     */
    private Map<String, Object> parametersFor(String toolCode) {
        return switch (toolCode) {
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
    private String descriptionFor(String toolCode, ChatTool tool) {
        String baseDescription = StrUtil.blankToDefault(tool.getDescription(), tool.getDisplayName());
        if (StrUtil.equals(toolCode, "shell_command")) {
            return baseDescription + "。" + SHELL_COMMAND_MODEL_GUIDANCE;
        }
        if (StrUtil.equals(toolCode, "exec_command")) {
            return baseDescription + "。" + EXEC_COMMAND_MODEL_GUIDANCE;
        }
        if (StrUtil.equals(toolCode, "apply_patch")) {
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
