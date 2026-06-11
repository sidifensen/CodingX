package com.codingx.chat.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.domain.model.ChatExecutionRun;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatExecutionRunDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatExecutionRunMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现执行主链路仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatExecutionRunRepositoryImpl implements ChatExecutionRunRepository {

    /** 执行运行 Mapper，用于读写 chat_execution_run 表的主链路状态。 */
    private final ChatExecutionRunMapper chatExecutionRunMapper;

    @Override
    public void save(ChatExecutionRun run) {
        ChatExecutionRunDO dataObject = toDataObject(run);
        if (chatExecutionRunMapper.selectById(run.getId()) == null) {
            chatExecutionRunMapper.insert(dataObject);
        } else {
            chatExecutionRunMapper.updateById(dataObject);
        }
    }

    @Override
    public List<ChatExecutionRun> findByConversationId(Long conversationId) {
        return chatExecutionRunMapper.selectList(new LambdaQueryWrapper<ChatExecutionRunDO>()
                .eq(ChatExecutionRunDO::getConversationId, conversationId)
                .orderByDesc(ChatExecutionRunDO::getCreatedAt))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    private ChatExecutionRunDO toDataObject(ChatExecutionRun run) {
        ChatExecutionRunDO dataObject = new ChatExecutionRunDO();
        dataObject.setId(run.getId());
        dataObject.setConversationId(run.getConversationId());
        dataObject.setRequestMessageId(run.getRequestMessageId());
        dataObject.setResponseMessageId(run.getResponseMessageId());
        dataObject.setIntentCode(run.getIntentCode());
        dataObject.setStatus(run.getStatus());
        dataObject.setQueueStatus(run.getQueueStatus());
        dataObject.setSearchEnabled(run.getSearchEnabled());
        dataObject.setArtifactEnabled(run.getArtifactEnabled());
        dataObject.setErrorMessage(run.getErrorMessage());
        dataObject.setStartedAt(run.getStartedAt());
        dataObject.setFinishedAt(run.getFinishedAt());
        dataObject.setCreatedAt(run.getCreatedAt());
        dataObject.setUpdatedAt(run.getUpdatedAt());
        return dataObject;
    }

    private ChatExecutionRun toDomain(ChatExecutionRunDO dataObject) {
        return ChatExecutionRun.builder()
            .id(dataObject.getId())
            .conversationId(dataObject.getConversationId())
            .requestMessageId(dataObject.getRequestMessageId())
            .responseMessageId(dataObject.getResponseMessageId())
            .intentCode(dataObject.getIntentCode())
            .status(dataObject.getStatus())
            .queueStatus(dataObject.getQueueStatus())
            .searchEnabled(dataObject.getSearchEnabled())
            .artifactEnabled(dataObject.getArtifactEnabled())
            .errorMessage(dataObject.getErrorMessage())
            .startedAt(dataObject.getStartedAt())
            .finishedAt(dataObject.getFinishedAt())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .build();
    }
}
