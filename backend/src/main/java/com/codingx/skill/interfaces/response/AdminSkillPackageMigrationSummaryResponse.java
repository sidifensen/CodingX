package com.codingx.skill.interfaces.response;

import java.util.List;

/**
 * 技能包迁移结果响应，供管理端展示迁移进度与失败项。
 * @param total 参与迁移的技能总数。
 * @param migrated 成功迁移为目录化存储的技能数量。
 * @param skipped 已经是目录格式而跳过的技能数量。
 * @param failures 迁移失败项列表。
 */
public record AdminSkillPackageMigrationSummaryResponse(
    int total, // 本次扫描到需要检查存储形态的技能总数。
    int migrated, // 成功从历史压缩包迁移为目录化存储的技能数量。
    int skipped, // 已经是目录化存储而跳过的技能数量。
    List<FailureItem> failures // 迁移失败项列表，空列表表示没有失败项。
) {

    /**
     * 单个技能迁移失败信息。
     * @param skillId 迁移失败的技能主键。
     * @param skillCode 迁移失败的技能编码。
     * @param reason 返回给管理端展示的中文失败原因。
     */
    public record FailureItem(
        Long skillId, // 迁移失败的技能主键。
        String skillCode, // 迁移失败的技能编码。
        String reason // 返回给管理端展示的中文失败原因。
    ) {
    }
}
