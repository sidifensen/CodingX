package com.codingx.governance.domain.repository;

import com.codingx.governance.domain.model.GovernanceProjectProfile;
import java.util.List;

/**
 * 定义项目画像持久化能力。
 */
public interface GovernanceProjectProfileRepository {

    /**
     * 保存项目画像。
     * @param profile 项目画像对象。
     */
    void save(GovernanceProjectProfile profile);

    /**
     * 查询指定工作空间最近一次项目画像。
     * @param workspaceId 工作空间 ID。
     * @return 项目画像，未命中返回 null。
     */
    GovernanceProjectProfile findLatestByWorkspaceId(Long workspaceId);

    /**
     * 查询最近项目画像。
     * @param limit 最大返回条数。
     * @return 项目画像列表。
     */
    List<GovernanceProjectProfile> findRecent(int limit);
}
