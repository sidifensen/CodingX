package com.codingx.chat.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.domain.model.ChatSampleQuestion;
import com.codingx.chat.domain.repository.ChatSampleQuestionRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatSampleQuestionDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatSampleQuestionMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现示例问题仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatSampleQuestionRepositoryImpl implements ChatSampleQuestionRepository {

    private final ChatSampleQuestionMapper chatSampleQuestionMapper;

    @Override
    public List<ChatSampleQuestion> findEnabledQuestions() {
        return chatSampleQuestionMapper.selectList(new LambdaQueryWrapper<ChatSampleQuestionDO>()
                .eq(ChatSampleQuestionDO::getEnabled, 1)
                .eq(ChatSampleQuestionDO::getDeleted, 0)
                .orderByAsc(ChatSampleQuestionDO::getSortNo))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    private ChatSampleQuestion toDomain(ChatSampleQuestionDO dataObject) {
        return ChatSampleQuestion.builder()
            .id(dataObject.getId())
            .questionText(dataObject.getQuestionText())
            .category(dataObject.getCategory())
            .enabled(dataObject.getEnabled())
            .sortNo(dataObject.getSortNo())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
