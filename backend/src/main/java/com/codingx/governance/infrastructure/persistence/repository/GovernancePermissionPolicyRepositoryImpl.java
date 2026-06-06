package com.codingx.governance.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codingx.governance.domain.model.GovernancePermissionPolicy;
import com.codingx.governance.domain.repository.GovernancePermissionPolicyRepository;
import com.codingx.governance.infrastructure.persistence.dataobject.GovernancePermissionPolicyDO;
import com.codingx.governance.infrastructure.persistence.mapper.GovernancePermissionPolicyMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 权限策略仓储实现，隔离 MyBatis 数据对象与治理领域模型。
 */
@Repository
@RequiredArgsConstructor
public class GovernancePermissionPolicyRepositoryImpl implements GovernancePermissionPolicyRepository {

    /** 权限策略 Mapper，用于查询、保存和逻辑删除策略配置。 */
    private final GovernancePermissionPolicyMapper mapper;

    @Override
    public List<GovernancePermissionPolicy> findEnabledPolicies() {
        // 步骤 1：只读取未删除且启用的策略，保持 sortNo 优先级稳定。
        return mapper.selectList(baseWrapper().eq(GovernancePermissionPolicyDO::getEnabled, 1))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<GovernancePermissionPolicy> findAll() {
        // 步骤 1：管理端列表保留未删除策略，不按启用态过滤。
        return mapper.selectList(baseWrapper()).stream().map(this::toDomain).toList();
    }

    @Override
    public GovernancePermissionPolicy findById(Long id) {
        // 步骤 1：空主键直接返回 null，由应用服务负责中文错误文案。
        if (id == null) {
            return null;
        }
        GovernancePermissionPolicyDO dataObject = mapper.selectOne(new LambdaQueryWrapper<GovernancePermissionPolicyDO>()
            .eq(GovernancePermissionPolicyDO::getId, id)
            .eq(GovernancePermissionPolicyDO::getDeleted, 0)
            .last("limit 1"));
        return dataObject == null ? null : toDomain(dataObject);
    }

    @Override
    public void save(GovernancePermissionPolicy policy) {
        // 步骤 1：策略主键由应用服务生成；仓储仅按主键插入或更新。
        GovernancePermissionPolicyDO dataObject = toDataObject(policy);
        if (dataObject.getId() == null || mapper.selectById(dataObject.getId()) == null) {
            mapper.insert(dataObject);
            return;
        }
        mapper.updateById(dataObject);
    }

    @Override
    public void softDeleteById(Long id) {
        // 步骤 1：逻辑删除保留历史策略和审计关联，不物理移除记录。
        mapper.update(
            null,
            new LambdaUpdateWrapper<GovernancePermissionPolicyDO>()
                .set(GovernancePermissionPolicyDO::getDeleted, 1)
                .set(GovernancePermissionPolicyDO::getUpdatedAt, LocalDateTime.now())
                .eq(GovernancePermissionPolicyDO::getId, id)
        );
    }

    private LambdaQueryWrapper<GovernancePermissionPolicyDO> baseWrapper() {
        return new LambdaQueryWrapper<GovernancePermissionPolicyDO>()
            .eq(GovernancePermissionPolicyDO::getDeleted, 0)
            .orderByAsc(GovernancePermissionPolicyDO::getSortNo)
            .orderByAsc(GovernancePermissionPolicyDO::getPolicyCode);
    }

    private GovernancePermissionPolicyDO toDataObject(GovernancePermissionPolicy policy) {
        GovernancePermissionPolicyDO dataObject = new GovernancePermissionPolicyDO();
        dataObject.setId(policy.getId());
        dataObject.setPolicyCode(StrUtil.trim(policy.getPolicyCode()));
        dataObject.setPolicyName(policy.getPolicyName());
        dataObject.setToolCode(StrUtil.trimToNull(policy.getToolCode()));
        dataObject.setCommandPattern(StrUtil.trimToNull(policy.getCommandPattern()));
        dataObject.setPathPattern(StrUtil.trimToNull(policy.getPathPattern()));
        dataObject.setAction(policy.getAction());
        dataObject.setRiskLevel(policy.getRiskLevel());
        dataObject.setDescription(policy.getDescription());
        dataObject.setEnabled(policy.getEnabled());
        dataObject.setSortNo(policy.getSortNo());
        dataObject.setCreatedAt(policy.getCreatedAt());
        dataObject.setUpdatedAt(policy.getUpdatedAt());
        dataObject.setDeleted(policy.getDeleted());
        return dataObject;
    }

    private GovernancePermissionPolicy toDomain(GovernancePermissionPolicyDO dataObject) {
        return GovernancePermissionPolicy.builder()
            .id(dataObject.getId())
            .policyCode(dataObject.getPolicyCode())
            .policyName(dataObject.getPolicyName())
            .toolCode(dataObject.getToolCode())
            .commandPattern(dataObject.getCommandPattern())
            .pathPattern(dataObject.getPathPattern())
            .action(dataObject.getAction())
            .riskLevel(dataObject.getRiskLevel())
            .description(dataObject.getDescription())
            .enabled(dataObject.getEnabled())
            .sortNo(dataObject.getSortNo())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
