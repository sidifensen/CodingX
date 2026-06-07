package com.codingx.cli.backend;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.StreamingAgentEventSource;
import com.codingx.cli.config.CliConfig;
import com.codingx.cli.config.CliConfigStore;
import com.codingx.cli.slash.CliSlashCommand;
import com.codingx.cli.slash.SlashCommandCatalog;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 调用现有后端 `/api/chat/stream` 的 CLI 事件源，把 SSE 流转换为 TUI 可消费的 `AgentEvent`。
 */
public class BackendChatEventSource implements StreamingAgentEventSource {

    /**
     * 后端 Long 类型会话参数只接受数值字符串，非数值本地快照不能透传。
     */
    private static final Pattern NUMERIC_SESSION_ID = Pattern.compile("\\d+");

    /**
     * 用户级 CLI 配置存储，负责读取 serverUrl/token 并写回最近会话。
     */
    private final CliConfigStore configStore;

    /**
     * JDK HTTP 客户端，用于保持 CLI 轻量并支持输入流式消费。
     */
    private final HttpClient httpClient;

    /**
     * SSE 事件解析器，按空行将后端流拆成事件块。
     */
    private final SseEventParser sseEventParser;

    /**
     * 后端治理中心 Slash Command 目录，用于把 CLI 输入的 `/review` 等命令转换为 Web 同款结构化消息。
     */
    private final SlashCommandCatalog slashCommandCatalog;

    /**
     * @param configStore 用户级 CLI 配置存储。
     */
    public BackendChatEventSource(CliConfigStore configStore) {
        this(
            configStore,
            SlashCommandCatalog.EMPTY
        );
    }

    /**
     * @param configStore 用户级 CLI 配置存储。
     * @param slashCommandCatalog 后端 Slash Command 目录。
     */
    public BackendChatEventSource(CliConfigStore configStore, SlashCommandCatalog slashCommandCatalog) {
        this(
            configStore,
            slashCommandCatalog,
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build(),
            new SseEventParser()
        );
    }

    /**
     * 测试可注入构造器，避免生产实现和测试服务强耦合。
     */
    BackendChatEventSource(CliConfigStore configStore, HttpClient httpClient, SseEventParser sseEventParser) {
        this(configStore, SlashCommandCatalog.EMPTY, httpClient, sseEventParser);
    }

    /**
     * 测试可注入构造器，允许单测同时替换目录、HTTP 客户端和 SSE 解析器。
     */
    BackendChatEventSource(
        CliConfigStore configStore,
        SlashCommandCatalog slashCommandCatalog,
        HttpClient httpClient,
        SseEventParser sseEventParser
    ) {
        this.configStore = configStore;
        this.httpClient = httpClient;
        this.sseEventParser = sseEventParser;
        this.slashCommandCatalog = slashCommandCatalog == null ? SlashCommandCatalog.EMPTY : slashCommandCatalog;
    }

    @Override
    public void startTurn(String task, Path workspace, Consumer<AgentEvent> eventConsumer) {
        startTurn(task, workspace, false, eventConsumer);
    }

    @Override
    public void startTurn(String task, Path workspace, boolean planMode, Consumer<AgentEvent> eventConsumer) {
        CliConfig config = configStore.load();
        String normalizedTask = StrUtil.trimToEmpty(task);
        String normalizedWorkspace = workspace.toAbsolutePath().normalize().toString();
        BackendChatEventMapper mapper = new BackendChatEventMapper(
            config.lastSessionId(),
            "cli-" + UUID.randomUUID()
        );
        AtomicReference<String> knownConversationId = new AtomicReference<>(config.lastSessionId());
        eventConsumer.accept(mapper.turnStarted(normalizedTask, normalizedWorkspace));
        if (StrUtil.isBlank(config.token())) {
            // 步骤：CLI 本机没有 token 时不请求聊天流，直接提示登录入口，避免用户看到泛化的网络失败。
            eventConsumer.accept(mapper.error("未登录或登录已失效，请先运行 /login，或执行 codingx auth login。"));
            return;
        }

        try {
            HttpRequest request = buildRequest(config, normalizedTask, normalizedWorkspace, planMode);
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                eventConsumer.accept(mapper.error(resolveErrorMessage(response.body())));
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                sseEventParser.parse(reader, sseEvent -> {
                    knownConversationId.set(persistConversationIdIfPresent(
                        knownConversationId.get(),
                        mapper.conversationId(sseEvent)
                    ));
                    for (AgentEvent event : mapper.map(sseEvent)) {
                        eventConsumer.accept(event);
                    }
                });
            }
        } catch (Exception exception) {
            eventConsumer.accept(mapper.error("聊天流请求失败：" + exception.getMessage()));
        }
    }

    /**
     * 构造后端聊天流请求，参数与 Web 端 `buildStreamRequestUrl` 保持同名。
     */
    private HttpRequest buildRequest(CliConfig config, String task, String workspace, boolean planMode) {
        StructuredSlashCommandRequest structuredRequest = parseStructuredSlashCommand(task);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("question", structuredRequest.question());
        if (isNumericSessionId(config.lastSessionId())) {
            query.put("conversationId", config.lastSessionId().trim());
        }
        query.put("runtimeTarget", "local");
        query.put("repositoryPath", workspace);
        query.put("planMode", String.valueOf(planMode));
        if (StrUtil.isNotBlank(structuredRequest.messagesJson())) {
            query.put("messages", structuredRequest.messagesJson());
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(buildUri(config.serverUrl(), query))
            .timeout(Duration.ofMinutes(10))
            .GET();
        if (StrUtil.isNotBlank(config.token())) {
            builder.header("satoken", config.token().trim());
        }
        builder.header("Accept", "text/event-stream, application/json");
        return builder.build();
    }

    /**
     * 拼装 stream URI；serverUrl 可带尾部斜杠，最终统一落到 `/api/chat/stream`。
     */
    private URI buildUri(String serverUrl, Map<String, String> query) {
        String normalizedServerUrl = StrUtil.removeSuffix(StrUtil.trimToEmpty(serverUrl), "/");
        StringBuilder builder = new StringBuilder(normalizedServerUrl)
            .append("/api/chat/stream?")
            .append(encodeQuery(query));
        return URI.create(builder.toString());
    }

    /**
     * 采用 UTF-8 编码查询参数，确保中文问题和 Windows 路径能安全传输。
     */
    private String encodeQuery(Map<String, String> query) {
        StringBuilder builder = new StringBuilder();
        query.forEach((key, value) -> {
            if (!builder.isEmpty()) {
                builder.append('&');
            }
            builder.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
            builder.append('=');
            builder.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return builder.toString();
    }

    /**
     * 解析 CLI 输入中的后端内置 Slash Command，生成与 Web 端 `buildStreamRequestUrl` 相同的结构化消息。
     */
    private StructuredSlashCommandRequest parseStructuredSlashCommand(String task) {
        String normalizedTask = StrUtil.trimToEmpty(task);
        String commandToken = leadingSlashToken(normalizedTask);
        if (StrUtil.isBlank(commandToken)) {
            return new StructuredSlashCommandRequest(normalizedTask, null);
        }
        CliSlashCommand command = findBuiltinSlashCommand(commandToken);
        if (command == null) {
            return new StructuredSlashCommandRequest(normalizedTask, null);
        }
        String question = stripLeadingSlashCommand(normalizedTask, command.commandCode());
        return new StructuredSlashCommandRequest(question, buildStructuredMessagesJson(command, question));
    }

    /**
     * 提取输入开头的 Slash token；只有首个 token 命中目录时才按内置命令处理。
     */
    private String leadingSlashToken(String task) {
        if (StrUtil.isBlank(task) || !task.startsWith("/")) {
            return null;
        }
        return StrUtil.removePrefix(task.split("\\s+", 2)[0], "/");
    }

    /**
     * 从治理命令目录中查找启用的 BUILTIN 命令，目录不可用时按普通文本提交。
     */
    private CliSlashCommand findBuiltinSlashCommand(String commandCode) {
        List<CliSlashCommand> commands;
        try {
            commands = slashCommandCatalog.listCommands();
        } catch (RuntimeException exception) {
            commands = List.of();
        }
        for (CliSlashCommand command : commands) {
            if (command == null || command.commandCode() == null) {
                continue;
            }
            boolean sameCommand = command.commandCode().equalsIgnoreCase(commandCode);
            boolean builtin = "BUILTIN".equalsIgnoreCase(StrUtil.blankToDefault(command.commandType(), "BUILTIN"));
            if (sameCommand && builtin) {
                return command;
            }
        }
        return null;
    }

    /**
     * 去掉用户输入开头的 `/command`，保留后续真实问题正文。
     */
    private String stripLeadingSlashCommand(String rawQuestion, String commandCode) {
        String trimmedQuestion = StrUtil.trimToEmpty(rawQuestion);
        String escapedCommand = Pattern.quote(StrUtil.removePrefix(StrUtil.trimToEmpty(commandCode), "/"));
        return trimmedQuestion
            .replaceFirst("(?i)^/" + escapedCommand + "(?:\\s+|$)", "")
            .trim();
    }

    /**
     * 构造后端结构化消息 JSON，字段名与 Web 端保持一致，避免 CLI 单独发明协议。
     */
    private String buildStructuredMessagesJson(CliSlashCommand command, String question) {
        cn.hutool.json.JSONArray messages = JSONUtil.createArray();
        messages.add(JSONUtil.createObj()
            .set("type", "slash_command")
            .set("data", JSONUtil.createObj()
                .set("command", command.commandCode())
                .set("command_type", StrUtil.blankToDefault(command.commandType(), "BUILTIN").toLowerCase(Locale.ROOT))));
        messages.add(JSONUtil.createObj()
            .set("type", "text")
            .set("data", JSONUtil.createObj()
                .set("content", StrUtil.trimToEmpty(question))));
        return messages.toString();
    }

    /**
     * HTTP 错误响应优先读取后端 `ApiResponse.message`，解析失败时回退通用中文提示。
     */
    private String resolveErrorMessage(InputStream body) {
        try (InputStream inputStream = body) {
            String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            if (StrUtil.isBlank(text)) {
                return "聊天流请求失败";
            }
            try {
                String message = JSONUtil.parseObj(text).getStr("message");
                return StrUtil.blankToDefault(message, "聊天流请求失败");
            } catch (RuntimeException exception) {
                return text;
            }
        } catch (Exception exception) {
            return "聊天流请求失败：" + exception.getMessage();
        }
    }

    /**
     * meta/finish 到达后写回最近会话 ID，下一轮 TUI 输入即可续接同一后端会话。
     */
    private String persistConversationIdIfPresent(String previousConversationId, String conversationId) {
        if (!isNumericSessionId(conversationId)) {
            return previousConversationId;
        }
        String normalizedConversationId = conversationId.trim();
        if (normalizedConversationId.equals(previousConversationId)) {
            return previousConversationId;
        }
        CliConfig latestConfig = configStore.load();
        configStore.save(new CliConfig(
            latestConfig.serverUrl(),
            latestConfig.token(),
            latestConfig.approvalPolicy(),
            normalizedConversationId
        ));
        return normalizedConversationId;
    }

    /**
     * 判断会话 ID 是否可安全传给后端 Long 参数。
     */
    private boolean isNumericSessionId(String sessionId) {
        return sessionId != null && NUMERIC_SESSION_ID.matcher(sessionId.trim()).matches();
    }

    /**
     * 后端聊天流请求中的文本问题与结构化消息参数。
     *
     * @param question 发送给后端的纯文本问题，内置命令场景会去掉前导 slash token。
     * @param messagesJson 结构化消息 JSON，可为空。
     */
    private record StructuredSlashCommandRequest(String question, String messagesJson) {
    }
}
