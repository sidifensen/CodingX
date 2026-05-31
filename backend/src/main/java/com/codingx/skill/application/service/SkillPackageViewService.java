package com.codingx.skill.application.service;

import com.codingx.skill.interfaces.response.AdminSkillPackageEntryResponse;
import com.codingx.skill.interfaces.response.AdminSkillPackageFileContentResponse;
import com.codingx.skill.interfaces.response.AdminSkillPackageMigrationSummaryResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 技能包视图服务，统一负责管理端技能包相关响应投影。
 */
@Service
public class SkillPackageViewService {

    /**
     * 将技能包迁移统计转换为管理端响应。
     * @param summary 技能包迁移统计。
     * @return 管理端迁移统计响应。
     */
    public AdminSkillPackageMigrationSummaryResponse toMigrationSummaryResponse(
        AdminChatSkillService.SkillPackageMigrationSummary summary
    ) {
        // 步骤 1：保留迁移总数、成功数和跳过数，便于管理端展示批处理结果。
        // 步骤 2：失败项逐条投影，保留技能主键、编码和中文失败原因。
        return new AdminSkillPackageMigrationSummaryResponse(
            summary.total(),
            summary.migrated(),
            summary.skipped(),
            summary.failures().stream()
                .map(item -> new AdminSkillPackageMigrationSummaryResponse.FailureItem(
                    item.skillId(),
                    item.skillCode(),
                    item.reason()
                ))
                .toList()
        );
    }

    /**
     * 将技能包目录条目转换为管理端资源树响应。
     * @param entries 技能包目录条目。
     * @return 管理端目录树响应列表。
     */
    public List<AdminSkillPackageEntryResponse> toEntryResponses(List<AdminChatSkillService.SkillPackageEntry> entries) {
        // 步骤 1：服务层已经完成目录优先排序，视图服务只保持顺序逐项投影。
        // 步骤 2：目录条目的 size 可为空，前端据 directory 字段区分目录和文件。
        return entries.stream()
            .map(entry -> new AdminSkillPackageEntryResponse(
                entry.path(),
                entry.name(),
                entry.directory(),
                entry.size()
            ))
            .toList();
    }

    /**
     * 将技能包文件预览内容转换为管理端响应。
     * @param content 技能包文件预览内容。
     * @return 管理端文件预览响应。
     */
    public AdminSkillPackageFileContentResponse toFileContentResponse(AdminChatSkillService.SkillPackageFileContent content) {
        // 步骤 1：保留规范化后的文件路径，便于前端定位当前预览文件。
        // 步骤 2：内容可能是文本或图片 data URL，truncated 标记用于提示预览被截断。
        return new AdminSkillPackageFileContentResponse(
            content.path(),
            content.content(),
            content.truncated()
        );
    }
}
