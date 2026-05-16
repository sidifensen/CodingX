package com.codingx.chat.application.service;

import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 销售查询 MCP 工具执行器。
 * <p>
 * 该实现直接内嵌在后端服务内，供聊天链路按 toolId 调用，
 * 不依赖独立 mcp-server 进程即可返回稳定的销售统计结果。
 */
@Component
public class SalesMcpToolExecutor implements ChatMcpToolExecutor {

    /**
     * 工具唯一标识。
     */
    private static final String TOOL_ID = "sales_query";
    private static final List<String> REGIONS = List.of("华东", "华南", "华北", "西南", "西北");
    private static final List<String> PRODUCTS = List.of("企业版", "专业版", "基础版");
    private static final List<String> SALES_PEOPLE = List.of(
        "张三", "李四", "王五", "赵六", "钱七", "孙八", "周九", "吴十", "郑冬", "陈春", "林夏", "黄秋", "刘一", "杨二", "马三"
    );
    private static final Map<String, List<String>> SALES_BY_REGION = Map.of(
        "华东", List.of("张三", "李四", "王五"),
        "华南", List.of("赵六", "钱七", "孙八"),
        "华北", List.of("周九", "吴十", "郑冬"),
        "西南", List.of("陈春", "林夏", "黄秋"),
        "西北", List.of("刘一", "杨二", "马三")
    );
    private static final List<String> CUSTOMER_POOL = List.of(
        "腾讯科技", "阿里巴巴", "字节跳动", "美团点评", "京东集团", "百度在线", "网易公司", "小米科技", "华为技术", "中兴通讯",
        "用友网络", "金蝶软件", "浪潮集团", "东软集团", "科大讯飞", "三一重工", "中联重科", "格力电器", "美的集团", "海尔智家"
    );

    private List<SalesRecord> cachedData;
    private String cacheKey;

    @Override
    public String toolId() {
        return TOOL_ID;
    }

    /**
     * 根据自然语言问题提取查询维度并返回销售结果。
     *
     * @param question 用户问题。
     * @return 工具结果。
     */
    @Override
    public ChatMcpToolResult execute(String question) {
        String safeQuestion = StrUtil.blankToDefault(question, "");
        SalesQueryContext context = parseQuestion(safeQuestion);
        List<SalesRecord> allData = getOrGenerateData(context.period());
        List<SalesRecord> filtered = filterData(allData, context.region(), context.product(), context.salesPerson());
        String content = switch (context.queryType()) {
            case "ranking" -> buildRankingResult(filtered, context.region(), context.period(), context.limit());
            case "detail" -> buildDetailResult(filtered, context.region(), context.period(), context.limit());
            case "trend" -> buildTrendResult(filtered, context.region(), context.period());
            default -> buildSummaryResult(filtered, context.region(), context.period(), context.product(), context.salesPerson());
        };
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("queryType", context.queryType());
        metadata.put("period", context.period());
        metadata.put("region", context.region());
        metadata.put("product", context.product());
        metadata.put("salesPerson", context.salesPerson());
        metadata.put("limit", context.limit());
        return new ChatMcpToolResult(toolId(), content, metadata);
    }

    /**
     * 从用户自然语言中提取销售查询参数。
     *
     * @param question 用户问题文本。
     * @return 解析后的查询上下文。
     */
    private SalesQueryContext parseQuestion(String question) {
        String region = REGIONS.stream().filter(question::contains).findFirst().orElse(null);
        String product = PRODUCTS.stream().filter(question::contains).findFirst().orElse(null);
        String salesPerson = SALES_PEOPLE.stream().filter(question::contains).findFirst().orElse(null);
        String period = parsePeriod(question);
        String queryType = parseQueryType(question);
        int limit = parseLimit(question, 10);
        return new SalesQueryContext(region, period, product, salesPerson, queryType, limit);
    }

    /**
     * 解析时间范围，不匹配时默认本月。
     *
     * @param question 用户问题。
     * @return 时间范围关键词。
     */
    private String parsePeriod(String question) {
        if (question.contains("上季度")) {
            return "上季度";
        }
        if (question.contains("本季度")) {
            return "本季度";
        }
        if (question.contains("上月")) {
            return "上月";
        }
        if (question.contains("本年") || question.contains("今年")) {
            return "本年";
        }
        return "本月";
    }

    /**
     * 解析查询类型。
     *
     * @param question 用户问题。
     * @return summary/ranking/detail/trend。
     */
    private String parseQueryType(String question) {
        if (containsAny(question, List.of("排名", "排行", "前", "top", "TOP"))) {
            return "ranking";
        }
        if (containsAny(question, List.of("趋势", "走势", "环比", "同比"))) {
            return "trend";
        }
        if (containsAny(question, List.of("明细", "列表", "客户", "订单"))) {
            return "detail";
        }
        return "summary";
    }

    /**
     * 解析返回数量上限。
     *
     * @param question    用户问题。
     * @param defaultLimit 默认条数。
     * @return 条数上限，最小 1，最大 50。
     */
    private int parseLimit(String question, int defaultLimit) {
        String digitLimit = ReUtil.get("(?:前|top|TOP)\\s*(\\d+)", question, 1);
        if (StrUtil.isNotBlank(digitLimit)) {
            return normalizeLimit(Integer.parseInt(digitLimit));
        }
        String chineseLimit = ReUtil.get("(?:前)([一二三四五六七八九十两]+)", question, 1);
        if (StrUtil.isNotBlank(chineseLimit)) {
            return normalizeLimit(parseChineseNumber(chineseLimit));
        }
        return defaultLimit;
    }

    /**
     * 将中文小数字转为整数。
     *
     * @param chinese 中文数字文本。
     * @return 对应整数，无法识别时返回 10。
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
            default -> 10;
        };
    }

    /**
     * 限制查询条数边界，防止超大输出。
     *
     * @param limit 原始条数。
     * @return 归一化后的条数。
     */
    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 10;
        }
        return Math.min(limit, 50);
    }

    /**
     * 判断文本是否包含任一关键词。
     *
     * @param text 目标文本。
     * @param keywords 关键词集合。
     * @return 命中时返回 true。
     */
    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    /**
     * 构建汇总结果。
     *
     * @param data 已过滤数据。
     * @param region 地区筛选。
     * @param period 时间范围。
     * @param product 产品筛选。
     * @param salesPerson 销售筛选。
     * @return 汇总文本。
     */
    private String buildSummaryResult(List<SalesRecord> data, String region, String period,
                                      String product, String salesPerson) {
        double totalAmount = data.stream().mapToDouble(record -> record.amount).sum();
        int orderCount = data.size();
        double avgAmount = orderCount > 0 ? totalAmount / orderCount : 0;
        Map<String, Double> byProduct = data.stream()
            .collect(Collectors.groupingBy(record -> record.product, Collectors.summingDouble(record -> record.amount)));
        Map<String, Double> byRegion = data.stream()
            .collect(Collectors.groupingBy(record -> record.region, Collectors.summingDouble(record -> record.amount)));

        StringBuilder builder = new StringBuilder();
        builder.append("【").append(period).append(" 销售数据汇总】\n\n");
        List<String> filters = new ArrayList<>();
        if (region != null) {
            filters.add("地区: " + region);
        }
        if (product != null) {
            filters.add("产品: " + product);
        }
        if (salesPerson != null) {
            filters.add("销售: " + salesPerson);
        }
        if (!filters.isEmpty()) {
            builder.append("筛选条件: ").append(String.join("，", filters)).append("\n\n");
        }

        builder.append(String.format("总销售额: ¥%.2f 万\n", totalAmount));
        builder.append(String.format("成交订单: %d 笔\n", orderCount));
        builder.append(String.format("平均单价: ¥%.2f 万\n", avgAmount));

        if (product == null && !byProduct.isEmpty()) {
            builder.append("\n【按产品分布】\n");
            byProduct.entrySet().stream().sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .forEach(entry -> builder.append(
                    String.format("  %s: ¥%.2f 万 (%.1f%%)\n", entry.getKey(), entry.getValue(), ratio(entry.getValue(), totalAmount))
                ));
        }
        if (region == null && !byRegion.isEmpty()) {
            builder.append("\n【按地区分布】\n");
            byRegion.entrySet().stream().sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .forEach(entry -> builder.append(
                    String.format("  %s: ¥%.2f 万 (%.1f%%)\n", entry.getKey(), entry.getValue(), ratio(entry.getValue(), totalAmount))
                ));
        }
        return builder.toString().trim();
    }

    /**
     * 构建销售排名结果。
     *
     * @param data 已过滤数据。
     * @param region 地区筛选。
     * @param period 时间范围。
     * @param limit 返回上限。
     * @return 排名文本。
     */
    private String buildRankingResult(List<SalesRecord> data, String region, String period, int limit) {
        Map<String, Double> bySales = data.stream()
            .collect(Collectors.groupingBy(record -> record.salesPerson, Collectors.summingDouble(record -> record.amount)));
        List<Map.Entry<String, Double>> ranking = bySales.entrySet().stream()
            .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
            .limit(limit)
            .toList();
        StringBuilder builder = new StringBuilder();
        builder.append("【").append(period);
        if (region != null) {
            builder.append(" ").append(region);
        }
        builder.append(" 销售排名】\n\n");
        if (ranking.isEmpty()) {
            builder.append("暂无销售数据");
        } else {
            for (int i = 0; i < ranking.size(); i++) {
                Map.Entry<String, Double> entry = ranking.get(i);
                builder.append(String.format("第%d名: %s - ¥%.2f 万\n", i + 1, entry.getKey(), entry.getValue()));
            }
        }
        return builder.toString().trim();
    }

    /**
     * 构建销售明细结果。
     *
     * @param data 已过滤数据。
     * @param region 地区筛选。
     * @param period 时间范围。
     * @param limit 返回上限。
     * @return 明细文本。
     */
    private String buildDetailResult(List<SalesRecord> data, String region, String period, int limit) {
        List<SalesRecord> topRecords = data.stream()
            .sorted((left, right) -> Double.compare(right.amount, left.amount))
            .limit(limit)
            .toList();
        StringBuilder builder = new StringBuilder();
        builder.append("【").append(period);
        if (region != null) {
            builder.append(" ").append(region);
        }
        builder.append(" 销售明细】\n\n");
        builder.append(String.format("共 %d 条记录，显示金额最高的 %d 条：\n\n", data.size(), topRecords.size()));
        for (int i = 0; i < topRecords.size(); i++) {
            SalesRecord record = topRecords.get(i);
            builder.append(String.format("%d. %s\n", i + 1, record.customer));
            builder.append(String.format("   产品: %s | 金额: ¥%.2f 万\n", record.product, record.amount));
            builder.append(String.format("   销售: %s | 地区: %s | 日期: %s\n\n", record.salesPerson, record.region, record.date));
        }
        return builder.toString().trim();
    }

    /**
     * 构建销售趋势结果。
     *
     * @param data 已过滤数据。
     * @param region 地区筛选。
     * @param period 时间范围。
     * @return 趋势文本。
     */
    private String buildTrendResult(List<SalesRecord> data, String region, String period) {
        Map<String, Double> byWeek = data.stream()
            .collect(Collectors.groupingBy(
                record -> "第" + ((LocalDate.parse(record.date).getDayOfMonth() - 1) / 7 + 1) + "周",
                Collectors.summingDouble(record -> record.amount)
            ));
        StringBuilder builder = new StringBuilder();
        builder.append("【").append(period);
        if (region != null) {
            builder.append(" ").append(region);
        }
        builder.append(" 销售趋势】\n\n");
        if (byWeek.isEmpty()) {
            builder.append("暂无数据");
        } else {
            byWeek.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> builder.append(String.format("%s: ¥%.2f 万\n", entry.getKey(), entry.getValue())));
        }
        return builder.toString().trim();
    }

    /**
     * 计算百分比，自动处理分母为 0 的情况。
     *
     * @param numerator 分子。
     * @param denominator 分母。
     * @return 百分比数值。
     */
    private double ratio(double numerator, double denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return numerator / denominator * 100;
    }

    /**
     * 应用筛选条件。
     *
     * @param data 全量数据。
     * @param region 地区筛选。
     * @param product 产品筛选。
     * @param salesPerson 销售筛选。
     * @return 过滤后的数据。
     */
    private List<SalesRecord> filterData(List<SalesRecord> data, String region, String product, String salesPerson) {
        return data.stream()
            .filter(record -> region == null || region.equals(record.region))
            .filter(record -> product == null || product.equals(record.product))
            .filter(record -> salesPerson == null || salesPerson.equals(record.salesPerson))
            .toList();
    }

    /**
     * 获取当前时间段缓存数据。
     *
     * @param period 时间范围。
     * @return 对应数据。
     */
    private List<SalesRecord> getOrGenerateData(String period) {
        String key = period + "_" + LocalDate.now();
        if (cachedData != null && key.equals(cacheKey)) {
            return cachedData;
        }
        LocalDate[] dateRange = getDateRange(period);
        cachedData = generateMockData(dateRange[0], dateRange[1]);
        cacheKey = key;
        return cachedData;
    }

    /**
     * 将时间关键词映射到起止日期。
     *
     * @param period 时间关键词。
     * @return 起止日期数组。
     */
    private LocalDate[] getDateRange(String period) {
        LocalDate now = LocalDate.now();
        return switch (period) {
            case "上月" -> new LocalDate[]{now.minusMonths(1).withDayOfMonth(1), now.withDayOfMonth(1).minusDays(1)};
            case "本季度" -> {
                int quarter = (now.getMonthValue() - 1) / 3;
                yield new LocalDate[]{now.withMonth(quarter * 3 + 1).withDayOfMonth(1), now};
            }
            case "上季度" -> {
                int currentQuarterStartMonth = ((now.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate end = now.withMonth(currentQuarterStartMonth).withDayOfMonth(1).minusDays(1);
                int previousQuarterStartMonth = ((end.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate start = end.withMonth(previousQuarterStartMonth).withDayOfMonth(1);
                yield new LocalDate[]{start, end};
            }
            case "本年" -> new LocalDate[]{now.withDayOfYear(1), now};
            default -> new LocalDate[]{now.withDayOfMonth(1), now};
        };
    }

    /**
     * 生成模拟销售明细。
     *
     * @param start 起始日期。
     * @param end 结束日期。
     * @return 销售记录集合。
     */
    private List<SalesRecord> generateMockData(LocalDate start, LocalDate end) {
        List<SalesRecord> records = new ArrayList<>();
        Random random = new Random(start.toEpochDay());
        long days = end.toEpochDay() - start.toEpochDay() + 1;
        for (long d = 0; d < days; d++) {
            LocalDate date = start.plusDays(d);
            if (date.getDayOfWeek().getValue() > 5) {
                continue;
            }
            int ordersPerDay = 3 + random.nextInt(6);
            for (int i = 0; i < ordersPerDay; i++) {
                SalesRecord record = new SalesRecord();
                record.region = REGIONS.get(random.nextInt(REGIONS.size()));
                record.salesPerson = SALES_BY_REGION.get(record.region).get(random.nextInt(3));
                record.product = PRODUCTS.get(random.nextInt(PRODUCTS.size()));
                record.customer = CUSTOMER_POOL.get(random.nextInt(CUSTOMER_POOL.size())) + date.getDayOfMonth();
                record.amount = switch (record.product) {
                    case "企业版" -> 50 + random.nextDouble() * 150;
                    case "专业版" -> 10 + random.nextDouble() * 40;
                    default -> 1 + random.nextDouble() * 9;
                };
                record.amount = Math.round(record.amount * 100) / 100.0;
                record.date = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
                records.add(record);
            }
        }
        return records;
    }

    /**
     * 销售查询上下文。
     *
     * @param region 地区筛选。
     * @param period 时间范围。
     * @param product 产品筛选。
     * @param salesPerson 销售筛选。
     * @param queryType 查询类型。
     * @param limit 返回条数。
     */
    private record SalesQueryContext(
        String region,
        String period,
        String product,
        String salesPerson,
        String queryType,
        int limit
    ) {
    }

    /**
     * 销售记录模型。
     */
    private static class SalesRecord {
        /**
         * 所属地区。
         */
        String region;
        /**
         * 销售人员。
         */
        String salesPerson;
        /**
         * 产品类型。
         */
        String product;
        /**
         * 客户名称。
         */
        String customer;
        /**
         * 销售金额（万元）。
         */
        double amount;
        /**
         * 成交日期。
         */
        String date;
    }
}
