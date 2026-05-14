package com.codingx.chat.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.chat.domain.repository.ChatTraceRunRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatTraceRunDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatTraceRunMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现 Trace 根链路仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatTraceRunRepositoryImpl implements ChatTraceRunRepository {

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
        return ChatTraceRun.builder()
            .id(dataObject.getId())
            .traceId(dataObject.getTraceId())
            .traceName(dataObject.getTraceName())
            .entryMethod(dataObject.getEntryMethod())
            .conversationId(dataObject.getConversationId())
            .taskId(dataObject.getTaskId())
            .userId(dataObject.getUserId())
            .status(dataObject.getStatus())
            .errorMessage(dataObject.getErrorMessage())
            .durationMs(dataObject.getDurationMs())
            .extraDataJson(dataObject.getExtraDataJson())
            .startedAt(dataObject.getStartedAt())
            .finishedAt(dataObject.getFinishedAt())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
