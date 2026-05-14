package com.codingx.chat.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.domain.model.ChatExecutionStep;
import com.codingx.chat.domain.repository.ChatExecutionStepRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatExecutionStepDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatExecutionStepMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现执行步骤仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatExecutionStepRepositoryImpl implements ChatExecutionStepRepository {

    private final ChatExecutionStepMapper chatExecutionStepMapper;

    @Override
    public void save(ChatExecutionStep step) {
        ChatExecutionStepDO dataObject = toDataObject(step);
        if (chatExecutionStepMapper.selectById(step.getId()) == null) {
            chatExecutionStepMapper.insert(dataObject);
        } else {
            chatExecutionStepMapper.updateById(dataObject);
        }
    }

    @Override
    public List<ChatExecutionStep> findByRunId(Long runId) {
        return chatExecutionStepMapper.selectList(new LambdaQueryWrapper<ChatExecutionStepDO>()
                .eq(ChatExecutionStepDO::getRunId, runId)
                .orderByAsc(ChatExecutionStepDO::getSequenceNo))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    private ChatExecutionStepDO toDataObject(ChatExecutionStep step) {
        ChatExecutionStepDO dataObject = new ChatExecutionStepDO();
        dataObject.setId(step.getId());
        dataObject.setRunId(step.getRunId());
        dataObject.setStepType(step.getStepType());
        dataObject.setStepTitle(step.getStepTitle());
        dataObject.setStepStatus(step.getStepStatus());
        dataObject.setSequenceNo(step.getSequenceNo());
        dataObject.setContent(step.getContent());
        dataObject.setMetadataJson(step.getMetadataJson());
        dataObject.setCreatedAt(step.getCreatedAt());
        dataObject.setUpdatedAt(step.getUpdatedAt());
        return dataObject;
    }

    private ChatExecutionStep toDomain(ChatExecutionStepDO dataObject) {
        return ChatExecutionStep.builder()
            .id(dataObject.getId())
            .runId(dataObject.getRunId())
            .stepType(dataObject.getStepType())
            .stepTitle(dataObject.getStepTitle())
            .stepStatus(dataObject.getStepStatus())
            .sequenceNo(dataObject.getSequenceNo())
            .content(dataObject.getContent())
            .metadataJson(dataObject.getMetadataJson())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .build();
    }
}
