package com.codingx.governance.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.governance.domain.model.GovernanceHookAudit;
import com.codingx.governance.domain.repository.GovernanceHookAuditRepository;
import com.codingx.governance.infrastructure.persistence.dataobject.GovernanceHookAuditDO;
import com.codingx.governance.infrastructure.persistence.mapper.GovernanceHookAuditMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Hook 审计仓储实现，负责追加 Hook 触发记录并提供最近记录查询。
 */
@Repository
@RequiredArgsConstructor
public class GovernanceHookAuditRepositoryImpl implements GovernanceHookAuditRepository {

    /** Hook 审计 Mapper，用于写入生命周期审计记录。 */
    private final GovernanceHookAuditMapper mapper;

    @Override
    public void save(GovernanceHookAudit audit) {
        // 步骤 1：Hook 审计只追加，避免运行过程证据被覆盖。
        mapper.insert(toDataObject(audit));
    }

    @Override
    public List<GovernanceHookAudit> findRecent(int limit) {
        return mapper.selectList(new LambdaQueryWrapper<GovernanceHookAuditDO>()
                .orderByDesc(GovernanceHookAuditDO::getCreatedAt)
                .last("limit " + Math.min(Math.max(limit, 1), 200)))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    private GovernanceHookAuditDO toDataObject(GovernanceHookAudit audit) {
        GovernanceHookAuditDO dataObject = new GovernanceHookAuditDO();
        dataObject.setId(audit.getId());
        dataObject.setHookCode(audit.getHookCode());
        dataObject.setTriggerPoint(audit.getTriggerPoint());
        dataObject.setConversationId(audit.getConversationId());
        dataObject.setRunId(audit.getRunId());
        dataObject.setToolCode(audit.getToolCode());
        dataObject.setStatus(audit.getStatus());
        dataObject.setMessage(audit.getMessage());
        dataObject.setCreatedAt(audit.getCreatedAt());
        return dataObject;
    }

    private GovernanceHookAudit toDomain(GovernanceHookAuditDO dataObject) {
        return GovernanceHookAudit.builder()
            .id(dataObject.getId())
            .hookCode(dataObject.getHookCode())
            .triggerPoint(dataObject.getTriggerPoint())
            .conversationId(dataObject.getConversationId())
            .runId(dataObject.getRunId())
            .toolCode(dataObject.getToolCode())
            .status(dataObject.getStatus())
            .message(dataObject.getMessage())
            .createdAt(dataObject.getCreatedAt())
            .build();
    }
}
