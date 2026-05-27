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
    private static final String CITY_EXTRACT_TEMPLATE = "weather-city-extract";
    private static final String OPEN_METEO_GEOCODING = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String OPEN_METEO_FORECAST = "https://api.open-meteo.com/v1/forecast";
    private final WeatherQuestionParser weatherQuestionParser = new WeatherQuestionParser();
    @Autowired(required = false)
    private PromptTemplateLoader promptTemplateLoader;
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
        String safeQuestion = StrUtil.blankToDefault(question, "");
        WeatherQuestionParser.WeatherQueryContext context = weatherQuestionParser.parse(safeQuestion, extractCityByAi(safeQuestion));
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
            return new ChatMcpToolResult(toolId(), "请提供城市名称", Map.of("error", true));
        }
        try {
            emitProgress(
                progressListener,
                "resolve-coordinates",
                "正在查询城市坐标",
                Map.of("queryCity", context.city())
            );
            GeocodeResult geocodeResult = resolveCoordinates(context.city());
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
        if (StrUtil.isBlank(question) || promptTemplateLoader == null || aiPromptExecutionService == null) {
            return null;
        }
        try {
            String prompt = promptTemplateLoader.load(CITY_EXTRACT_TEMPLATE);
            String raw = aiPromptExecutionService.complete(prompt, question);
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
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(raw).getStr("city");
        } catch (Exception primaryException) {
            String jsonFragment = ReUtil.get("(?s)\\{.*?\\}", raw, 0);
            if (StrUtil.isBlank(jsonFragment)) {
                return null;
            }
            try {
                return JSONUtil.parseObj(jsonFragment).getStr("city");
            } catch (Exception secondaryException) {
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
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("【%s 未来%d天天气预报】\n\n", city, days));
        int loopDays = Math.min(days, dailyRows.size());
        for (int d = 0; d < loopDays; d++) {
            DailyWeatherData weather = dailyRows.get(d);
            LocalDate date = weather.date();
            String dayLabel = d == 0 ? "今天" : d == 1 ? "明天" : d == 2 ? "后天" : date.format(DateTimeFormatter.ofPattern("MM月dd日"));
            builder.append(String.format("📅 %s（%s）\n", dayLabel, date.format(DateTimeFormatter.ofPattern("MM-dd"))));
            builder.append(String.format("   天气: %s | 温度: %.1f°C ~ %.1f°C\n", weatherCodeLabel(weather.weatherCode()), weather.tempMin(), weather.tempMax()));
            builder.append(String.format("   降水概率: %d%% | 最大风速: %.1f km/h\n\n", weather.precipitationProbabilityMax(), weather.windSpeedMax()));
        }
        if (!dailyRows.isEmpty()) {
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
        HttpResponse response = HttpRequest.get(geocodingEndpoint())
            // 部分网络环境下自动压缩解码会返回损坏流，显式禁用压缩可规避 ZipException。
            .header("Accept-Encoding", "identity")
            .form("name", city)
            .form("count", 1)
            .form("language", "zh")
            .form("format", "json")
            .timeout(5000)
            .execute();
        if (response.getStatus() != HttpStatus.HTTP_OK) {
            throw new IllegalStateException(ErrorMessageCatalog.WEATHER_GEOCODING_REQUEST_FAILED);
        }
        JSONObject body = JSONUtil.parseObj(response.body());
        JSONArray results = body.getJSONArray("results");
        if (results == null || results.isEmpty()) {
            throw new IllegalStateException(ErrorMessageCatalog.WEATHER_COORDINATE_NOT_FOUND);
        }
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
        int forecastDays = Math.min(Math.max(days, 1), 7);
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
        if (response.getStatus() != HttpStatus.HTTP_OK) {
            throw new IllegalStateException(ErrorMessageCatalog.WEATHER_FORECAST_REQUEST_FAILED);
        }
        JSONObject body = JSONUtil.parseObj(response.body());
        JSONObject currentNode = body.getJSONObject("current");
        JSONObject dailyNode = body.getJSONObject("daily");
        if (currentNode == null || dailyNode == null) {
            throw new IllegalStateException(ErrorMessageCatalog.WEATHER_RESPONSE_INVALID);
        }
        CurrentWeatherData current = new CurrentWeatherData(
            parseDate(currentNode.getStr("time")),
            currentNode.getDouble("temperature_2m", 0D),
            currentNode.getDouble("apparent_temperature", 0D),
            currentNode.getInt("weather_code", 0),
            currentNode.getDouble("wind_speed_10m", 0D),
            body.getInt("utc_offset_seconds", 0)
        );

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

    private LocalDate parseDate(String value) {
        if (StrUtil.isBlank(value)) {
            return LocalDate.now();
        }
        if (value.contains("T")) {
            return LocalDate.parse(value.substring(0, 10));
        }
        return LocalDate.parse(value);
    }

    private record GeocodeResult(double latitude, double longitude, String displayCity, String fullName) {
    }

    private record CurrentWeatherData(
        LocalDate date,
        double temperature,
        double apparentTemperature,
        int weatherCode,
        double windSpeed,
        int utcOffsetSeconds
    ) {
    }

    private record DailyWeatherData(
        LocalDate date,
        int weatherCode,
        double tempMax,
        double tempMin,
        int precipitationProbabilityMax,
        double windSpeedMax
    ) {
    }

    private record ForecastResult(
        String timezone,
        CurrentWeatherData current,
        List<DailyWeatherData> dailyRows,
        DailyWeatherData today
    ) {
    }
}

