package com.codingx.chat.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.domain.model.ChatQueryTermMapping;
import com.codingx.chat.domain.repository.ChatQueryTermMappingRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatQueryTermMappingDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatQueryTermMappingMapper;
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
                .orderByAsc(ChatQueryTermMappingDO::getSortNo))
            .stream()
            .map(this::toDomain)
            .sorted(Comparator
                .comparing(ChatQueryTermMapping::getSortNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(mapping -> mapping.getSourceTerm() == null ? 0 : -mapping.getSourceTerm().length()))
            .toList();
    }

    private ChatQueryTermMapping toDomain(ChatQueryTermMappingDO dataObject) {
        return ChatQueryTermMapping.builder()
            .id(dataObject.getId())
            .sourceTerm(dataObject.getSourceTerm())
            .targetTerm(dataObject.getTargetTerm())
            .mappingType(dataObject.getMappingType())
            .enabled(dataObject.getEnabled())
            .sortNo(dataObject.getSortNo())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
