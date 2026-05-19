package com.codingx.skill.interfaces.response;

import java.util.List;

/**
 * 定义技能包迁移结果响应，供管理端展示迁移进度与失败项。
 * @param total 参与迁移总数。
 * @param migrated 成功迁移数。
 * @param skipped 已是目录格式跳过数。
 * @param failures 失败项列表。
 */
public record AdminSkillPackageMigrationSummaryResponse(
    int total,
    int migrated,
    int skipped,
    List<FailureItem> failures
) {

    /**
     * 定义单个技能迁移失败信息。
     * @param skillId 技能主键。
     * @param skillCode 技能编码。
     * @param reason 失败原因。
     */
    public record FailureItem(
        Long skillId,
        String skillCode,
        String reason
    ) {
    }
}
