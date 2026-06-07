package com.codingx.governance.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.governance.domain.model.GovernanceLongTermMemory;
import com.codingx.governance.domain.repository.GovernanceLongTermMemoryRepository;
import com.codingx.governance.infrastructure.persistence.dataobject.GovernanceLongTermMemoryDO;
import com.codingx.governance.infrastructure.persistence.mapper.GovernanceLongTermMemoryMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 长期记忆仓储实现，负责状态过滤、归属过滤和领域对象转换。
 */
@Repository
@RequiredArgsConstructor
public class GovernanceLongTermMemoryRepositoryImpl implements GovernanceLongTermMemoryRepository {

    /** 长期记忆 Mapper，用于读写 governance_long_term_memory 表。 */
    private final GovernanceLongTermMemoryMapper mapper;

    @Override
    public void save(GovernanceLongTermMemory memory) {
        GovernanceLongTermMemoryDO dataObject = toDataObject(memory);
        if (dataObject.getId() == null || mapper.selectById(dataObject.getId()) == null) {
            mapper.insert(dataObject);
            return;
        }
        mapper.updateById(dataObject);
    }

    @Override
    public GovernanceLongTermMemory findById(Long id) {
        if (id == null) {
            return null;
        }
        GovernanceLongTermMemoryDO dataObject = mapper.selectOne(new LambdaQueryWrapper<GovernanceLongTermMemoryDO>()
            .eq(GovernanceLongTermMemoryDO::getId, id)
            .eq(GovernanceLongTermMemoryDO::getDeleted, 0)
            .last("limit 1"));
        return dataObject == null ? null : toDomain(dataObject);
    }

    @Override
    public GovernanceLongTermMemory findByMemoryKey(String memoryKey) {
        if (StrUtil.isBlank(memoryKey)) {
            return null;
        }
        GovernanceLongTermMemoryDO dataObject = mapper.selectOne(new LambdaQueryWrapper<GovernanceLongTermMemoryDO>()
            .eq(GovernanceLongTermMemoryDO::getMemoryKey, memoryKey)
            .eq(GovernanceLongTermMemoryDO::getDeleted, 0)
            .last("limit 1"));
        return dataObject == null ? null : toDomain(dataObject);
    }

    @Override
    public List<GovernanceLongTermMemory> findForUser(Long userId, Long workspaceId, String status, int limit) {
        LambdaQueryWrapper<GovernanceLongTermMemoryDO> wrapper = baseWrapper(status, limit)
            .eq(userId != null, GovernanceLongTermMemoryDO::getUserId, userId)
            .and(nested -> {
                // workspaceId 为空表示云端或未绑定会话，只能读取用户级记忆，不能串入任意项目约定。
                if (workspaceId == null) {
                    nested.isNull(GovernanceLongTermMemoryDO::getWorkspaceId);
                    return;
                }
                nested.isNull(GovernanceLongTermMemoryDO::getWorkspaceId)
                    .or()
                    .eq(GovernanceLongTermMemoryDO::getWorkspaceId, workspaceId);
            });
        return mapper.selectList(wrapper)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<GovernanceLongTermMemory> findForAdmin(String status, int limit) {
        return mapper.selectList(baseWrapper(status, limit)).stream().map(this::toDomain).toList();
    }

    @Override
    public List<GovernanceLongTermMemory> findActiveForContext(Long userId, Long workspaceId, int limit) {
        return findForUser(userId, workspaceId, "ACTIVE", limit);
    }

    @Override
    public int countPendingByUserAndWorkspace(Long userId, Long workspaceId) {
        LambdaQueryWrapper<GovernanceLongTermMemoryDO> wrapper = new LambdaQueryWrapper<GovernanceLongTermMemoryDO>()
            .eq(userId != null, GovernanceLongTermMemoryDO::getUserId, userId)
            .eq(GovernanceLongTermMemoryDO::getStatus, "PENDING")
            .eq(GovernanceLongTermMemoryDO::getDeleted, 0);
        wrapper.and(nested -> {
            // 用户级候选需要在任意工作空间提示；项目级候选只在对应工作空间提示。
            if (workspaceId == null) {
                nested.isNull(GovernanceLongTermMemoryDO::getWorkspaceId);
                return;
            }
            nested.isNull(GovernanceLongTermMemoryDO::getWorkspaceId)
                .or()
                .eq(GovernanceLongTermMemoryDO::getWorkspaceId, workspaceId);
        });
        return Math.toIntExact(mapper.selectCount(wrapper));
    }

    /**
     * 构造长期记忆基础查询条件，所有列表查询都必须默认排除逻辑删除记录并限制返回数量。
     * @param status 状态筛选，空值表示全部状态。
     * @param limit 最大返回条数。
     * @return 可继续追加归属条件的查询包装器。
     */
    private LambdaQueryWrapper<GovernanceLongTermMemoryDO> baseWrapper(String status, int limit) {
        // 步骤 1：limit 统一夹紧，避免管理端传入异常值导致慢查询。
        int normalizedLimit = Math.min(Math.max(limit, 1), 200);
        // 步骤 2：状态值统一大写，保证前端传 active/pending 时仍能命中数据库枚举。
        return new LambdaQueryWrapper<GovernanceLongTermMemoryDO>()
            .eq(GovernanceLongTermMemoryDO::getDeleted, 0)
            .eq(StrUtil.isNotBlank(status), GovernanceLongTermMemoryDO::getStatus, StrUtil.trimToEmpty(status).toUpperCase())
            .orderByDesc(GovernanceLongTermMemoryDO::getUpdatedAt)
            .last("limit " + normalizedLimit);
    }

    private GovernanceLongTermMemoryDO toDataObject(GovernanceLongTermMemory memory) {
        GovernanceLongTermMemoryDO dataObject = new GovernanceLongTermMemoryDO();
        dataObject.setId(memory.getId());
        dataObject.setMemoryScope(memory.getMemoryScope());
        dataObject.setUserId(memory.getUserId());
        dataObject.setWorkspaceId(memory.getWorkspaceId());
        dataObject.setMemoryKey(memory.getMemoryKey());
        dataObject.setContent(memory.getContent());
        dataObject.setStatus(memory.getStatus());
        dataObject.setSourceType(memory.getSourceType());
        dataObject.setSourceConversationId(memory.getSourceConversationId());
        dataObject.setSourceMessageId(memory.getSourceMessageId());
        dataObject.setKeywordJson(memory.getKeywordJson());
        dataObject.setConfidenceScore(memory.getConfidenceScore());
        dataObject.setLastUsedAt(memory.getLastUsedAt());
        dataObject.setCreatedAt(memory.getCreatedAt());
        dataObject.setUpdatedAt(memory.getUpdatedAt());
        dataObject.setDeleted(memory.getDeleted());
        return dataObject;
    }

    private GovernanceLongTermMemory toDomain(GovernanceLongTermMemoryDO dataObject) {
        return GovernanceLongTermMemory.builder()
            .id(dataObject.getId())
            .memoryScope(dataObject.getMemoryScope())
            .userId(dataObject.getUserId())
            .workspaceId(dataObject.getWorkspaceId())
            .memoryKey(dataObject.getMemoryKey())
            .content(dataObject.getContent())
            .status(dataObject.getStatus())
            .sourceType(dataObject.getSourceType())
            .sourceConversationId(dataObject.getSourceConversationId())
            .sourceMessageId(dataObject.getSourceMessageId())
            .keywordJson(dataObject.getKeywordJson())
            .confidenceScore(dataObject.getConfidenceScore())
            .lastUsedAt(dataObject.getLastUsedAt())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
