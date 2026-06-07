package com.codingx.cli.slash;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.cli.config.CliConfig;
import com.codingx.cli.config.CliConfigStore;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * 后端 Slash Command 目录客户端，读取管理端治理中心启用后暴露给用户侧的真实命令。
 */
public class BackendSlashCommandCatalog implements SlashCommandCatalog {

    /**
     * 用户级 CLI 配置存储，提供后端地址和 satoken。
     */
    private final CliConfigStore configStore;

    /**
     * JDK HTTP 客户端，保持 CLI 无额外运行时依赖。
     */
    private final HttpClient httpClient;

    /**
     * @param configStore 用户级 CLI 配置存储。
     */
    public BackendSlashCommandCatalog(CliConfigStore configStore) {
        this(
            configStore,
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()
        );
    }

    /**
     * 测试可注入构造器，避免单测依赖真实后端。
     */
    BackendSlashCommandCatalog(CliConfigStore configStore, HttpClient httpClient) {
        this.configStore = configStore;
        this.httpClient = httpClient;
    }

    @Override
    public List<CliSlashCommand> listCommands() {
        CliConfig config = configStore.load();
        if (StrUtil.isBlank(config.token())) {
            // 步骤 1：未登录时不请求目录接口，TUI 仍展示本地 /login 等控制命令。
            return List.of();
        }
        try {
            HttpRequest request = buildRequest(config);
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                closeQuietly(response.body());
                return List.of();
            }
            try (InputStream body = response.body()) {
                return parseCommands(new String(body.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // 步骤 2：命令目录只是输入辅助，失败时必须降级为空目录，不能阻断 TUI 发送普通聊天。
            return List.of();
        }
    }

    /**
     * 构造用户侧 Slash Command 目录请求。
     */
    private HttpRequest buildRequest(CliConfig config) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(buildUri(config.serverUrl()))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .header("Accept", "application/json");
        if (StrUtil.isNotBlank(config.token())) {
            builder.header("satoken", config.token().trim());
        }
        return builder.build();
    }

    /**
     * 拼装目录 URI；serverUrl 可带尾部斜杠，最终统一落到 `/api/chat/slash-commands`。
     */
    private URI buildUri(String serverUrl) {
        String normalizedServerUrl = StrUtil.removeSuffix(StrUtil.trimToEmpty(serverUrl), "/");
        return URI.create(normalizedServerUrl + "/api/chat/slash-commands");
    }

    /**
     * 解析后端统一 ApiResponse，只有 success=true 且 data 为数组时才返回命令列表。
     */
    private List<CliSlashCommand> parseCommands(String responseText) {
        if (StrUtil.isBlank(responseText)) {
            return List.of();
        }
        JSONObject envelope = JSONUtil.parseObj(responseText);
        if (!envelope.getBool("success", false)) {
            return List.of();
        }
        JSONArray data = envelope.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            return List.of();
        }
        return data.stream()
            .map(JSONUtil::parseObj)
            .map(this::toCommand)
            .filter(command -> StrUtil.isNotBlank(command.commandCode()))
            .sorted(Comparator
                .comparing((CliSlashCommand command) -> command.sortNo() == null ? Integer.MAX_VALUE : command.sortNo())
                .thenComparing(CliSlashCommand::commandCode))
            .toList();
    }

    /**
     * 将后端领域对象规整为 CLI 展示对象，避免 TUI 直接依赖后端 JSON 字段缺省逻辑。
     */
    private CliSlashCommand toCommand(JSONObject item) {
        String commandCode = StrUtil.removePrefix(StrUtil.trimToEmpty(item.getStr("commandCode")), "/");
        return new CliSlashCommand(
            StrUtil.toStringOrNull(item.get("id")),
            commandCode,
            StrUtil.blankToDefault(item.getStr("displayName"), StrUtil.isBlank(commandCode) ? "" : "/" + commandCode),
            StrUtil.trimToEmpty(item.getStr("description")),
            StrUtil.blankToDefault(item.getStr("commandType"), "BUILTIN"),
            item.getInt("sortNo")
        );
    }

    /**
     * 非成功响应也需要关闭 body，避免测试 HTTP 服务连接被占用。
     */
    private void closeQuietly(InputStream body) {
        if (body == null) {
            return;
        }
        try {
            body.close();
        } catch (Exception ignored) {
            // 关闭失败不影响命令目录降级逻辑。
        }
    }
}
