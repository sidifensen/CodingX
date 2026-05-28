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

    private static final DateTimeFormatter BUCKET_KEY_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter HOUR_LABEL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    private final ChatConversationMapper chatConversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatTraceRunMapper chatTraceRunMapper;
    private final WorkspaceMapper workspaceMapper;
    private final ChatIntentNodeRepository chatIntentNodeRepository;
    private final ChatMcpRepository chatMcpRepository;
    private final ChatQueryTermMappingRepository chatQueryTermMappingRepository;
    private final ChatSampleQuestionRepository chatSampleQuestionRepository;
    private final ChatSkillRepository chatSkillRepository;
    private final ChatToolRepository chatToolRepository;
    private final ChatExpertRepository chatExpertRepository;

    /**
     * 返回指定窗口的 Dashboard 聚合视图。
     * @param rawWindow 时间窗口。
     * @return 聚合视图。
     */
    public AdminChatDashboardView getDashboard(String rawWindow) {
        DashboardWindow window = DashboardWindow.from(rawWindow);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime rangeEndExclusive = window.nextBucketStart(now);
        LocalDateTime rangeStartInclusive = window.firstBucketStart(rangeEndExclusive);

        List<ChatConversationDO> conversations = filterConversations(loadConversations(rangeStartInclusive), rangeStartInclusive, rangeEndExclusive);
        List<ChatMessageDO> messages = filterMessages(loadMessages(rangeStartInclusive), rangeStartInclusive, rangeEndExclusive);
        List<ChatTraceRunDO> traces = filterTraces(loadTraces(rangeStartInclusive), rangeStartInclusive, rangeEndExclusive);

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
        int total = traces.size();
        int successCount = (int) traces.stream().filter(this::isSuccessTrace).count();
        int failureCount = (int) traces.stream().filter(this::isFailureTrace).count();
        int runningCount = (int) traces.stream().filter(this::isRunningTrace).count();
        List<Long> durations = traces.stream()
            .map(ChatTraceRunDO::getDurationMs)
            .filter(duration -> duration != null && duration > 0)
            .sorted()
            .toList();
        long avgDuration = durations.isEmpty()
            ? 0L
            : Math.round(durations.stream().mapToLong(Long::longValue).average().orElse(0D));
        long p95Duration = durations.isEmpty() ? 0L : durations.get(Math.max(0, (int) Math.ceil(durations.size() * 0.95D) - 1));
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
        Map<String, BucketAccumulator> bucketMap = new HashMap<>();
        List<LocalDateTime> bucketStarts = new ArrayList<>();
        LocalDateTime cursor = window.firstBucketStart(rangeEndExclusive);
        for (int index = 0; index < window.bucketCount(); index += 1) {
            bucketStarts.add(cursor);
            bucketMap.put(bucketKey(cursor), new BucketAccumulator());
            cursor = window.increment(cursor);
        }

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
        for (ChatMessageDO message : messages) {
            LocalDateTime bucketStart = window.alignBucketStart(message.getCreatedAt());
            BucketAccumulator bucket = bucketMap.get(bucketKey(bucketStart));
            if (bucket != null) {
                bucket.messageCount += 1;
            }
        }
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
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageDO>()
            .eq(ChatMessageDO::getDeleted, 0)
            .ge(ChatMessageDO::getCreatedAt, rangeStartInclusive)
            .orderByAsc(ChatMessageDO::getCreatedAt));
    }

    private List<ChatTraceRunDO> loadTraces(LocalDateTime rangeStartInclusive) {
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

        private final String value;
        private final int bucketCount;
        private final ChronoUnit unit;

        DashboardWindow(String value, int bucketCount, ChronoUnit unit) {
            this.value = value;
            this.bucketCount = bucketCount;
            this.unit = unit;
        }

        static DashboardWindow from(String rawWindow) {
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
            if (unit == ChronoUnit.HOURS) {
                return now.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            }
            return now.toLocalDate().plusDays(1).atStartOfDay();
        }

        LocalDateTime firstBucketStart(LocalDateTime rangeEndExclusive) {
            return rangeEndExclusive.minus(bucketCount, unit);
        }

        LocalDateTime alignBucketStart(LocalDateTime value) {
            if (unit == ChronoUnit.HOURS) {
                return value.truncatedTo(ChronoUnit.HOURS);
            }
            return value.toLocalDate().atStartOfDay();
        }

        LocalDateTime increment(LocalDateTime value) {
            return value.plus(1, unit);
        }

        String formatLabel(LocalDateTime value) {
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
        private int conversationCount;
        private int messageCount;
        private int traceCount;
        private int successCount;
        private int failedCount;
        private final Set<Long> activeUserIds = new HashSet<>();
        private final List<Long> durationValues = new ArrayList<>();
    }
}
