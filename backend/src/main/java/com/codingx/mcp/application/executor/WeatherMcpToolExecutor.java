package com.codingx.mcp.application.executor;

import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.application.service.AiPromptExecutionService;
import com.codingx.chat.application.service.PromptTemplateLoader;
import com.codingx.common.error.ErrorMessageCatalog;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 天气查询 MCP 工具执行器。
 * <p>
 * 该执行器通过 Open-Meteo 实时接口获取天气能力，返回当前天气或未来天气预报文本。
 */
@Component
@Slf4j
public class WeatherMcpToolExecutor implements ChatMcpToolExecutor {

    /**
     * 工具唯一标识。
     */
    private static final String TOOL_ID = "weather_query";

    /**
     * 城市抽取提示词模板编码，用于从用户自然语言中提取城市名称。
     */
    private static final String CITY_EXTRACT_TEMPLATE = "weather-city-extract";

    /**
     * Open-Meteo 地理编码接口地址，用城市名换取经纬度。
     */
    private static final String OPEN_METEO_GEOCODING = "https://geocoding-api.open-meteo.com/v1/search";

    /**
     * Open-Meteo 天气预报接口地址，用经纬度换取实时天气和日级预报。
     */
    private static final String OPEN_METEO_FORECAST = "https://api.open-meteo.com/v1/forecast";

    /**
     * 天气问题规则解析器，作为 AI 参数抽取后的确定性兜底。
     */
    private final WeatherQuestionParser weatherQuestionParser = new WeatherQuestionParser();

    /**
     * 提示词模板加载器；未配置时城市抽取自动降级为规则解析。
     */
    @Autowired(required = false)
    private PromptTemplateLoader promptTemplateLoader;

    /**
     * AI 提示词执行服务；未配置或调用失败时不影响天气工具主链路。
     */
    @Autowired(required = false)
    private AiPromptExecutionService aiPromptExecutionService;

    /**
     * 暴露地理编码地址，便于测试注入替身接口。
     * @return 地理编码接口地址。
     */
    protected String geocodingEndpoint() {
        return OPEN_METEO_GEOCODING;
    }

    /**
     * 暴露天气预报地址，便于测试注入替身接口。
     * @return 天气预报接口地址。
     */
    protected String forecastEndpoint() {
        return OPEN_METEO_FORECAST;
    }

    @Override
    public String toolId() {
        // 步骤 1：返回注册到聊天 MCP 路由中的固定工具标识。
        return TOOL_ID;
    }

    /**
     * 按问题提取城市与查询类型并返回天气结果。
     *
     * @param question 用户问题。
     * @return 工具结果。
     */
    @Override
    public ChatMcpToolResult execute(String question) {
        // 步骤 1：无进度监听的调用统一复用带监听器入口，避免两套执行逻辑分叉。
        return execute(question, ChatMcpProgressListener.noop());
    }

    /**
     * 按问题提取城市与查询类型并返回天气结果，同时上报真实阶段进度。
     *
     * @param question 用户问题。
     * @param progressListener 进度回调。
     * @return 工具结果。
     */
    @Override
    public ChatMcpToolResult execute(String question, ChatMcpProgressListener progressListener) {
        // 步骤 1：规整用户问题，先尝试 AI 抽取城市，再交给规则解析器补齐查询类型和天数。
        String safeQuestion = StrUtil.blankToDefault(question, "");
        WeatherQuestionParser.WeatherQueryContext context = weatherQuestionParser.parse(safeQuestion, extractCityByAi(safeQuestion));
        // 步骤 2：上报解析阶段进度，前端可据此展示工具真实执行状态。
        emitProgress(
            progressListener,
            "parse-question",
            "正在解析天气查询条件",
            Map.of(
                "queryType", context.queryType(),
                "days", context.days()
            )
        );
        if (StrUtil.isBlank(context.city())) {
            // 步骤 3：城市缺失时不调用外部接口，直接返回可读提示和错误元数据。
            return new ChatMcpToolResult(toolId(), "请提供城市名称", Map.of("error", true));
        }
        try {
            // 步骤 4：先用城市名解析经纬度，后续天气接口只依赖标准坐标。
            emitProgress(
                progressListener,
                "resolve-coordinates",
                "正在查询城市坐标",
                Map.of("queryCity", context.city())
            );
            GeocodeResult geocodeResult = resolveCoordinates(context.city());
            // 步骤 5：按标准坐标和天数查询天气数据，外部接口失败会进入统一降级路径。
            emitProgress(
                progressListener,
                "query-forecast",
                "正在拉取天气数据",
                Map.of(
                    "city", geocodeResult.displayCity(),
                    "days", context.days()
                )
            );
            ForecastResult forecastResult = queryForecast(geocodeResult.latitude(), geocodeResult.longitude(), context.days());
            // 步骤 6：根据查询类型生成当前天气或未来预报文本。
            emitProgress(
                progressListener,
                "build-result",
                "正在整理天气结果",
                Map.of(
                    "city", geocodeResult.displayCity(),
                    "queryType", context.queryType()
                )
            );
            String content = "forecast".equals(context.queryType())
                ? buildForecastResult(geocodeResult.displayCity(), forecastResult.dailyRows(), context.days())
                : buildCurrentResult(geocodeResult.displayCity(), forecastResult.current(), forecastResult.today());
            // 步骤 7：组装工具元数据，保留解析城市、标准城市、坐标和数据源供前端展示或排障。
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("city", geocodeResult.displayCity());
            metadata.put("queryCity", context.city());
            metadata.put("queryType", context.queryType());
            metadata.put("days", context.days());
            metadata.put("latitude", geocodeResult.latitude());
            metadata.put("longitude", geocodeResult.longitude());
            metadata.put("timezone", forecastResult.timezone());
            metadata.put("source", "open-meteo");
            metadata.put("sourceApi", List.of(OPEN_METEO_GEOCODING, OPEN_METEO_FORECAST));
            metadata.put("resolvedName", geocodeResult.fullName());
            emitProgress(
                progressListener,
                "completed",
                "天气数据整理完成",
                Map.of(
                    "city", geocodeResult.displayCity(),
                    "timezone", forecastResult.timezone()
                )
            );
            return new ChatMcpToolResult(toolId(), content, metadata);
        } catch (Exception exception) {
            // 步骤 8：外部接口、响应结构或日期解析异常统一降级，避免 MCP 工具错误中断聊天主链路。
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("city", context.city());
            metadata.put("queryType", context.queryType());
            metadata.put("days", context.days());
            metadata.put("error", true);
            emitProgress(
                progressListener,
                "failed",
                "天气查询失败，正在返回降级结果",
                Map.of(
                    "city", context.city(),
                    "queryType", context.queryType()
                )
            );
            // 记录外部天气接口失败根因，便于线上定位“统一降级文案”背后的真实异常。
            log.warn(
                "天气工具调用失败，question={}, city={}, queryType={}, days={}",
                safeQuestion,
                context.city(),
                context.queryType(),
                context.days(),
                exception
            );
            return new ChatMcpToolResult(toolId(), "天气服务暂时不可用，请稍后重试", metadata);
        }
    }

    /**
     * 借助 LLM 从自然语言问题中抽取城市，作为规则匹配前的主路径。
     *
     * @param question 用户问题。
     * @return 模型抽取到的城市；抽取失败返回 null。
     */
    protected String extractCityByAi(String question) {
        // 步骤 1：AI 抽取能力是可选依赖，依赖缺失或问题为空时直接交给规则解析。
        if (StrUtil.isBlank(question) || promptTemplateLoader == null || aiPromptExecutionService == null) {
            return null;
        }
        try {
            // 步骤 2：加载城市抽取模板并调用模型，把自然语言问题转换成可解析 JSON。
            String prompt = promptTemplateLoader.load(CITY_EXTRACT_TEMPLATE);
            String raw = aiPromptExecutionService.complete(prompt, question);
            // 步骤 3：解析模型输出中的 city 字段，解析失败返回 null 触发规则兜底。
            return parseCityFromAiPayload(raw);
        } catch (Exception exception) {
            // AI 参数抽取失败时必须自动降级到规则兜底，避免影响主链路可用性。
            log.debug("天气城市 AI 抽取失败，回退规则解析，question={}", question, exception);
            return null;
        }
    }

    /**
     * 解析 AI 返回的 JSON，提取 city 字段。
     *
     * @param raw 模型原始输出。
     * @return 城市名；解析失败返回 null。
     */
    private String parseCityFromAiPayload(String raw) {
        // 步骤 1：模型无输出时视为抽取失败，不抛异常影响主链路。
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            // 步骤 2：优先按标准 JSON 解析，适配模型严格返回 {"city":"..."} 的场景。
            return JSONUtil.parseObj(raw).getStr("city");
        } catch (Exception primaryException) {
            // 步骤 3：模型可能带解释文本，尝试截取首个 JSON 片段继续解析。
            String jsonFragment = ReUtil.get("(?s)\\{.*?\\}", raw, 0);
            if (StrUtil.isBlank(jsonFragment)) {
                return null;
            }
            try {
                return JSONUtil.parseObj(jsonFragment).getStr("city");
            } catch (Exception secondaryException) {
                // 步骤 4：二次解析仍失败时返回 null，由规则解析器继续兜底。
                return null;
            }
        }
    }

    /**
     * 构建当前天气结果。
     *
     * @param city 城市。
     * @return 当前天气文本。
     */
    private String buildCurrentResult(String city, CurrentWeatherData current, DailyWeatherData today) {
        // 步骤 1：按固定中文模板输出当前天气，保证工具结果可直接展示给用户。
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("【%s 今日天气】\n\n", city));
        builder.append(String.format("日期: %s\n", current.date().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))));
        builder.append(String.format("天气: %s\n", weatherCodeLabel(current.weatherCode())));
        builder.append(String.format("当前温度: %.1f°C\n", current.temperature()));
        builder.append(String.format("体感温度: %.1f°C\n", current.apparentTemperature()));
        builder.append(String.format("最高温度: %.1f°C\n", today.tempMax()));
        builder.append(String.format("最低温度: %.1f°C\n", today.tempMin()));
        builder.append(String.format("降水概率: %d%%\n", today.precipitationProbabilityMax()));
        builder.append(String.format("风速: %.1f km/h\n", current.windSpeed()));
        builder.append(String.format("数据源: Open-Meteo（UTC偏移 %d 秒）\n", current.utcOffsetSeconds()));

        // 步骤 2：基于降水、极端高温和低温补充出行提示，避免只返回裸数据。
        if (today.precipitationProbabilityMax() >= 50 || weatherCodeLabel(current.weatherCode()).contains("雨")
            || weatherCodeLabel(current.weatherCode()).contains("雪")) {
            builder.append("\n提示: 今日有降水，出行请携带雨具。");
        } else if (today.tempMax() >= 35) {
            builder.append("\n提示: 今日高温，注意防暑降温。");
        } else if (today.tempMin() <= 0) {
            builder.append("\n提示: 今日气温较低，注意防寒保暖。");
        }
        return builder.toString().trim();
    }

    /**
     * 构建天气预报结果。
     *
     * @param city 城市。
     * @param days 天数。
     * @return 预报文本。
     */
    private String buildForecastResult(String city, List<DailyWeatherData> dailyRows, int days) {
        // 步骤 1：按请求天数和接口实际返回行数取交集，避免越界访问。
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("【%s 未来%d天天气预报】\n\n", city, days));
        int loopDays = Math.min(days, dailyRows.size());
        for (int d = 0; d < loopDays; d++) {
            // 步骤 2：逐日输出天气、温度、降水概率和风速，前端无需再解析结构化字段。
            DailyWeatherData weather = dailyRows.get(d);
            LocalDate date = weather.date();
            String dayLabel = d == 0 ? "今天" : d == 1 ? "明天" : d == 2 ? "后天" : date.format(DateTimeFormatter.ofPattern("MM月dd日"));
            builder.append(String.format("📅 %s（%s）\n", dayLabel, date.format(DateTimeFormatter.ofPattern("MM-dd"))));
            builder.append(String.format("   天气: %s | 温度: %.1f°C ~ %.1f°C\n", weatherCodeLabel(weather.weatherCode()), weather.tempMin(), weather.tempMax()));
            builder.append(String.format("   降水概率: %d%% | 最大风速: %.1f km/h\n\n", weather.precipitationProbabilityMax(), weather.windSpeedMax()));
        }
        if (!dailyRows.isEmpty()) {
            // 步骤 3：首尾最高温差超过阈值时追加趋势提醒，帮助用户理解未来变化。
            DailyWeatherData todayWeather = dailyRows.getFirst();
            DailyWeatherData lastDayWeather = dailyRows.get(loopDays - 1);
            double tempTrend = lastDayWeather.tempMax() - todayWeather.tempMax();
            if (Math.abs(tempTrend) >= 5) {
                builder.append(String.format(
                    "趋势: 未来%d天气温%s，注意%s。",
                    days,
                    tempTrend > 0 ? "逐渐升高" : "逐渐下降",
                    tempTrend > 0 ? "防暑" : "保暖"
                ));
            }
        }
        return builder.toString().trim();
    }

    /**
     * 通过地理编码接口将城市名解析为坐标。
     *
     * @param city 城市。
     * @return 坐标与规范化名称。
     */
    private GeocodeResult resolveCoordinates(String city) {
        // 步骤 1：调用 Open-Meteo 地理编码接口，使用 identity 编码规避部分环境压缩流异常。
        HttpResponse response = HttpRequest.get(geocodingEndpoint())
            // 部分网络环境下自动压缩解码会返回损坏流，显式禁用压缩可规避 ZipException。
            .header("Accept-Encoding", "identity")
            .form("name", city)
            .form("count", 1)
            .form("language", "zh")
            .form("format", "json")
            .timeout(5000)
            .execute();
        // 步骤 2：非 200 响应视为外部服务失败，由上层统一降级为中文提示。
        if (response.getStatus() != HttpStatus.HTTP_OK) {
            throw new IllegalStateException(ErrorMessageCatalog.WEATHER_GEOCODING_REQUEST_FAILED);
        }
        // 步骤 3：解析首个城市匹配结果；没有结果时抛业务可识别错误。
        JSONObject body = JSONUtil.parseObj(response.body());
        JSONArray results = body.getJSONArray("results");
        if (results == null || results.isEmpty()) {
            throw new IllegalStateException(ErrorMessageCatalog.WEATHER_COORDINATE_NOT_FOUND);
        }
        // 步骤 4：组装标准展示名和完整名称，元数据中保留两者方便排障。
        JSONObject first = results.getJSONObject(0);
        double latitude = first.getDouble("latitude", 0D);
        double longitude = first.getDouble("longitude", 0D);
        String name = first.getStr("name", city);
        String admin1 = first.getStr("admin1", "");
        String country = first.getStr("country", "");
        String fullName = StrUtil.join(" ", List.of(name, admin1, country).stream().filter(StrUtil::isNotBlank).toList());
        return new GeocodeResult(latitude, longitude, name, fullName);
    }

    /**
     * 调用 Open-Meteo 预报接口并组装当前天气与日级预报。
     *
     * @param latitude 纬度。
     * @param longitude 经度。
     * @param days 预报天数。
     * @return 预报结果。
     */
    private ForecastResult queryForecast(double latitude, double longitude, int days) {
        // 步骤 1：预报天数限制在 1 到 7 天，避免外部接口请求超出工具支持范围。
        int forecastDays = Math.min(Math.max(days, 1), 7);
        // 步骤 2：一次请求同时获取当前天气和日级预报，减少外部 API 调用次数。
        HttpResponse response = HttpRequest.get(forecastEndpoint())
            // 与地理编码保持一致，统一使用未压缩响应，避免压缩流解码异常导致整链路降级。
            .header("Accept-Encoding", "identity")
            .form("latitude", latitude)
            .form("longitude", longitude)
            .form("current", "temperature_2m,apparent_temperature,weather_code,wind_speed_10m")
            .form("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max")
            .form("forecast_days", forecastDays)
            .form("timezone", "auto")
            .timeout(5000)
            .execute();
        // 步骤 3：非 200 响应统一视为预报接口失败，由 execute 捕获后返回降级结果。
        if (response.getStatus() != HttpStatus.HTTP_OK) {
            throw new IllegalStateException(ErrorMessageCatalog.WEATHER_FORECAST_REQUEST_FAILED);
        }
        // 步骤 4：响应必须同时包含 current 和 daily 节点，否则认为数据结构不可用。
        JSONObject body = JSONUtil.parseObj(response.body());
        JSONObject currentNode = body.getJSONObject("current");
        JSONObject dailyNode = body.getJSONObject("daily");
        if (currentNode == null || dailyNode == null) {
            throw new IllegalStateException(ErrorMessageCatalog.WEATHER_RESPONSE_INVALID);
        }
        // 步骤 5：解析当前天气，缺失数值字段使用 0 兜底，缺失日期由 parseDate 兜底到今天。
        CurrentWeatherData current = new CurrentWeatherData(
            parseDate(currentNode.getStr("time")),
            currentNode.getDouble("temperature_2m", 0D),
            currentNode.getDouble("apparent_temperature", 0D),
            currentNode.getInt("weather_code", 0),
            currentNode.getDouble("wind_speed_10m", 0D),
            body.getInt("utc_offset_seconds", 0)
        );

        // 步骤 6：日级数组字段必须齐全且至少一行，避免生成不完整预报文本。
        JSONArray timeArray = dailyNode.getJSONArray("time");
        JSONArray weatherCodeArray = dailyNode.getJSONArray("weather_code");
        JSONArray maxArray = dailyNode.getJSONArray("temperature_2m_max");
        JSONArray minArray = dailyNode.getJSONArray("temperature_2m_min");
        JSONArray precipitationArray = dailyNode.getJSONArray("precipitation_probability_max");
        JSONArray windArray = dailyNode.getJSONArray("wind_speed_10m_max");
        int length = Objects.requireNonNullElse(timeArray, new JSONArray()).size();
        if (length == 0) {
            throw new IllegalStateException(ErrorMessageCatalog.WEATHER_FORECAST_EMPTY);
        }
        if (weatherCodeArray == null || maxArray == null || minArray == null || precipitationArray == null || windArray == null) {
            throw new IllegalStateException(ErrorMessageCatalog.WEATHER_FORECAST_FIELD_MISSING);
        }
        // 步骤 7：按数组下标组装每日天气对象，保持接口返回顺序。
        List<DailyWeatherData> dailyRows = new java.util.ArrayList<>();
        for (int i = 0; i < length; i++) {
            dailyRows.add(new DailyWeatherData(
                parseDate(timeArray.getStr(i)),
                weatherCodeArray.getInt(i),
                maxArray.getDouble(i),
                minArray.getDouble(i),
                precipitationArray.getInt(i),
                windArray.getDouble(i)
            ));
        }
        // 步骤 8：返回当前天气、完整日级列表和当天行，调用方据此选择当前或预报模板。
        return new ForecastResult(
            body.getStr("timezone", "auto"),
            current,
            dailyRows,
            dailyRows.getFirst()
        );
    }

    /**
     * 把 Open-Meteo 的 WMO 代码映射为中文天气文案。
     *
     * @param code WMO 天气代码。
     * @return 中文文案。
     */
    private String weatherCodeLabel(int code) {
        // 步骤 1：按 Open-Meteo WMO 天气代码映射中文展示文案，未知代码保持可读兜底。
        return switch (code) {
            case 0 -> "晴";
            case 1 -> "基本晴";
            case 2 -> "局部多云";
            case 3 -> "阴";
            case 45, 48 -> "雾";
            case 51, 53, 55 -> "毛毛雨";
            case 56, 57 -> "冻毛毛雨";
            case 61, 63, 65 -> "雨";
            case 66, 67 -> "冻雨";
            case 71, 73, 75 -> "雪";
            case 77 -> "雪粒";
            case 80, 81, 82 -> "阵雨";
            case 85, 86 -> "阵雪";
            case 95 -> "雷暴";
            case 96, 99 -> "雷暴伴冰雹";
            default -> "未知";
        };
    }

    /**
     * 解析 Open-Meteo 日期字符串，兼容日期和日期时间两种返回格式。
     * @param value 日期或日期时间字符串。
     * @return 本地日期；空值兜底为当前日期。
     */
    private LocalDate parseDate(String value) {
        // 步骤 1：外部接口缺少日期时兜底今天，保证天气结果仍可生成。
        if (StrUtil.isBlank(value)) {
            return LocalDate.now();
        }
        // 步骤 2：current.time 可能带 T 分隔的时间部分，日级字段通常只有日期。
        if (value.contains("T")) {
            return LocalDate.parse(value.substring(0, 10));
        }
        return LocalDate.parse(value);
    }

    /**
     * 地理编码结果。
     * @param latitude 纬度，来自 Open-Meteo geocoding 结果。
     * @param longitude 经度，来自 Open-Meteo geocoding 结果。
     * @param displayCity 用于用户展示的城市名，通常是接口返回 name。
     * @param fullName 包含省州和国家的完整名称，用于元数据排障。
     */
    private record GeocodeResult(double latitude, double longitude, String displayCity, String fullName) {
    }

    /**
     * 当前天气数据。
     * @param date 当前天气对应日期，空值已在解析阶段兜底。
     * @param temperature 当前温度，单位摄氏度。
     * @param apparentTemperature 体感温度，单位摄氏度。
     * @param weatherCode WMO 天气代码。
     * @param windSpeed 当前风速，单位 km/h。
     * @param utcOffsetSeconds 接口返回时区偏移秒数，用于结果说明。
     */
    private record CurrentWeatherData(
        LocalDate date,
        double temperature,
        double apparentTemperature,
        int weatherCode,
        double windSpeed,
        int utcOffsetSeconds
    ) {
    }

    /**
     * 日级天气数据。
     * @param date 预报日期。
     * @param weatherCode WMO 天气代码。
     * @param tempMax 当日最高温度，单位摄氏度。
     * @param tempMin 当日最低温度，单位摄氏度。
     * @param precipitationProbabilityMax 当日最高降水概率，单位百分比。
     * @param windSpeedMax 当日最大风速，单位 km/h。
     */
    private record DailyWeatherData(
        LocalDate date,
        int weatherCode,
        double tempMax,
        double tempMin,
        int precipitationProbabilityMax,
        double windSpeedMax
    ) {
    }

    /**
     * 天气预报聚合结果。
     * @param timezone Open-Meteo 返回的时区名称。
     * @param current 当前天气节点。
     * @param dailyRows 日级预报列表，顺序与接口返回一致。
     * @param today 当日预报行，用于当前天气补充最高温、最低温和降水概率。
     */
    private record ForecastResult(
        String timezone,
        CurrentWeatherData current,
        List<DailyWeatherData> dailyRows,
        DailyWeatherData today
    ) {
    }
}

