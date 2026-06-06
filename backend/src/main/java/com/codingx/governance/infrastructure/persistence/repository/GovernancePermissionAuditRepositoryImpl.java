package com.codingx.governance.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.governance.domain.model.GovernancePermissionAudit;
import com.codingx.governance.domain.repository.GovernancePermissionAuditRepository;
import com.codingx.governance.infrastructure.persistence.dataobject.GovernancePermissionAuditDO;
import com.codingx.governance.infrastructure.persistence.mapper.GovernancePermissionAuditMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 权限审计仓储实现，负责保存和查询最近工具判定记录。
 */
@Repository
@RequiredArgsConstructor
public class GovernancePermissionAuditRepositoryImpl implements GovernancePermissionAuditRepository {

    /** 权限审计 Mapper，用于插入审计和按时间倒序读取最近记录。 */
    private final GovernancePermissionAuditMapper mapper;

    @Override
    public void save(GovernancePermissionAudit audit) {
        // 步骤 1：审计记录只追加不更新，避免执行历史被后续流程覆盖。
        mapper.insert(toDataObject(audit));
    }

    @Override
    public List<GovernancePermissionAudit> findRecent(int limit) {
        // 步骤 1：管理端只需要最近记录，limit 做下限和上限保护。
        return mapper.selectList(new LambdaQueryWrapper<GovernancePermissionAuditDO>()
                .orderByDesc(GovernancePermissionAuditDO::getCreatedAt)
                .last("limit " + Math.min(Math.max(limit, 1), 200)))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    private GovernancePermissionAuditDO toDataObject(GovernancePermissionAudit audit) {
        GovernancePermissionAuditDO dataObject = new GovernancePermissionAuditDO();
        dataObject.setId(audit.getId());
        dataObject.setUserId(audit.getUserId());
        dataObject.setConversationId(audit.getConversationId());
        dataObject.setRunId(audit.getRunId());
        dataObject.setToolCode(audit.getToolCode());
        dataObject.setToolInput(audit.getToolInput());
        dataObject.setWorkingDirectory(audit.getWorkingDirectory());
        dataObject.setMatchedPolicyCode(audit.getMatchedPolicyCode());
        dataObject.setDecision(audit.getDecision());
        dataObject.setRiskLevel(audit.getRiskLevel());
        dataObject.setResult(audit.getResult());
        dataObject.setMessage(audit.getMessage());
        dataObject.setCreatedAt(audit.getCreatedAt());
        return dataObject;
    }

    private GovernancePermissionAudit toDomain(GovernancePermissionAuditDO dataObject) {
        return GovernancePermissionAudit.builder()
            .id(dataObject.getId())
            .userId(dataObject.getUserId())
            .conversationId(dataObject.getConversationId())
            .runId(dataObject.getRunId())
            .toolCode(dataObject.getToolCode())
            .toolInput(dataObject.getToolInput())
            .workingDirectory(dataObject.getWorkingDirectory())
            .matchedPolicyCode(dataObject.getMatchedPolicyCode())
            .decision(dataObject.getDecision())
            .riskLevel(dataObject.getRiskLevel())
            .result(dataObject.getResult())
            .message(dataObject.getMessage())
            .createdAt(dataObject.getCreatedAt())
            .build();
    }
}
