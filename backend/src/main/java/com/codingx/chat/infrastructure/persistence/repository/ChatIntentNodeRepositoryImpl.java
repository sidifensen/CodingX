package com.codingx.chat.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.domain.model.ChatIntentNode;
import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatIntentNodeDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatIntentNodeMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现意图树节点仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatIntentNodeRepositoryImpl implements ChatIntentNodeRepository {

    private final ChatIntentNodeMapper chatIntentNodeMapper;

    @Override
    public void save(ChatIntentNode node) {
        ChatIntentNodeDO dataObject = toDataObject(node);
        if (chatIntentNodeMapper.selectById(node.getId()) == null) {
            chatIntentNodeMapper.insert(dataObject);
        } else {
            chatIntentNodeMapper.updateById(dataObject);
        }
    }

    @Override
    public List<ChatIntentNode> findEnabledNodes() {
        return chatIntentNodeMapper.selectList(new LambdaQueryWrapper<ChatIntentNodeDO>()
                .eq(ChatIntentNodeDO::getEnabled, 1)
                .eq(ChatIntentNodeDO::getDeleted, 0)
                .orderByAsc(ChatIntentNodeDO::getSortNo))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<ChatIntentNode> findAllNodes() {
        return chatIntentNodeMapper.selectList(new LambdaQueryWrapper<ChatIntentNodeDO>()
                .eq(ChatIntentNodeDO::getDeleted, 0)
                .orderByAsc(ChatIntentNodeDO::getSortNo))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public ChatIntentNode findByIntentCode(String intentCode) {
        ChatIntentNodeDO dataObject = chatIntentNodeMapper.selectOne(new LambdaQueryWrapper<ChatIntentNodeDO>()
            .eq(ChatIntentNodeDO::getIntentCode, intentCode)
            .eq(ChatIntentNodeDO::getDeleted, 0)
            .last("limit 1"));
        return dataObject == null ? null : toDomain(dataObject);
    }

    private ChatIntentNodeDO toDataObject(ChatIntentNode node) {
        ChatIntentNodeDO dataObject = new ChatIntentNodeDO();
        dataObject.setId(node.getId());
        dataObject.setIntentCode(node.getIntentCode());
        dataObject.setParentCode(node.getParentCode());
        dataObject.setName(node.getName());
        dataObject.setDescription(node.getDescription());
        dataObject.setIntentType(node.getIntentType());
        dataObject.setPromptTemplate(node.getPromptTemplate());
        dataObject.setMcpToolId(node.getMcpToolId());
        dataObject.setParamPromptTemplate(node.getParamPromptTemplate());
        dataObject.setEnabled(node.getEnabled());
        dataObject.setSortNo(node.getSortNo());
        dataObject.setCreatedAt(node.getCreatedAt());
        dataObject.setUpdatedAt(node.getUpdatedAt());
        dataObject.setDeleted(node.getDeleted());
        return dataObject;
    }

    private ChatIntentNode toDomain(ChatIntentNodeDO dataObject) {
        return ChatIntentNode.builder()
            .id(dataObject.getId())
            .intentCode(dataObject.getIntentCode())
            .parentCode(dataObject.getParentCode())
            .name(dataObject.getName())
            .description(dataObject.getDescription())
            .intentType(dataObject.getIntentType())
            .promptTemplate(dataObject.getPromptTemplate())
            .mcpToolId(dataObject.getMcpToolId())
            .paramPromptTemplate(dataObject.getParamPromptTemplate())
            .enabled(dataObject.getEnabled())
            .sortNo(dataObject.getSortNo())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
