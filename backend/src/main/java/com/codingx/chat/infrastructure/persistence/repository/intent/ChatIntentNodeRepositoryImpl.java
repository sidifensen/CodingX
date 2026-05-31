package com.codingx.chat.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.domain.model.ChatIntentNode;
import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatIntentNodeDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatIntentNodeMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现意图树节点仓储的 MyBatis 持久化逻辑，统一屏蔽逻辑删除数据。
 */
@Repository
@RequiredArgsConstructor
public class ChatIntentNodeRepositoryImpl implements ChatIntentNodeRepository {

    /** 意图节点 Mapper，用于维护 chat_intent_node 表中的树形配置。 */
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
                .orderByAsc(ChatIntentNodeDO::getSortOrder)
                .orderByAsc(ChatIntentNodeDO::getSortNo))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<ChatIntentNode> findAllNodes() {
        return chatIntentNodeMapper.selectList(new LambdaQueryWrapper<ChatIntentNodeDO>()
                .eq(ChatIntentNodeDO::getDeleted, 0)
                .orderByAsc(ChatIntentNodeDO::getSortOrder)
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

    @Override
    public ChatIntentNode findById(Long id) {
        ChatIntentNodeDO dataObject = chatIntentNodeMapper.selectById(id);
        if (dataObject == null || Integer.valueOf(1).equals(dataObject.getDeleted())) {
            return null;
        }
        return toDomain(dataObject);
    }

    @Override
    public boolean existsByIntentCode(String intentCode, Long excludedId) {
        if (StrUtil.isBlank(intentCode)) {
            return false;
        }
        LambdaQueryWrapper<ChatIntentNodeDO> wrapper = new LambdaQueryWrapper<ChatIntentNodeDO>()
            .eq(ChatIntentNodeDO::getIntentCode, intentCode)
            .eq(ChatIntentNodeDO::getDeleted, 0)
            .ne(excludedId != null, ChatIntentNodeDO::getId, excludedId);
        return chatIntentNodeMapper.selectCount(wrapper) > 0;
    }

    @Override
    public boolean hasChildren(String parentCode) {
        if (StrUtil.isBlank(parentCode)) {
            return false;
        }
        return chatIntentNodeMapper.selectCount(new LambdaQueryWrapper<ChatIntentNodeDO>()
            .eq(ChatIntentNodeDO::getParentCode, parentCode)
            .eq(ChatIntentNodeDO::getDeleted, 0)) > 0;
    }

    @Override
    public void softDeleteById(Long id) {
        ChatIntentNodeDO dataObject = new ChatIntentNodeDO();
        dataObject.setId(id);
        dataObject.setDeleted(1);
        dataObject.setUpdatedAt(LocalDateTime.now());
        chatIntentNodeMapper.updateById(dataObject);
    }

    private ChatIntentNodeDO toDataObject(ChatIntentNode node) {
        ChatIntentNodeDO dataObject = new ChatIntentNodeDO();
        dataObject.setId(node.getId());
        dataObject.setIntentCode(node.getIntentCode());
        dataObject.setParentCode(node.getParentCode());
        dataObject.setName(node.getName());
        dataObject.setDescription(node.getDescription());
        dataObject.setIntentType(node.getIntentType());
        dataObject.setKbId(node.getKbId());
        dataObject.setLevel(node.getLevel());
        dataObject.setExamples(node.getExamples());
        dataObject.setCollectionName(node.getCollectionName());
        dataObject.setTopK(node.getTopK());
        dataObject.setKind(node.getKind());
        dataObject.setPromptTemplate(node.getPromptTemplate());
        dataObject.setMcpToolId(node.getMcpToolId());
        dataObject.setParamPromptTemplate(node.getParamPromptTemplate());
        dataObject.setPromptSnippet(node.getPromptSnippet());
        dataObject.setEnabled(node.getEnabled());
        dataObject.setSortNo(node.getSortNo());
        dataObject.setSortOrder(node.getSortOrder());
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
            .kbId(dataObject.getKbId())
            .level(dataObject.getLevel())
            .examples(dataObject.getExamples())
            .collectionName(dataObject.getCollectionName())
            .topK(dataObject.getTopK())
            .kind(dataObject.getKind())
            .promptTemplate(dataObject.getPromptTemplate())
            .mcpToolId(dataObject.getMcpToolId())
            .paramPromptTemplate(dataObject.getParamPromptTemplate())
            .promptSnippet(dataObject.getPromptSnippet())
            .enabled(dataObject.getEnabled())
            .sortNo(dataObject.getSortNo())
            .sortOrder(dataObject.getSortOrder())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
