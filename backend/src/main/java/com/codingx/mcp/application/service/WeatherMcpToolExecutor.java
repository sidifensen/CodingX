package com.codingx.mcp.application.service;

import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.common.error.ErrorMessageCatalog;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
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
    private static final String OPEN_METEO_GEOCODING = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String OPEN_METEO_FORECAST = "https://api.open-meteo.com/v1/forecast";

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
        String safeQuestion = StrUtil.blankToDefault(question, "");
        WeatherQueryContext context = parseQuestion(safeQuestion);
        if (StrUtil.isBlank(context.city())) {
            return new ChatMcpToolResult(toolId(), "请提供城市名称", Map.of("error", true));
        }
        try {
            GeocodeResult geocodeResult = resolveCoordinates(context.city());
            ForecastResult forecastResult = queryForecast(geocodeResult.latitude(), geocodeResult.longitude(), context.days());
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
            return new ChatMcpToolResult(toolId(), content, metadata);
        } catch (Exception exception) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("city", context.city());
            metadata.put("queryType", context.queryType());
            metadata.put("days", context.days());
            metadata.put("error", true);
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
     * 解析天气查询参数。
     *
     * @param question 用户问题。
     * @return 查询上下文。
     */
    private WeatherQueryContext parseQuestion(String question) {
        String city = extractCity(question);
        String queryType = containsAny(question, List.of("预报", "未来", "后天", "明天")) ? "forecast" : "current";
        int days = parseDays(question, 3);
        return new WeatherQueryContext(city, queryType, days);
    }

    /**
     * 从用户问题中提取城市关键词。
     *
     * @param question 用户问题。
     * @return 城市名；无法识别返回 null。
     */
    private String extractCity(String question) {
        if (StrUtil.isBlank(question)) {
            return null;
        }
        // 城市与时间词分组提取，避免把“北京今天/上海未来三天”误识别为城市名。
        String directCity = ReUtil.get(
            "([\\p{IsHan}]{2,8}?)(?:市|区|县|州|盟)?(?:今天|明天|后天|未来\\d+天|未来[一二三四五六七八九十两]+天)?(?:天气|气温|温度|预报)",
            question,
            1
        );
        if (StrUtil.isNotBlank(directCity)) {
            return directCity;
        }
        String fallbackCity = ReUtil.get("(北京|上海|广州|深圳|杭州|成都|武汉|南京|西安|重庆|长沙|天津|苏州|郑州|青岛|大连|厦门|昆明|哈尔滨|三亚)", question, 1);
        return StrUtil.isBlank(fallbackCity) ? null : fallbackCity.trim();
    }

    /**
     * 从文本中解析预报天数。
     *
     * @param question 问题文本。
     * @param defaultDays 默认天数。
     * @return 预报天数。
     */
    private int parseDays(String question, int defaultDays) {
        String digitDays = ReUtil.get("(\\d+)\\s*天", question, 1);
        if (StrUtil.isNotBlank(digitDays)) {
            return normalizeDays(Integer.parseInt(digitDays));
        }
        String chineseDays = ReUtil.get("([一二三四五六七八九十两]+)天", question, 1);
        if (StrUtil.isNotBlank(chineseDays)) {
            return normalizeDays(parseChineseNumber(chineseDays));
        }
        if (question.contains("明天")) {
            return 2;
        }
        if (question.contains("后天")) {
            return 3;
        }
        return defaultDays;
    }

    /**
     * 中文数字转整数。
     *
     * @param chinese 中文数字。
     * @return 对应整数。
     */
    private int parseChineseNumber(String chinese) {
        return switch (chinese) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> 3;
        };
    }

    /**
     * 天数边界保护。
     *
     * @param days 原始天数。
     * @return 合法天数。
     */
    private int normalizeDays(int days) {
        if (days <= 0) {
            return 3;
        }
        return Math.min(days, 7);
    }

    /**
     * 判断是否命中任一关键词。
     *
     * @param text 文本。
     * @param keywords 关键词。
     * @return 命中返回 true。
     */
    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
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

    /**
     * 天气查询上下文。
     *
     * @param city 城市名。
     * @param queryType 查询类型。
     * @param days 预报天数。
     */
    private record WeatherQueryContext(String city, String queryType, int days) {
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

