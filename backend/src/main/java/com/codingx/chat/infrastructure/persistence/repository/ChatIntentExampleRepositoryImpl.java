package com.codingx.chat.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.domain.model.ChatIntentExample;
import com.codingx.chat.domain.repository.ChatIntentExampleRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatIntentExampleDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatIntentExampleMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现意图示例仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatIntentExampleRepositoryImpl implements ChatIntentExampleRepository {

    private final ChatIntentExampleMapper chatIntentExampleMapper;

    @Override
    public void save(ChatIntentExample example) {
        ChatIntentExampleDO dataObject = toDataObject(example);
        if (chatIntentExampleMapper.selectById(example.getId()) == null) {
            chatIntentExampleMapper.insert(dataObject);
        } else {
            chatIntentExampleMapper.updateById(dataObject);
        }
    }

    @Override
    public List<ChatIntentExample> findByIntentCode(String intentCode) {
        return chatIntentExampleMapper.selectList(new LambdaQueryWrapper<ChatIntentExampleDO>()
                .eq(ChatIntentExampleDO::getIntentCode, intentCode)
                .orderByAsc(ChatIntentExampleDO::getSortNo))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    private ChatIntentExampleDO toDataObject(ChatIntentExample example) {
        ChatIntentExampleDO dataObject = new ChatIntentExampleDO();
        dataObject.setId(example.getId());
        dataObject.setIntentCode(example.getIntentCode());
        dataObject.setExampleText(example.getExampleText());
        dataObject.setSortNo(example.getSortNo());
        dataObject.setCreatedAt(example.getCreatedAt());
        return dataObject;
    }

    private ChatIntentExample toDomain(ChatIntentExampleDO dataObject) {
        return ChatIntentExample.builder()
            .id(dataObject.getId())
            .intentCode(dataObject.getIntentCode())
            .exampleText(dataObject.getExampleText())
            .sortNo(dataObject.getSortNo())
            .createdAt(dataObject.getCreatedAt())
            .build();
    }
}
