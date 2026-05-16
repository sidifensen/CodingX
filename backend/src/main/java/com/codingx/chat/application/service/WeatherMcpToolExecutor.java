package com.codingx.chat.application.service;

import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * 天气查询 MCP 工具执行器。
 * <p>
 * 该执行器在后端服务内直接提供天气能力，返回当前天气或未来天气预报文本。
 */
@Component
public class WeatherMcpToolExecutor implements ChatMcpToolExecutor {

    /**
     * 工具唯一标识。
     */
    private static final String TOOL_ID = "weather_query";
    private static final Map<String, double[]> CITY_COORDINATES = new LinkedHashMap<>();
    private static final List<String> WEATHER_TYPES_SPRING = List.of("晴", "多云", "阴", "小雨", "阵雨", "多云转晴");
    private static final List<String> WEATHER_TYPES_SUMMER = List.of("晴", "多云", "雷阵雨", "大雨", "暴雨", "多云转阴");
    private static final List<String> WEATHER_TYPES_AUTUMN = List.of("晴", "多云", "阴", "小雨", "晴转多云", "多云转晴");
    private static final List<String> WEATHER_TYPES_WINTER = List.of("晴", "多云", "阴", "小雪", "中雪", "晴转多云", "雾");

    static {
        CITY_COORDINATES.put("北京", new double[]{39.9, 116.4});
        CITY_COORDINATES.put("上海", new double[]{31.2, 121.5});
        CITY_COORDINATES.put("广州", new double[]{23.1, 113.3});
        CITY_COORDINATES.put("深圳", new double[]{22.5, 114.1});
        CITY_COORDINATES.put("杭州", new double[]{30.3, 120.2});
        CITY_COORDINATES.put("成都", new double[]{30.6, 104.1});
        CITY_COORDINATES.put("武汉", new double[]{30.6, 114.3});
        CITY_COORDINATES.put("南京", new double[]{32.1, 118.8});
        CITY_COORDINATES.put("西安", new double[]{34.3, 108.9});
        CITY_COORDINATES.put("重庆", new double[]{29.6, 106.5});
        CITY_COORDINATES.put("长沙", new double[]{28.2, 112.9});
        CITY_COORDINATES.put("天津", new double[]{39.1, 117.2});
        CITY_COORDINATES.put("苏州", new double[]{31.3, 120.6});
        CITY_COORDINATES.put("郑州", new double[]{34.7, 113.6});
        CITY_COORDINATES.put("青岛", new double[]{36.1, 120.4});
        CITY_COORDINATES.put("大连", new double[]{38.9, 121.6});
        CITY_COORDINATES.put("厦门", new double[]{24.5, 118.1});
        CITY_COORDINATES.put("昆明", new double[]{25.0, 102.7});
        CITY_COORDINATES.put("哈尔滨", new double[]{45.8, 126.5});
        CITY_COORDINATES.put("三亚", new double[]{18.3, 109.5});
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
        if (!CITY_COORDINATES.containsKey(context.city())) {
            return new ChatMcpToolResult(
                toolId(),
                "暂不支持查询该城市，当前支持：" + String.join("、", CITY_COORDINATES.keySet()),
                Map.of("error", true, "city", context.city())
            );
        }
        String content = "forecast".equals(context.queryType())
            ? buildForecastResult(context.city(), context.days())
            : buildCurrentResult(context.city());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("city", context.city());
        metadata.put("queryType", context.queryType());
        metadata.put("days", context.days());
        return new ChatMcpToolResult(toolId(), content, metadata);
    }

    /**
     * 解析天气查询参数。
     *
     * @param question 用户问题。
     * @return 查询上下文。
     */
    private WeatherQueryContext parseQuestion(String question) {
        String city = CITY_COORDINATES.keySet().stream().filter(question::contains).findFirst().orElse(null);
        String queryType = containsAny(question, List.of("预报", "未来", "后天", "明天")) ? "forecast" : "current";
        int days = parseDays(question, 3);
        return new WeatherQueryContext(city, queryType, days);
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
    private String buildCurrentResult(String city) {
        LocalDate today = LocalDate.now();
        WeatherData weather = generateWeatherForDate(city, today);
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("【%s 今日天气】\n\n", city));
        builder.append(String.format("日期: %s\n", today.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))));
        builder.append(String.format("天气: %s\n", weather.weatherType));
        builder.append(String.format("当前温度: %d°C\n", weather.currentTemp));
        builder.append(String.format("最高温度: %d°C\n", weather.highTemp));
        builder.append(String.format("最低温度: %d°C\n", weather.lowTemp));
        builder.append(String.format("相对湿度: %d%%\n", weather.humidity));
        builder.append(String.format("风向: %s\n", weather.windDirection));
        builder.append(String.format("风力: %s\n", weather.windLevel));
        builder.append(String.format("空气质量: %s\n", weather.airQuality));

        if (weather.weatherType.contains("雨") || weather.weatherType.contains("雪")) {
            builder.append("\n提示: 今日有降水，出行请携带雨具。");
        } else if (weather.highTemp >= 35) {
            builder.append("\n提示: 今日高温，注意防暑降温。");
        } else if (weather.lowTemp <= 0) {
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
    private String buildForecastResult(String city, int days) {
        LocalDate today = LocalDate.now();
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("【%s 未来%d天天气预报】\n\n", city, days));
        for (int d = 0; d < days; d++) {
            LocalDate date = today.plusDays(d);
            WeatherData weather = generateWeatherForDate(city, date);
            String dayLabel = d == 0 ? "今天" : d == 1 ? "明天" : d == 2 ? "后天" : date.format(DateTimeFormatter.ofPattern("MM月dd日"));
            builder.append(String.format("📅 %s（%s）\n", dayLabel, date.format(DateTimeFormatter.ofPattern("MM-dd"))));
            builder.append(String.format("   天气: %s | 温度: %d°C ~ %d°C\n", weather.weatherType, weather.lowTemp, weather.highTemp));
            builder.append(String.format("   湿度: %d%% | %s %s\n\n", weather.humidity, weather.windDirection, weather.windLevel));
        }

        WeatherData todayWeather = generateWeatherForDate(city, today);
        WeatherData lastDayWeather = generateWeatherForDate(city, today.plusDays(days - 1));
        int tempTrend = lastDayWeather.highTemp - todayWeather.highTemp;
        if (Math.abs(tempTrend) >= 5) {
            builder.append(String.format(
                "趋势: 未来%d天气温%s，注意%s。",
                days,
                tempTrend > 0 ? "逐渐升高" : "逐渐下降",
                tempTrend > 0 ? "防暑" : "保暖"
            ));
        }
        return builder.toString().trim();
    }

    /**
     * 生成指定城市与日期的模拟天气。
     *
     * @param city 城市。
     * @param date 日期。
     * @return 天气对象。
     */
    private WeatherData generateWeatherForDate(String city, LocalDate date) {
        double[] coordinates = CITY_COORDINATES.get(city);
        double latitude = coordinates[0];
        long seed = date.toEpochDay() * 31 + city.hashCode();
        Random random = new Random(seed);

        int month = date.getMonthValue();
        int season = (month >= 3 && month <= 5) ? 0 : (month >= 6 && month <= 8) ? 1 : (month >= 9 && month <= 11) ? 2 : 3;
        double baseTemp = switch (season) {
            case 0 -> 15 - (latitude - 25) * 0.5;
            case 1 -> 30 - (latitude - 25) * 0.3;
            case 2 -> 18 - (latitude - 25) * 0.5;
            default -> 5 - (latitude - 25) * 0.8;
        };
        int highTemp = (int) (baseTemp + 3 + random.nextInt(6));
        int lowTemp = (int) (baseTemp - 3 - random.nextInt(5));
        int currentTemp = lowTemp + random.nextInt(Math.max(1, highTemp - lowTemp + 1));

        List<String> weatherTypes = switch (season) {
            case 0 -> WEATHER_TYPES_SPRING;
            case 1 -> WEATHER_TYPES_SUMMER;
            case 2 -> WEATHER_TYPES_AUTUMN;
            default -> WEATHER_TYPES_WINTER;
        };
        String weatherType = weatherTypes.get(random.nextInt(weatherTypes.size()));

        int humidity = switch (season) {
            case 1 -> 60 + random.nextInt(30);
            case 3 -> 20 + random.nextInt(30);
            default -> 40 + random.nextInt(30);
        };
        if (weatherType.contains("雨") || weatherType.contains("雪")) {
            humidity = Math.min(95, humidity + 20);
        }

        String[] directions = {"东风", "南风", "西风", "北风", "东南风", "西北风", "东北风", "西南风"};
        String windDirection = directions[random.nextInt(directions.length)];
        int windForce = 1 + random.nextInt(5);
        String windLevel = windForce + "-" + (windForce + 1) + "级";

        int aqiBase = 30 + random.nextInt(120);
        if (latitude > 35) {
            aqiBase += 20;
        }
        String airQuality;
        if (aqiBase <= 50) {
            airQuality = "优";
        } else if (aqiBase <= 100) {
            airQuality = "良";
        } else if (aqiBase <= 150) {
            airQuality = "轻度污染";
        } else {
            airQuality = "中度污染";
        }

        WeatherData weatherData = new WeatherData();
        weatherData.weatherType = weatherType;
        weatherData.currentTemp = currentTemp;
        weatherData.highTemp = highTemp;
        weatherData.lowTemp = lowTemp;
        weatherData.humidity = humidity;
        weatherData.windDirection = windDirection;
        weatherData.windLevel = windLevel;
        weatherData.airQuality = airQuality;
        return weatherData;
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

    /**
     * 天气数据模型。
     */
    private static class WeatherData {
        /**
         * 天气现象。
         */
        String weatherType;
        /**
         * 当前温度。
         */
        int currentTemp;
        /**
         * 最高温度。
         */
        int highTemp;
        /**
         * 最低温度。
         */
        int lowTemp;
        /**
         * 相对湿度。
         */
        int humidity;
        /**
         * 风向。
         */
        String windDirection;
        /**
         * 风力等级。
         */
        String windLevel;
        /**
         * 空气质量等级。
         */
        String airQuality;
    }
}
