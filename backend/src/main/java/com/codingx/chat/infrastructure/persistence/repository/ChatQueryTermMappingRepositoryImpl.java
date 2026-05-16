package com.codingx.chat.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codingx.chat.domain.model.ChatQueryTermMapping;
import com.codingx.chat.domain.repository.ChatQueryTermMappingRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatQueryTermMappingDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatQueryTermMappingMapper;
import com.codingx.chat.interfaces.response.PageResult;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现查询词映射仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatQueryTermMappingRepositoryImpl implements ChatQueryTermMappingRepository {

    private final ChatQueryTermMappingMapper chatQueryTermMappingMapper;

    @Override
    public List<ChatQueryTermMapping> findEnabledMappings() {
        return chatQueryTermMappingMapper.selectList(new LambdaQueryWrapper<ChatQueryTermMappingDO>()
                .eq(ChatQueryTermMappingDO::getEnabled, 1)
                .eq(ChatQueryTermMappingDO::getDeleted, 0)
                .orderByAsc(ChatQueryTermMappingDO::getPriority))
            .stream()
            .map(this::toDomain)
            .sorted(Comparator
                .comparing(ChatQueryTermMapping::getPriority, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(mapping -> mapping.getSourceTerm() == null ? 0 : -mapping.getSourceTerm().length()))
            .toList();
    }

    @Override
    public List<ChatQueryTermMapping> findAllMappings() {
        return chatQueryTermMappingMapper.selectList(new LambdaQueryWrapper<ChatQueryTermMappingDO>()
                .eq(ChatQueryTermMappingDO::getDeleted, 0)
                .orderByAsc(ChatQueryTermMappingDO::getPriority)
                .orderByDesc(ChatQueryTermMappingDO::getUpdatedAt))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public PageResult<ChatQueryTermMapping> pageQuery(int current, int size, String keyword) {
        LambdaQueryWrapper<ChatQueryTermMappingDO> wrapper = new LambdaQueryWrapper<ChatQueryTermMappingDO>()
            .eq(ChatQueryTermMappingDO::getDeleted, 0)
            .and(StrUtil.isNotBlank(keyword), query -> query
                .like(ChatQueryTermMappingDO::getSourceTerm, keyword)
                .or()
                .like(ChatQueryTermMappingDO::getTargetTerm, keyword))
            .orderByAsc(ChatQueryTermMappingDO::getPriority)
            .orderByDesc(ChatQueryTermMappingDO::getUpdatedAt);
        Page<ChatQueryTermMappingDO> page = chatQueryTermMappingMapper.selectPage(
            new Page<>(Math.max(1, current), Math.max(1, size)),
            wrapper
        );
        return PageResult.<ChatQueryTermMapping>builder()
            .records(page.getRecords().stream().map(this::toDomain).toList())
            .total(page.getTotal())
            .size(page.getSize())
            .current(page.getCurrent())
            .pages(page.getPages())
            .build();
    }

    @Override
    public ChatQueryTermMapping findById(Long id) {
        if (id == null) {
            return null;
        }
        ChatQueryTermMappingDO dataObject = chatQueryTermMappingMapper.selectById(id);
        if (dataObject == null || Integer.valueOf(1).equals(dataObject.getDeleted())) {
            return null;
        }
        return toDomain(dataObject);
    }

    @Override
    public void save(ChatQueryTermMapping mapping) {
        ChatQueryTermMappingDO dataObject = new ChatQueryTermMappingDO();
        dataObject.setId(mapping.getId());
        dataObject.setSourceTerm(mapping.getSourceTerm());
        dataObject.setTargetTerm(mapping.getTargetTerm());
        dataObject.setMatchType(mapping.getMatchType());
        dataObject.setEnabled(mapping.getEnabled());
        dataObject.setPriority(mapping.getPriority());
        dataObject.setRemark(mapping.getRemark());
        dataObject.setCreatedAt(mapping.getCreatedAt());
        dataObject.setUpdatedAt(mapping.getUpdatedAt());
        dataObject.setDeleted(mapping.getDeleted());
        if (chatQueryTermMappingMapper.selectById(mapping.getId()) == null) {
            chatQueryTermMappingMapper.insert(dataObject);
        } else {
            chatQueryTermMappingMapper.updateById(dataObject);
        }
    }

    @Override
    public void softDeleteById(Long id) {
        ChatQueryTermMappingDO update = new ChatQueryTermMappingDO();
        update.setId(id);
        update.setDeleted(1);
        update.setUpdatedAt(LocalDateTime.now());
        chatQueryTermMappingMapper.updateById(update);
    }

    private ChatQueryTermMapping toDomain(ChatQueryTermMappingDO dataObject) {
        return ChatQueryTermMapping.builder()
            .id(dataObject.getId())
            .sourceTerm(dataObject.getSourceTerm())
            .targetTerm(dataObject.getTargetTerm())
            .matchType(dataObject.getMatchType())
            .enabled(dataObject.getEnabled())
            .priority(dataObject.getPriority())
            .remark(dataObject.getRemark())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
