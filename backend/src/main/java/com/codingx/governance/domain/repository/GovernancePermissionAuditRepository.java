package com.codingx.governance.domain.repository;

import com.codingx.governance.domain.model.GovernancePermissionAudit;
import java.util.List;

/**
 * 定义权限审计持久化能力。
 */
public interface GovernancePermissionAuditRepository {

    /**
     * 保存权限审计记录。
     * @param audit 审计对象。
     */
    void save(GovernancePermissionAudit audit);

    /**
     * 查询最近权限审计。
     * @param limit 最大返回条数。
     * @return 审计列表。
     */
    List<GovernancePermissionAudit> findRecent(int limit);
}
