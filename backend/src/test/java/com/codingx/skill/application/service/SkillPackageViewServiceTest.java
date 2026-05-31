package com.codingx.skill.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.codingx.skill.interfaces.response.AdminSkillPackageEntryResponse;
import com.codingx.skill.interfaces.response.AdminSkillPackageFileContentResponse;
import com.codingx.skill.interfaces.response.AdminSkillPackageMigrationSummaryResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证技能包视图服务统一生成管理端技能包响应。
 */
class SkillPackageViewServiceTest {

    private final SkillPackageViewService skillPackageViewService = new SkillPackageViewService();

    /**
     * 迁移统计响应应保留总数、成功数、跳过数和失败明细。
     */
    @Test
    void toMigrationSummaryResponseProjectsFailures() {
        AdminChatSkillService.SkillPackageMigrationSummary summary = new AdminChatSkillService.SkillPackageMigrationSummary(
            3,
            2,
            1,
            List.of(new AdminChatSkillService.SkillPackageMigrationFailure(7101L, "legacy-skill", "迁移失败"))
        );

        AdminSkillPackageMigrationSummaryResponse response = skillPackageViewService.toMigrationSummaryResponse(summary);

        assertEquals(3, response.total());
        assertEquals(2, response.migrated());
        assertEquals(1, response.skipped());
        assertEquals(7101L, response.failures().getFirst().skillId());
        assertEquals("legacy-skill", response.failures().getFirst().skillCode());
        assertEquals("迁移失败", response.failures().getFirst().reason());
    }

    /**
     * 目录树响应应保持服务层返回的路径、名称、目录标记和文件大小。
     */
    @Test
    void toEntryResponsesProjectsPackageEntries() {
        List<AdminChatSkillService.SkillPackageEntry> entries = List.of(
            new AdminChatSkillService.SkillPackageEntry("templates", "templates", true, null),
            new AdminChatSkillService.SkillPackageEntry("templates/prompt.txt", "prompt.txt", false, 128L)
        );

        List<AdminSkillPackageEntryResponse> responses = skillPackageViewService.toEntryResponses(entries);

        assertEquals("templates", responses.getFirst().path());
        assertEquals(true, responses.getFirst().directory());
        assertEquals("prompt.txt", responses.get(1).name());
        assertEquals(128L, responses.get(1).size());
    }

    /**
     * 文件预览响应应保留内容与截断标记，避免 Controller 重复拆字段。
     */
    @Test
    void toFileContentResponseProjectsPreviewContent() {
        AdminChatSkillService.SkillPackageFileContent content = new AdminChatSkillService.SkillPackageFileContent(
            "templates/prompt.txt",
            "prompt-content",
            false
        );

        AdminSkillPackageFileContentResponse response = skillPackageViewService.toFileContentResponse(content);

        assertEquals("templates/prompt.txt", response.path());
        assertEquals("prompt-content", response.content());
        assertEquals(false, response.truncated());
    }
}
