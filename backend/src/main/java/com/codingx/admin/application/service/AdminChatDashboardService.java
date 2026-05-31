package com.codingx.admin.application.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import com.codingx.chat.domain.repository.ChatQueryTermMappingRepository;
import com.codingx.chat.domain.repository.ChatSampleQuestionRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatConversationDO;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatMessageDO;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatTraceRunDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatConversationMapper;
import com.codingx.chat.infrastructure.persistence.mapper.ChatMessageMapper;
import com.codingx.chat.infrastructure.persistence.mapper.ChatTraceRunMapper;
import com.codingx.expert.domain.repository.ChatExpertRepository;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.tool.domain.repository.ChatToolRepository;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.persistence.mapper.WorkspaceMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供聊天运行时后台 Dashboard 聚合数据。
 */
@Service
@RequiredArgsConstructor
public class AdminChatDashboardService {

    /**
     * 分桶键格式化器，保证同一窗口内 bucketMap 的键稳定可比较。
     */
    private static final DateTimeFormatter BUCKET_KEY_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 24 小时窗口的横轴标签格式。
     */
    private static final DateTimeFormatter HOUR_LABEL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 7 天和 30 天窗口的横轴标签格式。
     */
    private static final DateTimeFormatter DAY_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    /**
     * 会话 Mapper，用于统计窗口内新增会话。
     */
    private final ChatConversationMapper chatConversationMapper;

    /**
     * 消息 Mapper，用于统计窗口内新增消息。
     */
    private final ChatMessageMapper chatMessageMapper;

    /**
     * Trace 运行记录 Mapper，用于统计链路数量、状态和耗时。
     */
    private final ChatTraceRunMapper chatTraceRunMapper;

    /**
     * 工作空间 Mapper，用于统计当前有效工作空间总量。
     */
    private final WorkspaceMapper workspaceMapper;

    /**
     * 意图节点仓储，用于统计当前意图配置资产数量。
     */
    private final ChatIntentNodeRepository chatIntentNodeRepository;

    /**
     * MCP 配置仓储，用于统计当前 MCP 资产数量。
     */
    private final ChatMcpRepository chatMcpRepository;

    /**
     * 查询词映射仓储，用于统计当前关键词映射资产数量。
     */
    private final ChatQueryTermMappingRepository chatQueryTermMappingRepository;

    /**
     * 示例问题仓储，用于统计当前启用示例问题数量。
     */
    private final ChatSampleQuestionRepository chatSampleQuestionRepository;

    /**
     * 技能仓储，用于统计当前技能资产数量。
     */
    private final ChatSkillRepository chatSkillRepository;

    /**
     * 工具仓储，用于统计当前工具资产数量。
     */
    private final ChatToolRepository chatToolRepository;

    /**
     * 专家仓储，用于统计当前专家资产数量。
     */
    private final ChatExpertRepository chatExpertRepository;

    /**
     * 返回指定窗口的 Dashboard 聚合视图。
     * @param rawWindow 时间窗口。
     * @return 聚合视图。
     */
    public AdminChatDashboardView getDashboard(String rawWindow) {
        // 步骤 1：解析前端传入的窗口编码，非法值回退到 24 小时窗口。
        DashboardWindow window = DashboardWindow.from(rawWindow);
        // 步骤 2：按窗口对齐结束边界和起始边界，确保当前未结束分桶也能展示。
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime rangeEndExclusive = window.nextBucketStart(now);
        LocalDateTime rangeStartInclusive = window.firstBucketStart(rangeEndExclusive);

        // 步骤 3：分别读取会话、消息和 Trace 数据，并在应用层二次过滤时间边界。
        List<ChatConversationDO> conversations = filterConversations(loadConversations(rangeStartInclusive), rangeStartInclusive, rangeEndExclusive);
        List<ChatMessageDO> messages = filterMessages(loadMessages(rangeStartInclusive), rangeStartInclusive, rangeEndExclusive);
        List<ChatTraceRunDO> traces = filterTraces(loadTraces(rangeStartInclusive), rangeStartInclusive, rangeEndExclusive);

        // 步骤 4：基于窗口内运行数据构造 KPI、资源资产、健康摘要和趋势分桶。
        AdminChatDashboardKpiView kpis = buildKpis(conversations, messages, traces);
        AdminChatDashboardResourceView resources = new AdminChatDashboardResourceView(
            chatSkillRepository.findAll().size(),
            chatToolRepository.findAll().size(),
            chatExpertRepository.findAll().size(),
            chatMcpRepository.findAll().size(),
            chatIntentNodeRepository.findAllNodes().size(),
            chatQueryTermMappingRepository.findAllMappings().size(),
            chatSampleQuestionRepository.findEnabledQuestions().size()
        );
        AdminChatDashboardPerformanceView performance = buildPerformance(traces);

        // 步骤 5：返回前端 Dashboard 可直接消费的聚合视图。
        return new AdminChatDashboardView(
            window.value(),
            now.truncatedTo(ChronoUnit.SECONDS).toString(),
            kpis,
            resources,
            performance,
            buildTrendBuckets(window, rangeEndExclusive, conversations, messages, traces)
        );
    }

    /**
     * 按窗口统计 Dashboard 核心指标。
     */
    private AdminChatDashboardKpiView buildKpis(
        List<ChatConversationDO> conversations,
        List<ChatMessageDO> messages,
        List<ChatTraceRunDO> traces
    ) {
        // 步骤 1：会话创建人和 Trace 用户都算作窗口内活跃用户。
        Set<Long> activeUserIds = new HashSet<>();
        for (ChatConversationDO conversation : conversations) {
            if (conversation.getCreatedBy() != null) {
                activeUserIds.add(conversation.getCreatedBy());
            }
        }
        for (ChatTraceRunDO trace : traces) {
            if (trace.getUserId() != null) {
                activeUserIds.add(trace.getUserId());
            }
        }
        // 步骤 2：工作空间数量是当前有效总量，其他 KPI 都来自窗口内数据。
        return new AdminChatDashboardKpiView(
            activeUserIds.size(),
            conversations.size(),
            messages.size(),
            countActiveWorkspaces(),
            traces.size(),
            (int) traces.stream().filter(this::isRunningTrace).count()
        );
    }

    /**
     * 基于链路状态计算运行健康摘要。
     */
    private AdminChatDashboardPerformanceView buildPerformance(List<ChatTraceRunDO> traces) {
        // 步骤 1：按 Trace 终态和运行态计算成功、失败、运行中数量。
        int total = traces.size();
        int successCount = (int) traces.stream().filter(this::isSuccessTrace).count();
        int failureCount = (int) traces.stream().filter(this::isFailureTrace).count();
        int runningCount = (int) traces.stream().filter(this::isRunningTrace).count();
        // 步骤 2：只统计正数耗时，避免未结束或脏数据拉低平均值和 P95。
        List<Long> durations = traces.stream()
            .map(ChatTraceRunDO::getDurationMs)
            .filter(duration -> duration != null && duration > 0)
            .sorted()
            .toList();
        long avgDuration = durations.isEmpty()
            ? 0L
            : Math.round(durations.stream().mapToLong(Long::longValue).average().orElse(0D));
        long p95Duration = durations.isEmpty() ? 0L : durations.get(Math.max(0, (int) Math.ceil(durations.size() * 0.95D) - 1));
        // 步骤 3：百分比在无数据时返回 0，避免前端展示 NaN。
        return new AdminChatDashboardPerformanceView(
            toPercent(successCount, total),
            toPercent(failureCount, total),
            toPercent(runningCount, total),
            avgDuration,
            p95Duration
        );
    }

    /**
     * 构造前端图表直接消费的时间分桶数组。
     */
    private List<AdminChatDashboardTrendBucketView> buildTrendBuckets(
        DashboardWindow window,
        LocalDateTime rangeEndExclusive,
        List<ChatConversationDO> conversations,
        List<ChatMessageDO> messages,
        List<ChatTraceRunDO> traces
    ) {
        // 步骤 1：预先创建完整分桶，确保无数据的时间点也能返回 0 值。
        Map<String, BucketAccumulator> bucketMap = new HashMap<>();
        List<LocalDateTime> bucketStarts = new ArrayList<>();
        LocalDateTime cursor = window.firstBucketStart(rangeEndExclusive);
        for (int index = 0; index < window.bucketCount(); index += 1) {
            bucketStarts.add(cursor);
            bucketMap.put(bucketKey(cursor), new BucketAccumulator());
            cursor = window.increment(cursor);
        }

        // 步骤 2：会话按创建时间进入对应分桶，并贡献活跃用户。
        for (ChatConversationDO conversation : conversations) {
            LocalDateTime bucketStart = window.alignBucketStart(conversation.getCreatedAt());
            BucketAccumulator bucket = bucketMap.get(bucketKey(bucketStart));
            if (bucket == null) {
                continue;
            }
            bucket.conversationCount += 1;
            if (conversation.getCreatedBy() != null) {
                bucket.activeUserIds.add(conversation.getCreatedBy());
            }
        }
        // 步骤 3：消息按创建时间进入对应分桶，仅贡献消息数。
        for (ChatMessageDO message : messages) {
            LocalDateTime bucketStart = window.alignBucketStart(message.getCreatedAt());
            BucketAccumulator bucket = bucketMap.get(bucketKey(bucketStart));
            if (bucket != null) {
                bucket.messageCount += 1;
            }
        }
        // 步骤 4：Trace 优先按 startedAt 分桶，缺失时按 createdAt 分桶，并累计状态与耗时。
        for (ChatTraceRunDO trace : traces) {
            LocalDateTime timePoint = trace.getStartedAt() != null ? trace.getStartedAt() : trace.getCreatedAt();
            LocalDateTime bucketStart = window.alignBucketStart(timePoint);
            BucketAccumulator bucket = bucketMap.get(bucketKey(bucketStart));
            if (bucket == null) {
                continue;
            }
            bucket.traceCount += 1;
            if (trace.getUserId() != null) {
                bucket.activeUserIds.add(trace.getUserId());
            }
            if (isSuccessTrace(trace)) {
                bucket.successCount += 1;
            }
            if (isFailureTrace(trace)) {
                bucket.failedCount += 1;
            }
            if (trace.getDurationMs() != null && trace.getDurationMs() > 0) {
                bucket.durationValues.add(trace.getDurationMs());
            }
        }

        // 步骤 5：按分桶起点顺序生成趋势视图，空分桶保持 0 值。
        return bucketStarts.stream()
            .map(bucketStart -> {
                BucketAccumulator bucket = bucketMap.get(bucketKey(bucketStart));
                long avgDuration = bucket == null || bucket.durationValues.isEmpty()
                    ? 0L
                    : Math.round(bucket.durationValues.stream().mapToLong(Long::longValue).average().orElse(0D));
                return new AdminChatDashboardTrendBucketView(
                    window.formatLabel(bucketStart),
                    bucketStart.truncatedTo(ChronoUnit.SECONDS).toString(),
                    bucket == null ? 0 : bucket.conversationCount,
                    bucket == null ? 0 : bucket.messageCount,
                    bucket == null ? 0 : bucket.activeUserIds.size(),
                    bucket == null ? 0 : bucket.traceCount,
                    bucket == null ? 0 : bucket.successCount,
                    bucket == null ? 0 : bucket.failedCount,
                    avgDuration
                );
            })
            .toList();
    }

    /**
     * 只读取窗口起点之后的会话，后续仍会在应用层做一次时间过滤，避免测试桩或数据库时间边界造成误差。
     */
    private List<ChatConversationDO> loadConversations(LocalDateTime rangeStartInclusive) {
        return chatConversationMapper.selectList(new LambdaQueryWrapper<ChatConversationDO>()
            .eq(ChatConversationDO::getDeleted, 0)
            .ge(ChatConversationDO::getCreatedAt, rangeStartInclusive)
            .orderByAsc(ChatConversationDO::getCreatedAt));
    }

    private List<ChatMessageDO> loadMessages(LocalDateTime rangeStartInclusive) {
        // 步骤 1：只读取窗口起点之后的未删除消息，减少后续内存过滤数据量。
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageDO>()
            .eq(ChatMessageDO::getDeleted, 0)
            .ge(ChatMessageDO::getCreatedAt, rangeStartInclusive)
            .orderByAsc(ChatMessageDO::getCreatedAt));
    }

    private List<ChatTraceRunDO> loadTraces(LocalDateTime rangeStartInclusive) {
        // 步骤 1：只读取窗口起点之后的未删除 Trace，后续再按 startedAt/createdAt 二次过滤。
        return chatTraceRunMapper.selectList(new LambdaQueryWrapper<ChatTraceRunDO>()
            .eq(ChatTraceRunDO::getDeleted, 0)
            .ge(ChatTraceRunDO::getCreatedAt, rangeStartInclusive)
            .orderByAsc(ChatTraceRunDO::getCreatedAt));
    }

    private List<ChatConversationDO> filterConversations(
        List<ChatConversationDO> conversations,
        LocalDateTime rangeStartInclusive,
        LocalDateTime rangeEndExclusive
    ) {
        return conversations.stream()
            .filter(conversation -> isWithinRange(conversation.getCreatedAt(), rangeStartInclusive, rangeEndExclusive))
            .toList();
    }

    private List<ChatMessageDO> filterMessages(
        List<ChatMessageDO> messages,
        LocalDateTime rangeStartInclusive,
        LocalDateTime rangeEndExclusive
    ) {
        return messages.stream()
            .filter(message -> isWithinRange(message.getCreatedAt(), rangeStartInclusive, rangeEndExclusive))
            .toList();
    }

    private List<ChatTraceRunDO> filterTraces(
        List<ChatTraceRunDO> traces,
        LocalDateTime rangeStartInclusive,
        LocalDateTime rangeEndExclusive
    ) {
        return traces.stream()
            .filter(trace -> {
                LocalDateTime timePoint = trace.getStartedAt() != null ? trace.getStartedAt() : trace.getCreatedAt();
                return isWithinRange(timePoint, rangeStartInclusive, rangeEndExclusive);
            })
            .sorted(Comparator.comparing(trace -> trace.getStartedAt() != null ? trace.getStartedAt() : trace.getCreatedAt()))
            .toList();
    }

    private boolean isWithinRange(LocalDateTime value, LocalDateTime rangeStartInclusive, LocalDateTime rangeEndExclusive) {
        if (value == null) {
            return false;
        }
        return !value.isBefore(rangeStartInclusive) && value.isBefore(rangeEndExclusive);
    }

    private boolean isRunningTrace(ChatTraceRun trace) {
        return trace != null && isRunningStatus(trace.getStatus());
    }

    private boolean isRunningTrace(ChatTraceRunDO trace) {
        return trace != null && isRunningStatus(trace.getStatus());
    }

    private boolean isSuccessTrace(ChatTraceRunDO trace) {
        return trace != null && StrUtil.equalsAnyIgnoreCase(trace.getStatus(), "SUCCESS", "SUCCEEDED", "COMPLETED");
    }

    private boolean isFailureTrace(ChatTraceRunDO trace) {
        return trace != null && StrUtil.equalsAnyIgnoreCase(trace.getStatus(), "ERROR", "FAILED");
    }

    private boolean isRunningStatus(String status) {
        return StrUtil.equalsAnyIgnoreCase(status, "RUNNING", "WAITING", "ACQUIRED");
    }

    private int countActiveWorkspaces() {
        Long count = workspaceMapper.selectCount(new LambdaQueryWrapper<WorkspaceDO>()
            .eq(WorkspaceDO::getDeleted, 0));
        return count == null ? 0 : count.intValue();
    }

    private double toPercent(int value, int total) {
        if (total <= 0) {
            return 0D;
        }
        return BigDecimal.valueOf((double) value * 100D / total)
            .setScale(1, RoundingMode.HALF_UP)
            .doubleValue();
    }

    private String bucketKey(LocalDateTime bucketStart) {
        return BUCKET_KEY_FORMATTER.format(bucketStart);
    }

    /**
     * Dashboard 时间窗口定义。
     */
    enum DashboardWindow {
        LAST_24_HOURS("24h", 24, ChronoUnit.HOURS),
        LAST_7_DAYS("7d", 7, ChronoUnit.DAYS),
        LAST_30_DAYS("30d", 30, ChronoUnit.DAYS);

        /**
         * 前端传入和响应返回的窗口编码。
         */
        private final String value;

        /**
         * 窗口内分桶数量。
         */
        private final int bucketCount;

        /**
         * 分桶单位，小时窗口按小时，天窗口按天。
         */
        private final ChronoUnit unit;

        DashboardWindow(String value, int bucketCount, ChronoUnit unit) {
            this.value = value;
            this.bucketCount = bucketCount;
            this.unit = unit;
        }

        static DashboardWindow from(String rawWindow) {
            // 步骤 1：空值和非法值都回退到 24 小时窗口，保证 Dashboard 默认可用。
            String normalized = StrUtil.blankToDefault(rawWindow, "24h").trim().toLowerCase(Locale.ROOT);
            for (DashboardWindow candidate : values()) {
                if (candidate.value.equals(normalized)) {
                    return candidate;
                }
            }
            return LAST_24_HOURS;
        }

        String value() {
            return value;
        }

        int bucketCount() {
            return bucketCount;
        }

        LocalDateTime nextBucketStart(LocalDateTime now) {
            // 步骤 1：小时窗口对齐到下一个整点，天窗口对齐到下一天零点。
            if (unit == ChronoUnit.HOURS) {
                return now.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            }
            return now.toLocalDate().plusDays(1).atStartOfDay();
        }

        LocalDateTime firstBucketStart(LocalDateTime rangeEndExclusive) {
            return rangeEndExclusive.minus(bucketCount, unit);
        }

        LocalDateTime alignBucketStart(LocalDateTime value) {
            // 步骤 1：把任意时间点对齐到其所属分桶起点。
            if (unit == ChronoUnit.HOURS) {
                return value.truncatedTo(ChronoUnit.HOURS);
            }
            return value.toLocalDate().atStartOfDay();
        }

        LocalDateTime increment(LocalDateTime value) {
            return value.plus(1, unit);
        }

        String formatLabel(LocalDateTime value) {
            // 步骤 1：按窗口粒度选择不同横轴标签格式。
            if (unit == ChronoUnit.HOURS) {
                return HOUR_LABEL_FORMATTER.format(value);
            }
            return DAY_LABEL_FORMATTER.format(value);
        }
    }

    /**
     * 分桶累加器，仅服务于当前聚合过程。
     */
    private static final class BucketAccumulator {
        /**
         * 当前分桶内创建的会话数。
         */
        private int conversationCount;

        /**
         * 当前分桶内创建的消息数。
         */
        private int messageCount;

        /**
         * 当前分桶内链路数。
         */
        private int traceCount;

        /**
         * 当前分桶内成功链路数。
         */
        private int successCount;

        /**
         * 当前分桶内失败链路数。
         */
        private int failedCount;

        /**
         * 当前分桶内活跃用户集合，用 Set 去重。
         */
        private final Set<Long> activeUserIds = new HashSet<>();

        /**
         * 当前分桶内可统计的 Trace 耗时集合，用于计算平均耗时。
         */
        private final List<Long> durationValues = new ArrayList<>();
    }
}
