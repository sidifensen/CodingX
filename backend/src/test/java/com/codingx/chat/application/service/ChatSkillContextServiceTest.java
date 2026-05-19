package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.codingx.common.storage.RustFsSkillPackageClient;
import com.codingx.skill.application.service.ChatSkillContextService;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证技能上下文组装逻辑，确保无对象存储键时内置技能可从类路径读取 SKILL.md。
 */
@ExtendWith(MockitoExtension.class)
class ChatSkillContextServiceTest {

    @Mock
    private ChatSkillRepository chatSkillRepository;

    @Mock
    private RustFsSkillPackageClient rustFsSkillPackageClient;

    @InjectMocks
    private ChatSkillContextService chatSkillContextService;

    /**
     * 内置技能缺少 storageKey 时，应回退读取类路径 skills/<skillCode>/SKILL.md。
     */
    @Test
    void buildSkillContextReadsBuiltInSkillManifestFromClasspathWhenStorageKeyMissing() {
        when(chatSkillRepository.findBySkillCode("web-read")).thenReturn(
            ChatSkill.builder()
                .id(8101L)
                .skillCode("web-read")
                .displayName("网页读取")
                .sourceType("built-in")
                .enabled(1)
                .packageStorageFormat("zip")
                .storageKey(null)
                .build()
        );

        String context = chatSkillContextService.buildSkillContext(List.of("web-read"));

        assertTrue(context.contains("## /web-read（网页读取）"));
        assertTrue(context.contains("name: web-read"));
    }

    /**
     * 非内置技能且无 storageKey 时应跳过，避免脏数据误注入系统提示。
     */
    @Test
    void buildSkillContextSkipsUploadedSkillWithoutStorageKey() {
        when(chatSkillRepository.findBySkillCode("uploaded-empty")).thenReturn(
            ChatSkill.builder()
                .id(9001L)
                .skillCode("uploaded-empty")
                .displayName("上传技能")
                .sourceType("uploaded")
                .enabled(1)
                .packageStorageFormat("directory")
                .storageKey(null)
                .build()
        );

        String context = chatSkillContextService.buildSkillContext(List.of("uploaded-empty"));

        assertEquals("", context);
    }
}
