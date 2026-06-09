package com.codingx.chat.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.codingx.admin.application.service.AdminTraceRunPageView;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.chat.domain.repository.ChatTraceRunRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatTraceRunDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatTraceRunMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现 Trace 根链路仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatTraceRunRepositoryImpl implements ChatTraceRunRepository {

    /** 管理端 Trace 列表按链路耗时倒序的排序编码。 */
    private static final String SORT_DURATION_DESC = "duration_desc";

    /** Trace 根记录 Mapper，用于读写 chat_trace_run 表并支持管理端分页。 */
    private final ChatTraceRunMapper chatTraceRunMapper;

    @Override
    public void save(ChatTraceRun traceRun) {
        ChatTraceRunDO dataObject = toDataObject(traceRun);
        if (chatTraceRunMapper.selectById(traceRun.getId()) == null) {
            chatTraceRunMapper.insert(dataObject);
        } else {
            chatTraceRunMapper.updateById(dataObject);
        }
    }

    @Override
    public Optional<ChatTraceRun> findByTraceId(String traceId) {
        return Optional.ofNullable(chatTraceRunMapper.selectOne(new LambdaQueryWrapper<ChatTraceRunDO>()
            .eq(ChatTraceRunDO::getTraceId, traceId)
            .eq(ChatTraceRunDO::getDeleted, 0)
            .last("LIMIT 1"))).map(this::toDomain);
    }

    @Override
    public java.util.List<ChatTraceRun> findRecent(int limit) {
        return chatTraceRunMapper.selectList(new LambdaQueryWrapper<ChatTraceRunDO>()
                .eq(ChatTraceRunDO::getDeleted, 0)
                .orderByDesc(ChatTraceRunDO::getCreatedAt)
                .last("LIMIT " + Math.max(1, limit)))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public AdminTraceRunPageView pageByFilters(int current, int size, String traceId, String sort) {
        int normalizedCurrent = Math.max(1, current);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        LambdaQueryWrapper<ChatTraceRunDO> queryWrapper = Wrappers.lambdaQuery(ChatTraceRunDO.class)
            .eq(ChatTraceRunDO::getDeleted, 0);
        if (traceId != null && !traceId.isBlank()) {
            queryWrapper.eq(ChatTraceRunDO::getTraceId, traceId.trim());
        }
        queryWrapper.orderByDesc(ChatTraceRunDO::getStartedAt)
            .orderByDesc(ChatTraceRunDO::getId);
        List<ChatTraceRunDO> dataObjects = chatTraceRunMapper.selectList(queryWrapper);
        if (SORT_DURATION_DESC.equals(sort)) {
            // 步骤：PostgreSQL 的 DESC 可能把 NULL 排前，内存排序明确让无耗时链路排到最后。
            dataObjects = dataObjects.stream()
                .sorted(Comparator.comparing(
                        ChatTraceRunDO::getDurationMs,
                        Comparator.nullsLast(Comparator.reverseOrder())
                    )
                    .thenComparing(
                        ChatTraceRunDO::getStartedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                    )
                    .thenComparing(
                        ChatTraceRunDO::getId,
                        Comparator.nullsLast(Comparator.reverseOrder())
                    ))
                .toList();
        }
        long total = dataObjects.size();
        long pages = total == 0 ? 0 : (total + normalizedSize - 1L) / normalizedSize;
        int fromIndex = Math.min((normalizedCurrent - 1) * normalizedSize, dataObjects.size());
        int toIndex = Math.min(fromIndex + normalizedSize, dataObjects.size());
        List<ChatTraceRun> pageRecords = dataObjects.subList(fromIndex, toIndex).stream().map(this::toDomain).toList();
        return new AdminTraceRunPageView(pageRecords, total, normalizedSize, normalizedCurrent, pages);
    }

    private ChatTraceRunDO toDataObject(ChatTraceRun traceRun) {
        ChatTraceRunDO dataObject = new ChatTraceRunDO();
        dataObject.setId(traceRun.getId());
        dataObject.setTraceId(traceRun.getTraceId());
        dataObject.setTraceName(traceRun.getTraceName());
        dataObject.setEntryMethod(traceRun.getEntryMethod());
        dataObject.setConversationId(traceRun.getConversationId());
        dataObject.setTaskId(traceRun.getTaskId());
        dataObject.setUserId(traceRun.getUserId());
        dataObject.setStatus(traceRun.getStatus());
        dataObject.setErrorMessage(traceRun.getErrorMessage());
        dataObject.setDurationMs(traceRun.getDurationMs());
        dataObject.setExtraDataJson(traceRun.getExtraDataJson());
        dataObject.setStartedAt(traceRun.getStartedAt());
        dataObject.setFinishedAt(traceRun.getFinishedAt());
        dataObject.setCreatedAt(traceRun.getCreatedAt());
        dataObject.setUpdatedAt(traceRun.getUpdatedAt());
        dataObject.setDeleted(traceRun.getDeleted());
        return dataObject;
    }

    private ChatTraceRun toDomain(ChatTraceRunDO dataObject) {
        return ChatTraceRun.restore(
            dataObject.getId(),
            dataObject.getTraceId(),
            dataObject.getTraceName(),
            dataObject.getEntryMethod(),
            dataObject.getConversationId(),
            dataObject.getTaskId(),
            dataObject.getUserId(),
            dataObject.getStatus(),
            dataObject.getErrorMessage(),
            dataObject.getDurationMs(),
            dataObject.getExtraDataJson(),
            dataObject.getStartedAt(),
            dataObject.getFinishedAt(),
            dataObject.getCreatedAt(),
            dataObject.getUpdatedAt(),
            dataObject.getDeleted()
        );
    }
}
