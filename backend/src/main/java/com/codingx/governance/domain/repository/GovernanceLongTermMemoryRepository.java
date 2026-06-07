package com.codingx.governance.domain.repository;

import com.codingx.governance.domain.model.GovernanceLongTermMemory;
import java.util.List;

/**
 * 定义长期记忆持久化能力，隔离服务层与 MyBatis 数据对象。
 */
public interface GovernanceLongTermMemoryRepository {

    /**
     * 保存长期记忆。
     * @param memory 长期记忆对象。
     */
    void save(GovernanceLongTermMemory memory);

    /**
     * 按主键查询未删除长期记忆。
     * @param id 记忆主键。
     * @return 记忆对象，未命中返回 null。
     */
    GovernanceLongTermMemory findById(Long id);

    /**
     * 按确定性去重键查询长期记忆。
     * @param memoryKey 记忆去重键。
     * @return 记忆对象，未命中返回 null。
     */
    GovernanceLongTermMemory findByMemoryKey(String memoryKey);

    /**
     * 查询用户可见记忆。
     * @param userId 用户 ID。
     * @param workspaceId 工作空间 ID，可为空。
     * @param status 状态筛选，可为空。
     * @param limit 最大条数。
     * @return 记忆列表。
     */
    List<GovernanceLongTermMemory> findForUser(Long userId, Long workspaceId, String status, int limit);

    /**
     * 查询管理端记忆列表。
     * @param status 状态筛选，可为空。
     * @param limit 最大条数。
     * @return 记忆列表。
     */
    List<GovernanceLongTermMemory> findForAdmin(String status, int limit);

    /**
     * 查询可参与上下文回注的 ACTIVE 记忆。
     * @param userId 用户 ID。
     * @param workspaceId 工作空间 ID，可为空。
     * @param limit 最大条数。
     * @return ACTIVE 记忆。
     */
    List<GovernanceLongTermMemory> findActiveForContext(Long userId, Long workspaceId, int limit);

    /**
     * 统计用户在指定工作空间的已生效记忆数量。
     * @param userId 用户 ID。
     * @param workspaceId 工作空间 ID，可为空。
     * @return 已生效数量。
     */
    int countActiveByUserAndWorkspace(Long userId, Long workspaceId);
}
