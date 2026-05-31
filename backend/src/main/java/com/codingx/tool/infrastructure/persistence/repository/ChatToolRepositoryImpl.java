package com.codingx.tool.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codingx.tool.domain.model.ChatTool;
import com.codingx.tool.domain.repository.ChatToolRepository;
import com.codingx.tool.infrastructure.persistence.dataobject.ChatToolDO;
import com.codingx.tool.infrastructure.persistence.mapper.ChatToolMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 聊天工具配置仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class ChatToolRepositoryImpl implements ChatToolRepository {

    /** 工具 Mapper，用于读写 chat_tool 表中的工具配置。 */
    private final ChatToolMapper chatToolMapper;

    @Override
    public List<ChatTool> findAll() {
        return chatToolMapper.selectList(baseListWrapper())
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public ChatTool findById(Long id) {
        if (id == null) {
            return null;
        }
        ChatToolDO dataObject = chatToolMapper.selectOne(new LambdaQueryWrapper<ChatToolDO>()
            .eq(ChatToolDO::getId, id)
            .eq(ChatToolDO::getDeleted, 0)
            .last("limit 1"));
        return dataObject == null ? null : toDomain(dataObject);
    }

    @Override
    public ChatTool findByToolCode(String toolCode) {
        if (StrUtil.isBlank(toolCode)) {
            return null;
        }
        ChatToolDO dataObject = chatToolMapper.selectOne(new LambdaQueryWrapper<ChatToolDO>()
            .eq(ChatToolDO::getToolCode, toolCode.trim())
            .eq(ChatToolDO::getDeleted, 0)
            .last("limit 1"));
        return dataObject == null ? null : toDomain(dataObject);
    }

    @Override
    public boolean existsByToolCode(String toolCode, Long excludedId) {
        if (StrUtil.isBlank(toolCode)) {
            return false;
        }
        LambdaQueryWrapper<ChatToolDO> queryWrapper = new LambdaQueryWrapper<ChatToolDO>()
            .eq(ChatToolDO::getToolCode, toolCode.trim())
            .eq(ChatToolDO::getDeleted, 0)
            .ne(excludedId != null, ChatToolDO::getId, excludedId);
        return chatToolMapper.selectCount(queryWrapper) > 0;
    }

    @Override
    public void save(ChatTool chatTool) {
        ChatToolDO dataObject = toDataObject(chatTool);
        if (chatToolMapper.selectById(chatTool.getId()) == null) {
            chatToolMapper.insert(dataObject);
            return;
        }
        chatToolMapper.updateById(dataObject);
    }

    @Override
    public void softDeleteById(Long id) {
        chatToolMapper.update(
            null,
            new LambdaUpdateWrapper<ChatToolDO>()
                .set(ChatToolDO::getDeleted, 1)
                .set(ChatToolDO::getUpdatedAt, LocalDateTime.now())
                .eq(ChatToolDO::getId, id)
        );
    }

    private LambdaQueryWrapper<ChatToolDO> baseListWrapper() {
        return new LambdaQueryWrapper<ChatToolDO>()
            .eq(ChatToolDO::getDeleted, 0)
            .orderByAsc(ChatToolDO::getSortNo)
            .orderByAsc(ChatToolDO::getToolCode);
    }

    private ChatToolDO toDataObject(ChatTool chatTool) {
        ChatToolDO dataObject = new ChatToolDO();
        dataObject.setId(chatTool.getId());
        dataObject.setToolCode(chatTool.getToolCode());
        dataObject.setDisplayName(chatTool.getDisplayName());
        dataObject.setDescription(chatTool.getDescription());
        dataObject.setCategory(chatTool.getCategory());
        dataObject.setSourceType(chatTool.getSourceType());
        dataObject.setEnabled(chatTool.getEnabled());
        dataObject.setSortNo(chatTool.getSortNo());
        dataObject.setCreatedAt(chatTool.getCreatedAt());
        dataObject.setUpdatedAt(chatTool.getUpdatedAt());
        dataObject.setDeleted(chatTool.getDeleted());
        return dataObject;
    }

    private ChatTool toDomain(ChatToolDO dataObject) {
        return ChatTool.builder()
            .id(dataObject.getId())
            .toolCode(dataObject.getToolCode())
            .displayName(dataObject.getDisplayName())
            .description(dataObject.getDescription())
            .category(dataObject.getCategory())
            .sourceType(dataObject.getSourceType())
            .enabled(dataObject.getEnabled())
            .sortNo(dataObject.getSortNo())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
