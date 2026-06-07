package com.codingx.cli.auth;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.codingx.cli.config.CliConfig;
import com.codingx.cli.config.CliConfigStore;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * CLI 认证服务，负责浏览器 loopback 登录、设备码登录和 token 写入用户级配置。
 */
public class CliAuthService {

    /**
     * 浏览器回调等待超时秒数，避免 CLI 永久阻塞。
     */
    private static final long CALLBACK_TIMEOUT_SECONDS = 180;

    /**
     * 设备码轮询最大次数，防止后端异常时无限等待。
     */
    private static final int MAX_DEVICE_POLLS = 300;

    /**
     * 用户级配置存储，token 只能写入用户主目录。
     */
    private final CliConfigStore configStore;

    /**
     * 浏览器打开能力，测试可注入替身。
     */
    private final BrowserLauncher browserLauncher;

    /**
     * CLI 状态输出回调，命令模式下写到 stdout。
     */
    private final Consumer<String> outputConsumer;

    /**
     * HTTP 客户端，用于调用后端授权接口。
     */
    private final HttpClient httpClient;

    /**
     * @param configStore 用户级配置存储。
     * @param browserLauncher 浏览器打开能力。
     * @param outputConsumer CLI 输出回调。
     */
    public CliAuthService(
        CliConfigStore configStore,
        BrowserLauncher browserLauncher,
        Consumer<String> outputConsumer
    ) {
        this(configStore, browserLauncher, outputConsumer, HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build());
    }

    /**
     * 测试可注入 HTTP 客户端构造器。
     */
    CliAuthService(
        CliConfigStore configStore,
        BrowserLauncher browserLauncher,
        Consumer<String> outputConsumer,
        HttpClient httpClient
    ) {
        this.configStore = configStore;
        this.browserLauncher = browserLauncher;
        this.outputConsumer = outputConsumer;
        this.httpClient = httpClient;
    }

    /**
     * 执行浏览器 loopback 登录。
     *
     * @return true 表示登录成功并保存 token。
     */
    public boolean loginWithBrowser() {
        CliConfig config = configStore.load();
        String state = "state_" + IdUtil.fastSimpleUUID();
        String codeVerifier = "verifier_" + IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID();
        try (LoopbackCallbackServer callbackServer = LoopbackCallbackServer.start(state)) {
            String loginUrl = buildLoginUrl(config, callbackServer.callbackUri(), state, toCodeChallenge(codeVerifier));
            output("正在打开浏览器登录 CodingX: " + loginUrl);
            browserLauncher.open(loginUrl);
            LoopbackCallbackServer.CallbackResult callback = callbackServer.awaitCallback()
                .get(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            LoginPayload loginPayload = exchangeAuthorizationCode(config.serverUrl(), callback.code(), state, codeVerifier);
            saveLogin(config, loginPayload);
            output("CLI 登录成功：" + loginPayload.displayName());
            return true;
        } catch (Exception exception) {
            output("CLI 浏览器登录失败：" + exception.getMessage());
            output("如当前环境无法打开浏览器，可运行 codingx auth login --device 使用设备码登录。");
            return false;
        }
    }

    /**
     * 执行设备码登录，适合远程 SSH 或无浏览器环境。
     *
     * @return true 表示登录成功并保存 token。
     */
    public boolean loginWithDeviceCode() {
        CliConfig config = configStore.load();
        try {
            DeviceStartPayload startPayload = startDeviceAuthorization(config.serverUrl());
            String verificationUrl = absoluteFrontendUrl(config, startPayload.verificationUri())
                + "?deviceCode="
                + URLEncoder.encode(startPayload.userCode(), StandardCharsets.UTF_8);
            output("请在浏览器打开以下地址并授权 CodingX CLI：");
            output(verificationUrl);
            output("设备验证码：" + startPayload.userCode());
            for (int index = 0; index < MAX_DEVICE_POLLS; index++) {
                DeviceTokenPayload tokenPayload = exchangeDeviceToken(config.serverUrl(), startPayload.deviceCode());
                if (tokenPayload.approved() && tokenPayload.login() != null) {
                    saveLogin(config, tokenPayload.login());
                    output("CLI 登录成功：" + tokenPayload.login().displayName());
                    return true;
                }
                sleepSeconds(startPayload.pollIntervalSeconds());
            }
            output("CLI 设备码登录超时，请重新执行 codingx auth login --device");
            return false;
        } catch (Exception exception) {
            output("CLI 设备码登录失败：" + exception.getMessage());
            return false;
        }
    }

    /**
     * 判断 CLI 本机是否已有可用 token；仅检查本地配置，真实有效性仍以后端响应为准。
     *
     * @return true 表示本地配置中存在 token。
     */
    public boolean isLoggedIn() {
        return configStore != null && StrUtil.isNotBlank(configStore.load().token());
    }

    /**
     * 清理 CLI 本机登录态；退出登录只清空 token 和最近会话，保留后端地址与审批策略。
     *
     * @return true 表示本机 token 已清空。
     */
    public boolean logout() {
        try {
            CliConfig config = configStore.load();
            configStore.save(new CliConfig(
                config.serverUrl(),
                "",
                config.approvalPolicy(),
                null
            ));
            output("CLI 已退出登录");
            return true;
        } catch (RuntimeException exception) {
            output("CLI 退出登录失败：" + exception.getMessage());
            return false;
        }
    }

    /**
     * 构造前端 CLI 登录地址。
     */
    private String buildLoginUrl(CliConfig config, URI callbackUri, String state, String codeChallenge) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("redirectUri", callbackUri.toString());
        query.put("state", state);
        query.put("codeChallenge", codeChallenge);
        return absoluteFrontendUrl(config, "/cli-login") + "?" + encodeQuery(query);
    }

    /**
     * 使用授权码换取后端登录 token。
     */
    private LoginPayload exchangeAuthorizationCode(String serverUrl, String code, String state, String codeVerifier) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("state", state);
        body.put("codeVerifier", codeVerifier);
        HttpResponse<String> response = postJson(serverUrl, "/api/auth/cli/token", body);
        var data = parseEnvelopeData(response, "CLI 授权码兑换失败");
        return parseLoginPayload(data);
    }

    /**
     * 启动后端设备码授权。
     */
    private DeviceStartPayload startDeviceAuthorization(String serverUrl) throws Exception {
        HttpResponse<String> response = postJson(serverUrl, "/api/auth/cli/device/start", Map.of());
        var data = parseEnvelopeData(response, "CLI 设备码创建失败");
        return new DeviceStartPayload(
            data.getStr("deviceCode"),
            data.getStr("userCode"),
            data.getStr("verificationUri"),
            data.getLong("expiresInSeconds", 0L),
            data.getLong("pollIntervalSeconds", 2L)
        );
    }

    /**
     * 轮询设备码授权结果。
     */
    private DeviceTokenPayload exchangeDeviceToken(String serverUrl, String deviceCode) throws Exception {
        HttpResponse<String> response = postJson(serverUrl, "/api/auth/cli/device/token", Map.of(
            "deviceCode", deviceCode
        ));
        var data = parseEnvelopeData(response, "CLI 设备码轮询失败");
        var loginObject = data.getJSONObject("login");
        return new DeviceTokenPayload(
            Boolean.TRUE.equals(data.getBool("approved")),
            data.getStr("status"),
            loginObject == null ? null : parseLoginPayload(loginObject)
        );
    }

    /**
     * 发起 JSON POST 请求。
     */
    private HttpResponse<String> postJson(String serverUrl, String path, Map<String, Object> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(apiUri(serverUrl, path))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSONUtil.toJsonStr(body), StandardCharsets.UTF_8))
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * 从统一 ApiResponse 中读取 data，并优先抛出后端 message。
     */
    private cn.hutool.json.JSONObject parseEnvelopeData(HttpResponse<String> response, String fallbackMessage) {
        try {
            var object = JSONUtil.parseObj(response.body());
            boolean success = Boolean.TRUE.equals(object.getBool("success"));
            String message = StrUtil.blankToDefault(object.getStr("message"), fallbackMessage);
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !success) {
                throw new IllegalStateException(message);
            }
            return object.getJSONObject("data");
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalStateException) {
                throw exception;
            }
            throw new IllegalStateException(fallbackMessage + "：" + exception.getMessage(), exception);
        }
    }

    /**
     * 手动解析登录响应，避免 Java record 反射映射差异导致 CLI 登录流程失败。
     */
    private LoginPayload parseLoginPayload(cn.hutool.json.JSONObject data) {
        return new LoginPayload(
            data.getStr("userId"),
            data.getStr("username"),
            data.getStr("displayName"),
            data.getStr("userType"),
            data.getStr("token")
        );
    }

    /**
     * 保存登录 token，保留已有审批策略，登录后清空最近会话避免串到旧 token 的会话。
     */
    private void saveLogin(CliConfig previousConfig, LoginPayload loginPayload) {
        configStore.save(new CliConfig(
            previousConfig.serverUrl(),
            loginPayload.token(),
            previousConfig.approvalPolicy(),
            null
        ));
    }

    /**
     * 计算 PKCE S256 challenge。
     */
    private String toCodeChallenge(String codeVerifier) {
        byte[] digest = DigestUtil.sha256(codeVerifier.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    /**
     * 拼接后端 API URI。
     */
    private URI apiUri(String serverUrl, String path) {
        return URI.create(StrUtil.removeSuffix(StrUtil.trimToEmpty(serverUrl), "/") + path);
    }

    /**
     * 推导用户端前端地址；本地开发从 5001 切到 5002，正式 api 子域回退到根域。
     */
    private String absoluteFrontendUrl(CliConfig config, String path) {
        String frontendBase = deriveFrontendBase(config.serverUrl());
        return StrUtil.removeSuffix(frontendBase, "/") + path;
    }

    /**
     * 根据后端地址推导前端地址。
     */
    private String deriveFrontendBase(String serverUrl) {
        URI uri = URI.create(StrUtil.trimToEmpty(serverUrl));
        String text = StrUtil.removeSuffix(serverUrl.trim(), "/");
        if (uri.getPort() == 5001) {
            return text.replace(":5001", ":5002");
        }
        String host = uri.getHost();
        if (host != null && host.startsWith("api.")) {
            String rootHost = host.substring("api.".length());
            int port = uri.getPort();
            return uri.getScheme() + "://" + rootHost + (port > 0 ? ":" + port : "");
        }
        return text;
    }

    /**
     * URL 编码查询参数。
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
     * 轮询间隔休眠，测试传 0 时不等待。
     */
    private void sleepSeconds(long seconds) throws InterruptedException {
        if (seconds > 0) {
            TimeUnit.SECONDS.sleep(seconds);
        }
    }

    /**
     * 输出登录进度。
     */
    private void output(String message) {
        if (outputConsumer != null) {
            outputConsumer.accept(message + System.lineSeparator());
        }
    }

    /**
     * 登录响应 data。
     */
    public record LoginPayload(
        String userId, // 当前用户主键，CLI 仅用于展示和排障。
        String username, // 登录用户名。
        String displayName, // 展示名称。
        String userType, // 用户类型。
        String token // 后端发放的 satoken。
    ) {
    }

    /**
     * 设备码启动响应 data。
     */
    public record DeviceStartPayload(
        String deviceCode, // CLI 轮询使用的内部设备码。
        String userCode, // 用户可读验证码。
        String verificationUri, // 前端验证页路径或地址。
        long expiresInSeconds, // 有效期秒数。
        long pollIntervalSeconds // 轮询间隔秒数。
    ) {
    }

    /**
     * 设备码 token 轮询响应 data。
     */
    public record DeviceTokenPayload(
        boolean approved, // true 表示已授权。
        String status, // authorization_pending 或 approved。
        LoginPayload login // 授权成功后的登录信息。
    ) {
    }
}
