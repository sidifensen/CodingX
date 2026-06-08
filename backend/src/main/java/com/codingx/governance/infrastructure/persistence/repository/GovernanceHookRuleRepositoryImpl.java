package com.codingx.governance.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codingx.governance.domain.model.GovernanceHookRule;
import com.codingx.governance.domain.repository.GovernanceHookRuleRepository;
import com.codingx.governance.infrastructure.persistence.dataobject.GovernanceHookRuleDO;
import com.codingx.governance.infrastructure.persistence.mapper.GovernanceHookRuleMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Hook 规则仓储实现，负责按触发点读取启用规则和管理端维护规则。
 */
@Repository
@RequiredArgsConstructor
public class GovernanceHookRuleRepositoryImpl implements GovernanceHookRuleRepository {

    /** Hook 规则 Mapper，用于读写生命周期规则配置。 */
    private final GovernanceHookRuleMapper mapper;

    @Override
    public List<GovernanceHookRule> findEnabledByTriggerPoint(String triggerPoint) {
        // 步骤 1：触发链路只读取启用规则，并按 sortNo 保持触发顺序。
        return mapper.selectList(baseWrapper()
                .eq(GovernanceHookRuleDO::getEnabled, 1)
                .eq(GovernanceHookRuleDO::getTriggerPoint, triggerPoint))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<GovernanceHookRule> findAll() {
        return mapper.selectList(baseWrapper()).stream().map(this::toDomain).toList();
    }

    @Override
    public GovernanceHookRule findById(Long id) {
        if (id == null) {
            return null;
        }
        GovernanceHookRuleDO dataObject = mapper.selectOne(new LambdaQueryWrapper<GovernanceHookRuleDO>()
            .eq(GovernanceHookRuleDO::getId, id)
            .eq(GovernanceHookRuleDO::getDeleted, 0)
            .last("limit 1"));
        return dataObject == null ? null : toDomain(dataObject);
    }

    @Override
    public void save(GovernanceHookRule rule) {
        GovernanceHookRuleDO dataObject = toDataObject(rule);
        if (dataObject.getId() == null || mapper.selectById(dataObject.getId()) == null) {
            mapper.insert(dataObject);
            return;
        }
        mapper.updateById(dataObject);
    }

    @Override
    public void softDeleteById(Long id) {
        // 步骤 1：Hook 删除只标记 deleted，避免误删后影响其他会话正在读取的规则配置。
        mapper.update(
            null,
            new LambdaUpdateWrapper<GovernanceHookRuleDO>()
                .set(GovernanceHookRuleDO::getDeleted, 1)
                .set(GovernanceHookRuleDO::getUpdatedAt, LocalDateTime.now())
                .eq(GovernanceHookRuleDO::getId, id)
        );
    }

    private LambdaQueryWrapper<GovernanceHookRuleDO> baseWrapper() {
        return new LambdaQueryWrapper<GovernanceHookRuleDO>()
            .eq(GovernanceHookRuleDO::getDeleted, 0)
            .orderByAsc(GovernanceHookRuleDO::getSortNo)
            .orderByAsc(GovernanceHookRuleDO::getHookCode);
    }

    private GovernanceHookRuleDO toDataObject(GovernanceHookRule rule) {
        GovernanceHookRuleDO dataObject = new GovernanceHookRuleDO();
        dataObject.setId(rule.getId());
        dataObject.setHookCode(rule.getHookCode());
        dataObject.setHookName(rule.getHookName());
        dataObject.setTriggerPoint(rule.getTriggerPoint());
        dataObject.setConditionKeyword(rule.getConditionKeyword());
        dataObject.setActionType(rule.getActionType());
        dataObject.setActionConfigJson(rule.getActionConfigJson());
        dataObject.setEnabled(rule.getEnabled());
        dataObject.setSortNo(rule.getSortNo());
        dataObject.setCreatedAt(rule.getCreatedAt());
        dataObject.setUpdatedAt(rule.getUpdatedAt());
        dataObject.setDeleted(rule.getDeleted());
        return dataObject;
    }

    private GovernanceHookRule toDomain(GovernanceHookRuleDO dataObject) {
        return GovernanceHookRule.builder()
            .id(dataObject.getId())
            .hookCode(dataObject.getHookCode())
            .hookName(dataObject.getHookName())
            .triggerPoint(dataObject.getTriggerPoint())
            .conditionKeyword(dataObject.getConditionKeyword())
            .actionType(dataObject.getActionType())
            .actionConfigJson(dataObject.getActionConfigJson())
            .enabled(dataObject.getEnabled())
            .sortNo(dataObject.getSortNo())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
