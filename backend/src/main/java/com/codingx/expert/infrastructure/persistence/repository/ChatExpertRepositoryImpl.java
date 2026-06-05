package com.codingx.expert.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.expert.domain.model.ChatExpert;
import com.codingx.expert.domain.repository.ChatExpertRepository;
import com.codingx.expert.infrastructure.persistence.dataobject.ChatExpertDO;
import com.codingx.expert.infrastructure.persistence.mapper.ChatExpertMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现聊天专家配置仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatExpertRepositoryImpl implements ChatExpertRepository {

    /**
     * 专家配置 Mapper，负责访问 expert 表。
     */
    private final ChatExpertMapper chatExpertMapper;

    @Override
    public List<ChatExpert> findAll() {
        // 步骤 1：按基础列表条件读取未删除专家配置。
        // 步骤 2：转换为领域对象，避免应用层直接依赖 MyBatis 数据对象。
        return chatExpertMapper.selectList(baseListWrapper())
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public PageResult<ChatExpert> pageQuery(int current, int size) {
        // 步骤 1：页码和分页大小做最小边界保护，避免传入 0 或负数。
        Page<ChatExpertDO> page = chatExpertMapper.selectPage(
            new Page<>(Math.max(1, current), Math.max(1, size)),
            baseListWrapper()
        );
        // 步骤 2：分页记录逐条转换为领域对象，分页元数据保持 MyBatis 查询结果。
        return PageResult.<ChatExpert>builder()
            .records(page.getRecords().stream().map(this::toDomain).toList())
            .total(page.getTotal())
            .size(page.getSize())
            .current(page.getCurrent())
            .pages(page.getPages())
            .build();
    }

    @Override
    public List<ChatExpert> findAllEnabled() {
        return chatExpertMapper.selectList(baseListWrapper().eq(ChatExpertDO::getEnabled, 1))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public ChatExpert findById(Long id) {
        // 步骤 1：空主键直接返回 null，交由应用服务转换为业务异常。
        if (id == null) {
            return null;
        }
        // 步骤 2：只查询未删除专家，避免管理端误编辑逻辑删除数据。
        ChatExpertDO dataObject = chatExpertMapper.selectOne(new LambdaQueryWrapper<ChatExpertDO>()
            .eq(ChatExpertDO::getId, id)
            .eq(ChatExpertDO::getDeleted, 0)
            .last("limit 1"));
        return dataObject == null ? null : toDomain(dataObject);
    }

    @Override
    public ChatExpert findByExpertCode(String expertCode) {
        if (StrUtil.isBlank(expertCode)) {
            return null;
        }
        ChatExpertDO dataObject = chatExpertMapper.selectOne(new LambdaQueryWrapper<ChatExpertDO>()
            .eq(ChatExpertDO::getExpertCode, expertCode.trim())
            .eq(ChatExpertDO::getDeleted, 0)
            .last("limit 1"));
        return dataObject == null ? null : toDomain(dataObject);
    }

    @Override
    public boolean existsByExpertCode(String expertCode, Long excludedId) {
        if (StrUtil.isBlank(expertCode)) {
            return false;
        }
        LambdaQueryWrapper<ChatExpertDO> wrapper = new LambdaQueryWrapper<ChatExpertDO>()
            .eq(ChatExpertDO::getExpertCode, expertCode.trim())
            .eq(ChatExpertDO::getDeleted, 0)
            .ne(excludedId != null, ChatExpertDO::getId, excludedId);
        return chatExpertMapper.selectCount(wrapper) > 0;
    }

    @Override
    public void save(ChatExpert expert) {
        // 步骤 1：领域对象先映射为数据对象，保持持久化细节隔离。
        ChatExpertDO dataObject = toDataObject(expert);
        // 步骤 2：数据库不存在时插入，已存在时按主键更新。
        if (chatExpertMapper.selectById(expert.getId()) == null) {
            chatExpertMapper.insert(dataObject);
            return;
        }
        chatExpertMapper.updateById(dataObject);
    }

    @Override
    public void softDeleteById(Long id) {
        chatExpertMapper.update(
            null,
            new LambdaUpdateWrapper<ChatExpertDO>()
                .set(ChatExpertDO::getDeleted, 1)
                .set(ChatExpertDO::getUpdatedAt, LocalDateTime.now())
                .eq(ChatExpertDO::getId, id)
        );
    }

    private LambdaQueryWrapper<ChatExpertDO> baseListWrapper() {
        return new LambdaQueryWrapper<ChatExpertDO>()
            .eq(ChatExpertDO::getDeleted, 0)
            .orderByAsc(ChatExpertDO::getSortNo)
            .orderByAsc(ChatExpertDO::getExpertCode);
    }

    private ChatExpertDO toDataObject(ChatExpert expert) {
        ChatExpertDO dataObject = new ChatExpertDO();
        dataObject.setId(expert.getId());
        dataObject.setExpertCode(expert.getExpertCode());
        dataObject.setDisplayName(expert.getDisplayName());
        dataObject.setDescription(expert.getDescription());
        dataObject.setCategory(expert.getCategory());
        dataObject.setTagsJson(expert.getTagsJson());
        dataObject.setAvatarUrl(expert.getAvatarUrl());
        dataObject.setPresetQuestion(expert.getPresetQuestion());
        dataObject.setSystemPrompt(expert.getSystemPrompt());
        dataObject.setEnabled(expert.getEnabled());
        dataObject.setSortNo(expert.getSortNo());
        dataObject.setCreatedAt(expert.getCreatedAt());
        dataObject.setUpdatedAt(expert.getUpdatedAt());
        dataObject.setDeleted(expert.getDeleted());
        return dataObject;
    }

    private ChatExpert toDomain(ChatExpertDO dataObject) {
        return ChatExpert.builder()
            .id(dataObject.getId())
            .expertCode(dataObject.getExpertCode())
            .displayName(dataObject.getDisplayName())
            .description(dataObject.getDescription())
            .category(dataObject.getCategory())
            .tagsJson(dataObject.getTagsJson())
            .avatarUrl(dataObject.getAvatarUrl())
            .presetQuestion(dataObject.getPresetQuestion())
            .systemPrompt(dataObject.getSystemPrompt())
            .enabled(dataObject.getEnabled())
            .sortNo(dataObject.getSortNo())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
