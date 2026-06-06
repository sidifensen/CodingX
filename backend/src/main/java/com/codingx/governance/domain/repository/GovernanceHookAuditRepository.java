package com.codingx.governance.domain.repository;

import com.codingx.governance.domain.model.GovernanceHookAudit;
import java.util.List;

/**
 * 定义 Hook 审计持久化能力。
 */
public interface GovernanceHookAuditRepository {

    /**
     * 保存 Hook 审计记录。
     * @param audit Hook 审计对象。
     */
    void save(GovernanceHookAudit audit);

    /**
     * 查询最近 Hook 审计。
     * @param limit 最大返回条数。
     * @return Hook 审计列表。
     */
    List<GovernanceHookAudit> findRecent(int limit);
}
