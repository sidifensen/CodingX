package com.codingx.tool.application.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.config.WebAccessProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

/**
 * 通过本机 CDP Proxy 代理浏览器操作的模型可见工具执行器。
 * 约束：这里只做轻量 HTTP 代理，不自行实现浏览器能力，避免和宿主 Chrome 状态脱节。
 */
@Component
public class WebAccessChatToolExecutor implements ChatToolExecutor {

    private static final String TOOL_CODE = "web_access";
    private static final String DEFAULT_ACTION = "targets";
    private static final MediaType TEXT_PLAIN = MediaType.get("text/plain; charset=utf-8");
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final WebAccessProperties properties;
    private final OkHttpClient okHttpClient;

    public WebAccessChatToolExecutor(WebAccessProperties properties) {
        this.properties = properties;
        this.okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
            .readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
            .callTimeout(properties.getRequestTimeoutMs(), TimeUnit.MILLISECONDS)
            .build();
    }

    @Override
    public List<String> toolCodes() {
        return List.of(TOOL_CODE);
    }

    @Override
    public ChatToolExecutionResult execute(String toolCode, String question) {
        String normalizedToolCode = normalizeToolCode(toolCode);
        if (!StrUtil.equals(normalizedToolCode, TOOL_CODE)) {
            throw new BusinessException("CHAT_TOOL_WEB_ACCESS_INVALID_ACTION", ErrorMessageCatalog.CHAT_TOOL_WEB_ACCESS_ACTION_INVALID);
        }
        JSONObject input = parseInput(question);
        String action = StrUtil.blankToDefault(input.getStr("action"), DEFAULT_ACTION);
        action = normalizeAction(action);
        return switch (action) {
            case "targets", "list_targets" -> executeTargets(action);
            case "new", "open" -> executeOpen(action, input);
            case "eval" -> executeEval(action, input);
            case "screenshot" -> executeScreenshot(action, input);
            case "click" -> executeClick(action, input);
            case "setfiles", "set_files" -> executeSetFiles(action, input);
            case "scroll" -> executeScroll(action, input);
            case "close" -> executeClose(action, input);
            default -> throw new BusinessException("CHAT_TOOL_WEB_ACCESS_INVALID_ACTION", ErrorMessageCatalog.CHAT_TOOL_WEB_ACCESS_ACTION_INVALID);
        };
    }

    /**
     * 列出当前浏览器标签页。
     */
    private ChatToolExecutionResult executeTargets(String action) {
        HttpUrl url = buildUrl("targets", Map.of());
        String responseBody = callGet(url);
        return buildResult(action, url, null, null, null, responseBody, Map.of());
    }

    /**
     * 打开新标签页。
     */
    private ChatToolExecutionResult executeOpen(String action, JSONObject input) {
        String urlText = requireText(input, "url", ErrorMessageCatalog.CHAT_TOOL_WEB_ACCESS_URL_REQUIRED);
        HttpUrl url = buildUrl("new", Map.of("url", urlText));
        String responseBody = callGet(url);
        return buildResult(action, url, null, null, null, responseBody, Map.of("url", urlText));
    }

    /**
     * 在指定标签页中执行 JavaScript。
     */
    private ChatToolExecutionResult executeEval(String action, JSONObject input) {
        String target = requireText(input, "target", ErrorMessageCatalog.CHAT_TOOL_WEB_ACCESS_TARGET_REQUIRED);
        String script = requireText(input, "script", ErrorMessageCatalog.CHAT_TOOL_WEB_ACCESS_SCRIPT_REQUIRED);
        HttpUrl url = buildUrl("eval", Map.of("target", target));
        String responseBody = callPost(url, script, TEXT_PLAIN);
        return buildResult(action, url, target, script, null, responseBody, Map.of("target", target, "script", script));
    }

    /**
     * 截图并把结果落到本地文件，方便后续 view_image 读取。
     */
    private ChatToolExecutionResult executeScreenshot(String action, JSONObject input) {
        String target = requireText(input, "target", ErrorMessageCatalog.CHAT_TOOL_WEB_ACCESS_TARGET_REQUIRED);
        String filePath = StrUtil.trimToNull(input.getStr("file"));
        if (filePath == null) {
            filePath = defaultScreenshotPath();
        }
        Path outputPath = Path.of(filePath).toAbsolutePath().normalize();
        try {
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
        } catch (IOException exception) {
            throw new BusinessException("CHAT_TOOL_WEB_ACCESS_REQUEST_FAILED", ErrorMessageCatalog.CHAT_TOOL_WEB_ACCESS_REQUEST_FAILED_PREFIX + exception.getMessage());
        }
        HttpUrl url = buildUrl("screenshot", Map.of("target", target, "file", outputPath.toString()));
        String responseBody = callGet(url);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("target", target);
        metadata.put("file", outputPath.toString());
        return buildResult(action, url, target, null, outputPath.toString(), responseBody, metadata);
    }

    /**
     * 点击指定 CSS 选择器。
     */
    private ChatToolExecutionResult executeClick(String action, JSONObject input) {
        String target = requireText(input, "target", ErrorMessageCatalog.CHAT_TOOL_WEB_ACCESS_TARGET_REQUIRED);
        String selector = requireText(input, "selector", ErrorMessageCatalog.CHAT_TOOL_WEB_ACCESS_SELECTOR_REQUIRED);
        HttpUrl url = buildUrl("click", Map.of("target", target));
        String responseBody = callPost(url, selector, TEXT_PLAIN);
        return buildResult(action, url, target, selector, null, responseBody, Map.of("target", target, "selector", selector));
    }

    /**
     * 为文件输入框设置文件列表。
     */
    private ChatToolExecutionResult executeSetFiles(String action, JSONObject input) {
        String target = requireText(input, "target", ErrorMessageCatalog.CHAT_TOOL_WEB_ACCESS_TARGET_REQUIRED);
        String selector = requireText(input, "selector", ErrorMessageCatalog.CHAT_TOOL_WEB_ACCESS_SELECTOR_REQUIRED);
        List<String> files = readStringList(input.getJSONArray("files"));
        if (files.isEmpty()) {
            throw new BusinessException("CHAT_TOOL_WEB_ACCESS_FILES_REQUIRED", ErrorMessageCatalog.CHAT_TOOL_WEB_ACCESS_FILES_REQUIRED);
        }
        JSONObject body = JSONUtil.createObj()
            .set("selector", selector)
            .set("files", files);
        HttpUrl url = buildUrl("setFiles", Map.of("target", target));
        String responseBody = callPost(url, body.toString(), JSON_MEDIA_TYPE);
        return buildResult(action, url, target, body.toString(), null, responseBody, Map.of("target", target, "selector", selector, "files", files));
    }

    /**
     * 滚动当前标签页。
     */
    private ChatToolExecutionResult executeScroll(String action, JSONObject input) {
        String target = requireText(input, "target", ErrorMessageCatalog.CHAT_TOOL_WEB_ACCESS_TARGET_REQUIRED);
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("target", target);
        String direction = StrUtil.trimToNull(input.getStr("direction"));
        if (direction != null) {
            queryParams.put("direction", direction);
        }
        String x = StrUtil.trimToNull(input.getStr("x"));
        if (x != null) {
            queryParams.put("x", x);
        }
        String y = StrUtil.trimToNull(input.getStr("y"));
        if (y != null) {
            queryParams.put("y", y);
        }
        HttpUrl url = buildUrl("scroll", queryParams);
        String responseBody = callGet(url);
        return buildResult(action, url, target, null, null, responseBody, new LinkedHashMap<>(queryParams));
    }

    /**
     * 关闭标签页。
     */
    private ChatToolExecutionResult executeClose(String action, JSONObject input) {
        String target = requireText(input, "target", ErrorMessageCatalog.CHAT_TOOL_WEB_ACCESS_TARGET_REQUIRED);
        HttpUrl url = buildUrl("close", Map.of("target", target));
        String responseBody = callGet(url);
        return buildResult(action, url, target, null, null, responseBody, Map.of("target", target));
    }

    /**
     * 执行 GET 请求。
     */
    private String callGet(HttpUrl url) {
        Request request = new Request.Builder().url(url).get().build();
        return executeRequest(request);
    }

    /**
     * 执行 POST 请求。
     */
    private String callPost(HttpUrl url, String body, MediaType mediaType) {
        RequestBody requestBody = RequestBody.create(StrUtil.blankToDefault(body, ""), mediaType);
        Request request = new Request.Builder().url(url).post(requestBody).build();
        return executeRequest(request);
    }

    /**
     * 发送 HTTP 请求并统一处理异常。
     */
    private String executeRequest(Request request) {
        try (Response response = okHttpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                String message = StrUtil.blankToDefault(
                    responseBody,
                    ErrorMessageCatalog.CHAT_TOOL_WEB_ACCESS_REQUEST_FAILED_PREFIX + response.code()
                );
                throw new BusinessException("CHAT_TOOL_WEB_ACCESS_REQUEST_FAILED", message);
            }
            return responseBody;
        } catch (IOException exception) {
            throw new BusinessException("CHAT_TOOL_WEB_ACCESS_PROXY_UNAVAILABLE", ErrorMessageCatalog.CHAT_TOOL_WEB_ACCESS_PROXY_UNAVAILABLE);
        }
    }

    /**
     * 统一构建工具执行结果。
     */
    private ChatToolExecutionResult buildResult(
        String action,
        HttpUrl url,
        String target,
        String requestBody,
        String filePath,
        String responseBody,
        Map<String, Object> extraMetadata
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("toolCode", TOOL_CODE);
        metadata.put("action", action);
        metadata.put("requestUrl", url.toString());
        if (target != null) {
            metadata.put("target", target);
        }
        if (requestBody != null) {
            metadata.put("requestBody", requestBody);
        }
        if (filePath != null) {
            metadata.put("file", filePath);
        }
        if (StrUtil.isNotBlank(responseBody)) {
            metadata.put("responseBody", responseBody);
        }
        if (extraMetadata != null && !extraMetadata.isEmpty()) {
            metadata.putAll(extraMetadata);
        }
        String content;
        if (StrUtil.isNotBlank(responseBody)) {
            content = responseBody;
        } else if ("screenshot".equals(action) && filePath != null) {
            content = "已截图到 " + filePath;
        } else {
            content = action + " 已执行";
        }
        return new ChatToolExecutionResult(TOOL_CODE, content, metadata);
    }

    /**
     * 解析输入载荷，兼容空串与非 JSON 场景。
     */
    private JSONObject parseInput(String question) {
        String normalizedQuestion = StrUtil.blankToDefault(question, "{}");
        try {
            return JSONUtil.parseObj(normalizedQuestion);
        } catch (Exception exception) {
            throw new BusinessException(
                "CHAT_TOOL_WEB_ACCESS_REQUEST_FAILED",
                ErrorMessageCatalog.CHAT_TOOL_WEB_ACCESS_REQUEST_FAILED_PREFIX + "参数必须是 JSON"
            );
        }
    }

    /**
     * 提取并校验必填文本字段。
     */
    private String requireText(JSONObject input, String key, String message) {
        String value = StrUtil.trimToNull(input.getStr(key));
        if (value == null) {
            throw new BusinessException("CHAT_TOOL_WEB_ACCESS_REQUEST_FAILED", message);
        }
        return value;
    }

    /**
     * 解析字符串数组参数。
     */
    private List<String> readStringList(JSONArray array) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : array) {
            String value = StrUtil.trimToNull(Objects.toString(item, null));
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    /**
     * 生成默认截图文件路径。
     */
    private String defaultScreenshotPath() {
        Path screenshotDir = Path.of(System.getProperty("java.io.tmpdir"), "codingx-web-access");
        try {
            Files.createDirectories(screenshotDir);
        } catch (IOException exception) {
            throw new BusinessException("CHAT_TOOL_WEB_ACCESS_REQUEST_FAILED", ErrorMessageCatalog.CHAT_TOOL_WEB_ACCESS_REQUEST_FAILED_PREFIX + exception.getMessage());
        }
        return screenshotDir.resolve("screenshot-" + IdUtil.fastSimpleUUID() + ".png").toString();
    }

    /**
     * 构建完整请求地址。
     */
    private HttpUrl buildUrl(String path, Map<String, String> queryParams) {
        HttpUrl baseUrl = HttpUrl.get(StrUtil.blankToDefault(properties.getBaseUrl(), "http://127.0.0.1:3456"));
        HttpUrl.Builder builder = baseUrl.newBuilder().addPathSegment(path);
        if (queryParams != null) {
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                builder.addQueryParameter(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }

    private String normalizeToolCode(String toolCode) {
        return StrUtil.trimToEmpty(toolCode).toLowerCase(Locale.ROOT);
    }

    private String normalizeAction(String action) {
        return StrUtil.trimToEmpty(action).toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
