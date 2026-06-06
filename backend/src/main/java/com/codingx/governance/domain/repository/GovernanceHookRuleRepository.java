package com.codingx.governance.domain.repository;

import com.codingx.governance.domain.model.GovernanceHookRule;
import java.util.List;

/**
 * 定义 Hook 规则持久化能力。
 */
public interface GovernanceHookRuleRepository {

    /**
     * 查询指定触发点启用的 Hook 规则。
     * @param triggerPoint 触发点。
     * @return Hook 规则列表。
     */
    List<GovernanceHookRule> findEnabledByTriggerPoint(String triggerPoint);

    /**
     * 查询全部未删除 Hook 规则。
     * @return Hook 规则列表。
     */
    List<GovernanceHookRule> findAll();

    /**
     * 按主键查询 Hook 规则。
     * @param id Hook 主键。
     * @return Hook 规则，未命中返回 null。
     */
    GovernanceHookRule findById(Long id);

    /**
     * 保存 Hook 规则。
     * @param rule Hook 规则对象。
     */
    void save(GovernanceHookRule rule);

    /**
     * 逻辑删除 Hook 规则。
     * @param id Hook 主键。
     */
    void softDeleteById(Long id);
}
