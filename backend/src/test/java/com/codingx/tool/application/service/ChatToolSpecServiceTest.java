package com.codingx.tool.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.mcp.application.service.McpServerRuntimeService;
import com.codingx.tool.domain.model.ChatTool;
import com.codingx.tool.domain.repository.ChatToolRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 验证模型可见工具定义只能来自真实可执行且已启用的 Java 工具。
 */
class ChatToolSpecServiceTest {

    /**
     * schema 生成应过滤掉禁用工具和缺执行器工具，避免模型看到不可调用能力。
     */
    @Test
    void listModelVisibleSpecsOnlyIncludesEnabledRegisteredExecutors() {
        ChatToolRepository repository = new InMemoryChatToolRepository(List.of(
            ChatTool.builder()
                .toolCode("read")
                .displayName("读取文件")
                .description("读取文件内容")
                .enabled(1)
                .sortNo(1)
                .deleted(0)
                .build(),
            ChatTool.builder()
                .toolCode("bash")
                .displayName("执行命令")
                .description("执行命令")
                .enabled(1)
                .sortNo(2)
                .deleted(0)
                .build(),
            ChatTool.builder()
                .toolCode("spawn_agent")
                .displayName("创建子代理")
                .description("暂未适配真实 Java 子代理运行时")
                .enabled(0)
                .sortNo(3)
                .deleted(0)
                .build(),
            ChatTool.builder()
                .toolCode("fake_tool")
                .displayName("假工具")
                .description("数据库中存在但没有执行器")
                .enabled(1)
                .sortNo(4)
                .deleted(0)
                .build()
        ));
        ChatToolExecutor executor = new ChatToolExecutor() {
            @Override
            public List<String> toolCodes() {
                return List.of("read", "bash", "spawn_agent");
            }

            @Override
            public ChatToolExecutionResult execute(String toolCode, String question) {
                return new ChatToolExecutionResult(toolCode, "ok", Map.of());
            }
        };
        ChatToolRegistry registry = new ChatToolRegistry(List.of(executor));
        registry.init();
        ChatToolSpecService service = new ChatToolSpecService(repository, registry, new LocalToolAliasService());

        List<ChatToolSpec> specs = service.listModelVisibleToolSpecs();
        List<String> names = specs.stream().map(ChatToolSpec::name).toList();

        assertTrue(names.contains("read"));
        assertTrue(names.contains("bash"));
        assertFalse(names.contains("ReadFile"));
        assertFalse(names.contains("Bash"));
        assertFalse(names.contains("spawn_agent"));
        assertFalse(names.contains("fake_tool"));
        ChatToolSpec readSpec = specs.getFirst();
        assertTrue(readSpec.description().contains("读取文件"));
        assertTrue(readSpec.parameters().containsKey("properties"));
        assertFalse(readSpec.parameters().isEmpty());
    }

    /**
     * OpenClaw 风格短工具名应直接暴露给模型，避免模型还要学习 shell_command/apply_patch 等内部实现名。
     */
    @Test
    void listModelVisibleSpecsIncludesOpenClawStyleShortToolNames() {
        List<String> shortToolCodes = List.of("read", "write", "edit", "bash", "grep", "find", "ls");
        ChatToolRepository repository = new InMemoryChatToolRepository(shortToolCodes.stream()
            .map(toolCode -> ChatTool.builder()
                .toolCode(toolCode)
                .displayName(toolCode)
                .description(toolCode + " 工具")
                .enabled(1)
                .sortNo(shortToolCodes.indexOf(toolCode) + 1)
                .deleted(0)
                .build())
            .toList());
        ChatToolExecutor executor = new ChatToolExecutor() {
            @Override
            public List<String> toolCodes() {
                return shortToolCodes;
            }

            @Override
            public ChatToolExecutionResult execute(String toolCode, String question) {
                return new ChatToolExecutionResult(toolCode, "ok", Map.of());
            }
        };
        ChatToolRegistry registry = new ChatToolRegistry(List.of(executor));
        registry.init();
        ChatToolSpecService service = new ChatToolSpecService(repository, registry, new LocalToolAliasService());

        List<ChatToolSpec> specs = service.listModelVisibleToolSpecs();
        List<String> names = specs.stream().map(ChatToolSpec::name).toList();

        assertTrue(names.containsAll(shortToolCodes));
        for (ChatToolSpec spec : specs.stream().filter(spec -> shortToolCodes.contains(spec.name())).toList()) {
            assertTrue(spec.parameters().containsKey("properties"));
            assertTrue(String.valueOf(spec.description()).contains(spec.name()));
        }
    }

    /**
     * 模型可见工具只保留短工具名，Claude Code 风格别名只作为执行兼容入口存在。
     * 业务约束：同一能力不能同时以 read 和 ReadFile 暴露给模型，避免模型混用两个入口造成重复调用。
     */
    @Test
    void listModelVisibleSpecsShouldNotExposeClaudeCodeAliasToolNames() {
        List<String> shortToolCodes = List.of("read", "write", "edit", "bash", "grep", "find", "ls");
        ChatToolRepository repository = new InMemoryChatToolRepository(shortToolCodes.stream()
            .map(toolCode -> ChatTool.builder()
                .toolCode(toolCode)
                .displayName(toolCode)
                .description(toolCode + " 工具")
                .enabled(1)
                .sortNo(shortToolCodes.indexOf(toolCode) + 1)
                .deleted(0)
                .build())
            .toList());
        ChatToolExecutor executor = new ChatToolExecutor() {
            @Override
            public List<String> toolCodes() {
                return shortToolCodes;
            }

            @Override
            public ChatToolExecutionResult execute(String toolCode, String question) {
                return new ChatToolExecutionResult(toolCode, "ok", Map.of());
            }
        };
        ChatToolRegistry registry = new ChatToolRegistry(List.of(executor));
        registry.init();
        ChatToolSpecService service = new ChatToolSpecService(repository, registry, new LocalToolAliasService());

        List<String> names = service.listModelVisibleToolSpecs().stream()
            .map(ChatToolSpec::name)
            .toList();

        assertTrue(names.containsAll(shortToolCodes));
        assertFalse(names.contains("ReadFile"));
        assertFalse(names.contains("WriteFile"));
        assertFalse(names.contains("EditFile"));
        assertFalse(names.contains("Bash"));
        assertFalse(names.contains("Glob"));
        assertFalse(names.contains("Grep"));
    }

    /**
     * 已发现的外部 MCP 工具应合并进模型工具清单，保证模型能直接发起 mcp__server__tool 调用。
     */
    @Test
    void listModelVisibleSpecsIncludesDiscoveredExternalMcpTools() {
        ChatToolRepository repository = new InMemoryChatToolRepository(List.of());
        ChatToolRegistry registry = new ChatToolRegistry(List.of());
        registry.init();
        McpServerRuntimeService mcpServerRuntimeService = Mockito.mock(McpServerRuntimeService.class);
        Mockito.when(mcpServerRuntimeService.listDiscoveredToolSpecs()).thenReturn(List.of(
            new ChatToolSpec(
                "mcp__github__search",
                "Search repositories",
                Map.of("type", "object", "properties", Map.of()),
                "mcp__github__search"
            )
        ));
        ChatToolSpecService service = new ChatToolSpecService(
            repository,
            registry,
            new LocalToolAliasService(),
            mcpServerRuntimeService
        );

        List<ChatToolSpec> specs = service.listModelVisibleToolSpecs();

        assertEquals(List.of("mcp__github__search"), specs.stream().map(ChatToolSpec::name).toList());
    }

    /**
     * Windows 本地运行时会通过 PowerShell 执行命令，schema 必须把这一点暴露给模型，
     * 否则模型容易生成 mkdir -p、cat <<EOF 等 Bash 写法导致工具调用反复失败。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shellCommandSpecShouldTellModelToUsePowerShellSyntax() {
        ChatToolRepository repository = new InMemoryChatToolRepository(List.of(
            ChatTool.builder()
                .toolCode("bash")
                .displayName("命令执行")
                .description("在当前工作区执行终端命令")
                .enabled(1)
                .sortNo(1)
                .deleted(0)
                .build()
        ));
        ChatToolExecutor executor = new ChatToolExecutor() {
            @Override
            public List<String> toolCodes() {
                return List.of("bash");
            }

            @Override
            public ChatToolExecutionResult execute(String toolCode, String question) {
                return new ChatToolExecutionResult(toolCode, "ok", Map.of());
            }
        };
        ChatToolRegistry registry = new ChatToolRegistry(List.of(executor));
        registry.init();
        ChatToolSpecService service = new ChatToolSpecService(repository, registry, new LocalToolAliasService());

        ChatToolSpec bashSpec = service.listModelVisibleToolSpecs().getFirst();
        Map<String, Object> properties = (Map<String, Object>) bashSpec.parameters().get("properties");
        Map<String, Object> commandSchema = (Map<String, Object>) properties.get("command");
        String commandDescription = String.valueOf(commandSchema.get("description"));

        assertTrue(bashSpec.description().contains("Windows PowerShell"));
        assertTrue(commandDescription.contains("Windows PowerShell"));
        assertTrue(commandDescription.contains("mkdir -p"));
        assertTrue(commandDescription.contains("Set-Content"));
        assertTrue(commandDescription.contains("apply_patch"));
        assertTrue(commandDescription.contains("不要使用 shell_command 创建或编辑多行 HTML/XML/代码文件"));
        assertTrue(commandDescription.contains("< 是 PowerShell 保留字符"));
    }

    /**
     * write 的模型说明必须强调当前工作目录路径边界，避免模型继续编造 C:\workspace 这类虚拟根路径。
     */
    @Test
    @SuppressWarnings("unchecked")
    void writeSpecShouldTellModelToUseWorkspaceRelativePaths() {
        ChatToolRepository repository = new InMemoryChatToolRepository(List.of(
            ChatTool.builder()
                .toolCode("write")
                .displayName("写文件")
                .description("在当前工作区写入文件")
                .enabled(1)
                .sortNo(1)
                .deleted(0)
                .build()
        ));
        ChatToolExecutor executor = new ChatToolExecutor() {
            @Override
            public List<String> toolCodes() {
                return List.of("write");
            }

            @Override
            public ChatToolExecutionResult execute(String toolCode, String question) {
                return new ChatToolExecutionResult(toolCode, "ok", Map.of());
            }
        };
        ChatToolRegistry registry = new ChatToolRegistry(List.of(executor));
        registry.init();
        ChatToolSpecService service = new ChatToolSpecService(repository, registry, new LocalToolAliasService());

        ChatToolSpec writeSpec = service.listModelVisibleToolSpecs().getFirst();
        Map<String, Object> properties = (Map<String, Object>) writeSpec.parameters().get("properties");
        Map<String, Object> pathSchema = (Map<String, Object>) properties.get("path");
        String pathDescription = String.valueOf(pathSchema.get("description"));

        assertTrue(writeSpec.description().contains("当前工具工作目录"));
        assertTrue(pathDescription.contains("相对路径"));
        assertTrue(pathDescription.contains("工作区外"));
    }

    /**
     * 真实目标模式工具必须暴露给模型，否则模型只能看到系统提示却无法调用目标持久化链路。
     */
    @Test
    @SuppressWarnings("unchecked")
    void modelVisibleSpecsShouldIncludeRealGoalTools() {
        List<String> goalToolCodes = List.of("get_goal", "create_goal", "update_goal");
        ChatToolRepository repository = new InMemoryChatToolRepository(goalToolCodes.stream()
            .map(toolCode -> ChatTool.builder()
                .toolCode(toolCode)
                .displayName(toolCode)
                .description(toolCode + " 工具")
                .enabled(1)
                .sortNo(goalToolCodes.indexOf(toolCode) + 1)
                .deleted(0)
                .build())
            .toList());
        ChatToolExecutor executor = new ChatToolExecutor() {
            @Override
            public List<String> toolCodes() {
                return goalToolCodes;
            }

            @Override
            public ChatToolExecutionResult execute(String toolCode, String question) {
                return new ChatToolExecutionResult(toolCode, "ok", Map.of());
            }
        };
        ChatToolRegistry registry = new ChatToolRegistry(List.of(executor));
        registry.init();
        ChatToolSpecService service = new ChatToolSpecService(repository, registry, new LocalToolAliasService());

        List<ChatToolSpec> specs = service.listModelVisibleToolSpecs();
        List<String> names = specs.stream().map(ChatToolSpec::name).toList();
        ChatToolSpec createGoalSpec = specs.stream()
            .filter(spec -> spec.name().equals("create_goal"))
            .findFirst()
            .orElseThrow();
        Map<String, Object> properties = (Map<String, Object>) createGoalSpec.parameters().get("properties");

        assertTrue(names.containsAll(goalToolCodes));
        assertTrue(properties.containsKey("title"));
        assertTrue(properties.containsKey("steps"));
    }

    /**
     * 测试用内存仓储只实现 schema 服务所需的读取方法。
     */
    private record InMemoryChatToolRepository(List<ChatTool> tools) implements ChatToolRepository {

        @Override
        public List<ChatTool> findAll() {
            return tools;
        }

        @Override
        public ChatTool findById(Long id) {
            return null;
        }

        @Override
        public ChatTool findByToolCode(String toolCode) {
            return tools.stream()
                .filter(tool -> tool.getToolCode().equals(toolCode))
                .findFirst()
                .orElse(null);
        }

        @Override
        public boolean existsByToolCode(String toolCode, Long excludedId) {
            return false;
        }

        @Override
        public void save(ChatTool chatTool) {
        }

        @Override
        public void softDeleteById(Long id) {
        }
    }
}
