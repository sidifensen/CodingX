package com.codingx.mcp.application.service;

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
 * 工单查询 MCP 工具执行器。
 * <p>
 * 该执行器基于自然语言问题生成筛选条件，并在本地模拟工单数据上完成统计与列表输出。
 */
@Component
public class TicketMcpToolExecutor implements ChatMcpToolExecutor {

    /**
     * 工具唯一标识。
     */
    private static final String TOOL_ID = "ticket_query";
    private static final List<String> REGIONS = List.of("华东", "华南", "华北", "西南", "西北");
    private static final List<String> PRODUCTS = List.of("企业版", "专业版", "基础版");
    private static final String STATUS_PENDING = "待处理";
    private static final String STATUS_IN_PROGRESS = "处理中";
    private static final String STATUS_RESOLVED = "已解决";
    private static final String STATUS_CLOSED = "已关闭";
    private static final List<String> STATUSES = List.of(STATUS_PENDING, STATUS_IN_PROGRESS, STATUS_RESOLVED, STATUS_CLOSED);
    private static final List<String> PRIORITIES = List.of("紧急", "高", "中", "低");
    private static final List<String> CATEGORIES = List.of("功能异常", "性能问题", "安装部署", "使用咨询", "数据问题", "权限问题");
    private static final Map<String, List<String>> CUSTOMERS_BY_REGION = Map.of(
        "华东", List.of("腾讯科技", "阿里巴巴", "字节跳动", "网易公司"),
        "华南", List.of("美团点评", "京东集团", "小米科技", "格力电器"),
        "华北", List.of("百度在线", "华为技术", "中兴通讯", "用友网络"),
        "西南", List.of("科大讯飞", "金蝶软件", "三一重工", "中联重科"),
        "西北", List.of("浪潮集团", "东软集团", "美的集团", "海尔智家")
    );
    private static final Map<String, List<String>> ENGINEERS_BY_REGION = Map.of(
        "华东", List.of("工程师A1", "工程师A2"),
        "华南", List.of("工程师B1", "工程师B2"),
        "华北", List.of("工程师C1", "工程师C2"),
        "西南", List.of("工程师D1", "工程师D2"),
        "西北", List.of("工程师E1", "工程师E2")
    );
    private static final List<String> ISSUE_TEMPLATES = List.of(
        "系统登录后页面白屏无法操作",
        "报表导出功能超时失败",
        "用户权限配置不生效",
        "数据同步延迟超过预期",
        "批量导入数据格式校验异常",
        "API接口调用返回500错误",
        "定时任务未按计划执行",
        "搜索功能结果不准确",
        "通知消息无法正常推送",
        "文件上传大小限制配置无效",
        "仪表盘数据展示不一致",
        "多租户数据隔离存在问题",
        "审批流程节点卡住无法流转",
        "移动端页面适配显示异常",
        "数据备份任务执行失败"
    );

    private List<TicketRecord> cachedData;
    private String cacheKey;

    @Override
    public String toolId() {
        return TOOL_ID;
    }

    /**
     * 按问题语义查询工单数据。
     *
     * @param question 用户问题。
     * @return 工具结果。
     */
    @Override
    public ChatMcpToolResult execute(String question) {
        String safeQuestion = StrUtil.blankToDefault(question, "");
        TicketQueryContext context = parseQuestion(safeQuestion);
        List<TicketRecord> allData = getOrGenerateData();
        List<TicketRecord> filtered = filterData(
            allData,
            context.region(),
            context.status(),
            context.priority(),
            context.product(),
            context.customerName()
        );
        String content = switch (context.queryType()) {
            case "list" -> buildListResult(filtered, context.limit());
            case "stats" -> buildStatsResult(filtered);
            default -> buildSummaryResult(filtered, context.region(), context.status(), context.priority(), context.product());
        };
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("queryType", context.queryType());
        metadata.put("region", context.region());
        metadata.put("status", context.status());
        metadata.put("priority", context.priority());
        metadata.put("product", context.product());
        metadata.put("customerName", context.customerName());
        metadata.put("limit", context.limit());
        return new ChatMcpToolResult(toolId(), content, metadata);
    }

    /**
     * 从问题文本中提取工单筛选参数。
     *
     * @param question 用户问题。
     * @return 查询上下文。
     */
    private TicketQueryContext parseQuestion(String question) {
        String region = REGIONS.stream().filter(question::contains).findFirst().orElse(null);
        String status = STATUSES.stream().filter(question::contains).findFirst().orElse(null);
        String priority = PRIORITIES.stream().filter(question::contains).findFirst().orElse(null);
        String product = PRODUCTS.stream().filter(question::contains).findFirst().orElse(null);
        String customerName = null;
        for (List<String> customers : CUSTOMERS_BY_REGION.values()) {
            for (String customer : customers) {
                if (question.contains(customer)) {
                    customerName = customer;
                    break;
                }
            }
            if (customerName != null) {
                break;
            }
        }
        String queryType = parseQueryType(question);
        int limit = parseLimit(question, 10);
        return new TicketQueryContext(region, status, priority, product, customerName, queryType, limit);
    }

    /**
     * 解析查询类型。
     *
     * @param question 用户问题。
     * @return summary/list/stats。
     */
    private String parseQueryType(String question) {
        if (containsAny(question, List.of("列表", "列出", "工单号", "明细"))) {
            return "list";
        }
        if (containsAny(question, List.of("统计", "分析", "解决率", "占比", "分布"))) {
            return "stats";
        }
        return "summary";
    }

    /**
     * 解析输出条数。
     *
     * @param question 问题文本。
     * @param defaultLimit 默认值。
     * @return 输出条数。
     */
    private int parseLimit(String question, int defaultLimit) {
        String digitLimit = ReUtil.get("(?:前|top|TOP)\\s*(\\d+)", question, 1);
        if (StrUtil.isNotBlank(digitLimit)) {
            return normalizeLimit(Integer.parseInt(digitLimit));
        }
        return defaultLimit;
    }

    /**
     * 约束输出条数边界。
     *
     * @param limit 原始值。
     * @return 归一化值。
     */
    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 10;
        }
        return Math.min(limit, 50);
    }

    /**
     * 判断是否命中任一关键词。
     *
     * @param text 文本。
     * @param keywords 关键词集合。
     * @return 命中返回 true。
     */
    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    /**
     * 构建工单概览。
     *
     * @param data 已过滤数据。
     * @param region 地区筛选。
     * @param status 状态筛选。
     * @param priority 优先级筛选。
     * @param product 产品筛选。
     * @return 概览文本。
     */
    private String buildSummaryResult(List<TicketRecord> data, String region, String status,
                                      String priority, String product) {
        int total = data.size();
        long pending = data.stream().filter(ticket -> STATUS_PENDING.equals(ticket.status)).count();
        long inProgress = data.stream().filter(ticket -> STATUS_IN_PROGRESS.equals(ticket.status)).count();
        long resolved = data.stream().filter(ticket -> STATUS_RESOLVED.equals(ticket.status)).count();
        long closed = data.stream().filter(ticket -> STATUS_CLOSED.equals(ticket.status)).count();
        long urgent = data.stream().filter(ticket -> "紧急".equals(ticket.priority)).count();
        long high = data.stream().filter(ticket -> "高".equals(ticket.priority)).count();

        StringBuilder builder = new StringBuilder();
        builder.append("【客户工单汇总概览】\n\n");
        List<String> filters = new ArrayList<>();
        if (region != null) {
            filters.add("地区: " + region);
        }
        if (status != null) {
            filters.add("状态: " + status);
        }
        if (priority != null) {
            filters.add("优先级: " + priority);
        }
        if (product != null) {
            filters.add("产品: " + product);
        }
        if (!filters.isEmpty()) {
            builder.append("筛选条件: ").append(String.join("，", filters)).append("\n\n");
        }
        builder.append(String.format("工单总数: %d 个\n\n", total));
        builder.append("【状态分布】\n");
        builder.append(String.format("  待处理: %d 个\n", pending));
        builder.append(String.format("  处理中: %d 个\n", inProgress));
        builder.append(String.format("  已解决: %d 个\n", resolved));
        builder.append(String.format("  已关闭: %d 个\n\n", closed));

        if (total > 0) {
            double resolveRate = (resolved + closed) * 100.0 / total;
            builder.append(String.format("解决率: %.1f%%\n", resolveRate));
        }
        if (urgent + high > 0) {
            builder.append(String.format("\n⚠ 紧急/高优先级工单: %d 个（紧急 %d，高 %d）\n", urgent + high, urgent, high));
        }
        Map<String, Long> byProduct = data.stream().collect(Collectors.groupingBy(ticket -> ticket.product, Collectors.counting()));
        if (product == null && !byProduct.isEmpty()) {
            builder.append("\n【按产品分布】\n");
            byProduct.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
                .forEach(entry -> builder.append(String.format("  %s: %d 个\n", entry.getKey(), entry.getValue())));
        }
        Map<String, Long> byRegion = data.stream().collect(Collectors.groupingBy(ticket -> ticket.region, Collectors.counting()));
        if (region == null && !byRegion.isEmpty()) {
            builder.append("\n【按地区分布】\n");
            byRegion.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
                .forEach(entry -> builder.append(String.format("  %s: %d 个\n", entry.getKey(), entry.getValue())));
        }
        return builder.toString().trim();
    }

    /**
     * 构建工单列表。
     *
     * @param data 已过滤数据。
     * @param limit 输出上限。
     * @return 列表文本。
     */
    private String buildListResult(List<TicketRecord> data, int limit) {
        List<TicketRecord> sorted = data.stream()
            .sorted((left, right) -> {
                int leftPriority = PRIORITIES.indexOf(left.priority);
                int rightPriority = PRIORITIES.indexOf(right.priority);
                if (leftPriority != rightPriority) {
                    return Integer.compare(leftPriority, rightPriority);
                }
                return right.createDate.compareTo(left.createDate);
            })
            .limit(limit)
            .toList();

        StringBuilder builder = new StringBuilder();
        builder.append(String.format("【工单列表】共 %d 条，显示 %d 条（按优先级排序）\n\n", data.size(), sorted.size()));
        for (int i = 0; i < sorted.size(); i++) {
            TicketRecord ticket = sorted.get(i);
            builder.append(String.format("%d. [%s] %s\n", i + 1, ticket.ticketId, ticket.title));
            builder.append(String.format("   客户: %s | 产品: %s | 地区: %s\n", ticket.customer, ticket.product, ticket.region));
            builder.append(String.format("   优先级: %s | 状态: %s | 分类: %s\n", ticket.priority, ticket.status, ticket.category));
            builder.append(String.format("   处理人: %s | 创建时间: %s\n\n", ticket.engineer, ticket.createDate));
        }
        return builder.toString().trim();
    }

    /**
     * 构建统计分析结果。
     *
     * @param data 已过滤数据。
     * @return 统计文本。
     */
    private String buildStatsResult(List<TicketRecord> data) {
        StringBuilder builder = new StringBuilder();
        builder.append("【工单统计分析】\n\n");
        if (data.isEmpty()) {
            builder.append("暂无工单数据");
            return builder.toString();
        }
        Map<String, Long> byCategory = data.stream().collect(Collectors.groupingBy(ticket -> ticket.category, Collectors.counting()));
        builder.append("【问题分类统计】\n");
        byCategory.entrySet().stream()
            .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
            .forEach(entry -> builder.append(
                String.format("  %s: %d 个 (%.1f%%)\n", entry.getKey(), entry.getValue(), entry.getValue() * 100.0 / data.size())
            ));

        builder.append("\n【各产品解决率】\n");
        Map<String, List<TicketRecord>> byProduct = data.stream().collect(Collectors.groupingBy(ticket -> ticket.product));
        byProduct.forEach((product, tickets) -> {
            long resolvedCount = tickets.stream()
                .filter(ticket -> STATUS_RESOLVED.equals(ticket.status) || STATUS_CLOSED.equals(ticket.status))
                .count();
            builder.append(String.format("  %s: %.1f%% (%d/%d)\n",
                product, resolvedCount * 100.0 / tickets.size(), resolvedCount, tickets.size()));
        });

        builder.append("\n【处理人工单量排名】\n");
        Map<String, Long> byEngineer = data.stream()
            .filter(ticket -> STATUS_PENDING.equals(ticket.status) || STATUS_IN_PROGRESS.equals(ticket.status))
            .collect(Collectors.groupingBy(ticket -> ticket.engineer, Collectors.counting()));
        byEngineer.entrySet().stream()
            .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
            .limit(5)
            .forEach(entry -> builder.append(String.format("  %s: %d 个待处理\n", entry.getKey(), entry.getValue())));
        return builder.toString().trim();
    }

    /**
     * 应用筛选条件。
     *
     * @param data 原始数据。
     * @param region 地区筛选。
     * @param status 状态筛选。
     * @param priority 优先级筛选。
     * @param product 产品筛选。
     * @param customerName 客户筛选。
     * @return 过滤后的数据。
     */
    private List<TicketRecord> filterData(List<TicketRecord> data, String region, String status,
                                          String priority, String product, String customerName) {
        return data.stream()
            .filter(ticket -> region == null || region.equals(ticket.region))
            .filter(ticket -> status == null || status.equals(ticket.status))
            .filter(ticket -> priority == null || priority.equals(ticket.priority))
            .filter(ticket -> product == null || product.equals(ticket.product))
            .filter(ticket -> customerName == null || ticket.customer.contains(customerName))
            .toList();
    }

    /**
     * 获取缓存数据，不存在则重新生成。
     *
     * @return 工单数据。
     */
    private List<TicketRecord> getOrGenerateData() {
        String key = "tickets_" + LocalDate.now();
        if (cachedData != null && key.equals(cacheKey)) {
            return cachedData;
        }
        cachedData = generateMockData();
        cacheKey = key;
        return cachedData;
    }

    /**
     * 生成近 30 天模拟工单数据。
     *
     * @return 模拟工单集合。
     */
    private List<TicketRecord> generateMockData() {
        List<TicketRecord> records = new ArrayList<>();
        LocalDate today = LocalDate.now();
        Random random = new Random(today.toEpochDay());
        int ticketSeq = 1;

        for (int d = 0; d < 30; d++) {
            LocalDate date = today.minusDays(d);
            if (date.getDayOfWeek().getValue() > 5) {
                continue;
            }
            int ticketsPerDay = 2 + random.nextInt(5);
            for (int i = 0; i < ticketsPerDay; i++) {
                TicketRecord ticket = new TicketRecord();
                ticket.ticketId = String.format("TK-%s-%04d", today.format(DateTimeFormatter.ofPattern("yyyyMM")), ticketSeq++);
                ticket.region = REGIONS.get(random.nextInt(REGIONS.size()));
                ticket.customer = CUSTOMERS_BY_REGION.get(ticket.region).get(random.nextInt(4));
                ticket.product = PRODUCTS.get(random.nextInt(PRODUCTS.size()));
                ticket.title = ISSUE_TEMPLATES.get(random.nextInt(ISSUE_TEMPLATES.size()));
                ticket.category = CATEGORIES.get(random.nextInt(CATEGORIES.size()));
                ticket.engineer = ENGINEERS_BY_REGION.get(ticket.region).get(random.nextInt(2));
                ticket.createDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE);

                int priorityWeight = random.nextInt(100);
                if (priorityWeight < 5) {
                    ticket.priority = "紧急";
                } else if (priorityWeight < 20) {
                    ticket.priority = "高";
                } else if (priorityWeight < 60) {
                    ticket.priority = "中";
                } else {
                    ticket.priority = "低";
                }

                if (d > 7) {
                    ticket.status = random.nextInt(100) < 80 ? STATUS_CLOSED : STATUS_RESOLVED;
                } else if (d > 3) {
                    int statusWeight = random.nextInt(100);
                    if (statusWeight < 30) {
                        ticket.status = STATUS_RESOLVED;
                    } else if (statusWeight < 60) {
                        ticket.status = STATUS_CLOSED;
                    } else if (statusWeight < 85) {
                        ticket.status = STATUS_IN_PROGRESS;
                    } else {
                        ticket.status = STATUS_PENDING;
                    }
                } else {
                    int statusWeight = random.nextInt(100);
                    if (statusWeight < 35) {
                        ticket.status = STATUS_PENDING;
                    } else if (statusWeight < 70) {
                        ticket.status = STATUS_IN_PROGRESS;
                    } else if (statusWeight < 90) {
                        ticket.status = STATUS_RESOLVED;
                    } else {
                        ticket.status = STATUS_CLOSED;
                    }
                }
                records.add(ticket);
            }
        }
        return records;
    }

    /**
     * 工单查询上下文。
     *
     * @param region 地区筛选。
     * @param status 状态筛选。
     * @param priority 优先级筛选。
     * @param product 产品筛选。
     * @param customerName 客户筛选。
     * @param queryType 查询类型。
     * @param limit 输出条数。
     */
    private record TicketQueryContext(
        String region,
        String status,
        String priority,
        String product,
        String customerName,
        String queryType,
        int limit
    ) {
    }

    /**
     * 工单记录模型。
     */
    private static class TicketRecord {
        /**
         * 工单编号。
         */
        String ticketId;
        /**
         * 所属地区。
         */
        String region;
        /**
         * 客户名称。
         */
        String customer;
        /**
         * 产品类型。
         */
        String product;
        /**
         * 工单标题。
         */
        String title;
        /**
         * 问题分类。
         */
        String category;
        /**
         * 优先级。
         */
        String priority;
        /**
         * 工单状态。
         */
        String status;
        /**
         * 处理工程师。
         */
        String engineer;
        /**
         * 创建日期。
         */
        String createDate;
    }
}

