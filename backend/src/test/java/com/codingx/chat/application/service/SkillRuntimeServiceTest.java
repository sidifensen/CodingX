package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.codingx.common.storage.RustFsSkillPackageClient;
import com.codingx.skill.application.service.SkillResourceBoundaryService;
import com.codingx.skill.application.service.SkillRuntimeService;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 验证技能运行时能解析技能包元数据、资源清单并保护资源读取边界。
 */
class SkillRuntimeServiceTest {

    /**
     * 已选择技能应解析 SKILL.md、skill.json、resources 和 scripts，未选择技能不进入运行时。
     */
    @Test
    void loadSelectedSkillRuntimeShouldParseManifestMetadataResourcesAndScripts() {
        ChatSkillRepository repository = Mockito.mock(ChatSkillRepository.class);
        RustFsSkillPackageClient storageClient = Mockito.mock(RustFsSkillPackageClient.class);
        SkillRuntimeService service = new SkillRuntimeService(repository, storageClient);
        when(repository.findBySkillCode("meeting")).thenReturn(directorySkill());
        when(storageClient.downloadDirectoryFile("chat-skills/packages/meeting", "SKILL.md"))
            .thenReturn("name: meeting\n# Meeting Skill".getBytes(StandardCharsets.UTF_8));
        when(storageClient.downloadDirectoryFile("chat-skills/packages/meeting", ".codex-skill/skill.json"))
            .thenReturn("""
                {"name":"meeting","description":"Summarize meetings","tools":["ReadFile"],"resources":["resources/guide.md"],"scripts":["scripts/check.ps1"]}
                """.getBytes(StandardCharsets.UTF_8));
        when(storageClient.listDirectory("chat-skills/packages/meeting")).thenReturn(List.of(
            new RustFsSkillPackageClient.SkillObjectMetadata("chat-skills/packages/meeting/SKILL.md", "SKILL.md", 32L),
            new RustFsSkillPackageClient.SkillObjectMetadata("chat-skills/packages/meeting/resources/guide.md", "resources/guide.md", 48L),
            new RustFsSkillPackageClient.SkillObjectMetadata("chat-skills/packages/meeting/scripts/check.ps1", "scripts/check.ps1", 24L)
        ));

        List<SkillRuntimeService.SkillRuntimeDescriptor> descriptors = service.loadSelectedSkillRuntimes(List.of("meeting", "missing"));

        assertEquals(1, descriptors.size());
        SkillRuntimeService.SkillRuntimeDescriptor descriptor = descriptors.getFirst();
        assertEquals("meeting", descriptor.skillCode());
        assertTrue(descriptor.manifestContent().contains("Meeting Skill"));
        assertEquals("Summarize meetings", descriptor.metadata().description());
        assertEquals(List.of("ReadFile"), descriptor.metadata().tools());
        assertEquals(List.of("resources/guide.md"), descriptor.resources());
        assertEquals(List.of("scripts/check.ps1"), descriptor.scripts());
    }

    /**
     * 资源读取必须限定在技能包目录内，避免模型通过相对路径读取技能外部文件。
     */
    @Test
    void readResourceShouldRejectPathTraversal() {
        RustFsSkillPackageClient storageClient = Mockito.mock(RustFsSkillPackageClient.class);
        SkillResourceBoundaryService boundaryService = new SkillResourceBoundaryService(storageClient);
        ChatSkill skill = directorySkill();
        when(storageClient.downloadDirectoryFile("chat-skills/packages/meeting", "resources/guide.md"))
            .thenReturn("guide".getBytes(StandardCharsets.UTF_8));

        assertEquals("guide", boundaryService.readTextResource(skill, "resources/guide.md"));
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> boundaryService.readTextResource(skill, "../secret.txt")
        );
        assertTrue(exception.getMessage().contains("技能资源路径越界"));
    }

    private ChatSkill directorySkill() {
        return ChatSkill.builder()
            .id(9301L)
            .skillCode("meeting")
            .displayName("会议总结")
            .sourceType("uploaded")
            .enabled(1)
            .packageStorageFormat("directory")
            .storageKey("chat-skills/packages/meeting")
            .build();
    }
}
