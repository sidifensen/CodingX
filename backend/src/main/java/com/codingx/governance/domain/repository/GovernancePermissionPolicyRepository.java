package com.codingx.governance.domain.repository;

import com.codingx.governance.domain.model.GovernancePermissionPolicy;
import java.util.List;

/**
 * 定义权限策略持久化能力。
 */
public interface GovernancePermissionPolicyRepository {

    /**
     * 查询启用且未删除的权限策略，按排序号升序返回。
     * @return 启用策略列表。
     */
    List<GovernancePermissionPolicy> findEnabledPolicies();

    /**
     * 查询全部未删除策略，供管理端展示。
     * @return 策略列表。
     */
    List<GovernancePermissionPolicy> findAll();

    /**
     * 按主键查询策略。
     * @param id 策略主键。
     * @return 策略对象，未命中返回 null。
     */
    GovernancePermissionPolicy findById(Long id);

    /**
     * 保存策略。
     * @param policy 策略对象。
     */
    void save(GovernancePermissionPolicy policy);

    /**
     * 逻辑删除策略。
     * @param id 策略主键。
     */
    void softDeleteById(Long id);
}
