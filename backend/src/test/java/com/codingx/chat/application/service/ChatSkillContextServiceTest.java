package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.codingx.common.storage.RustFsSkillPackageClient;
import com.codingx.skill.application.service.ChatSkillContextService;
import com.codingx.skill.application.service.SkillRuntimeService;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import java.nio.charset.StandardCharsets;
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

    @Mock
    private SkillRuntimeService skillRuntimeService;

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
     * 新增的 web-access 内置技能应同样能从类路径读取 SKILL.md，避免数据库记录存在但上下文无法注入。
     */
    @Test
    void buildSkillContextReadsWebAccessManifestFromClasspath() {
        when(chatSkillRepository.findBySkillCode("web-access")).thenReturn(
            ChatSkill.builder()
                .id(8105L)
                .skillCode("web-access")
                .displayName("联网访问")
                .sourceType("built-in")
                .enabled(1)
                .packageStorageFormat("zip")
                .storageKey(null)
                .build()
        );

        String context = chatSkillContextService.buildSkillContext(List.of("web-access"));

        assertTrue(context.contains("## /web-access（联网访问）"));
        assertTrue(context.contains("所有联网操作必须通过此 skill 处理"));
        assertTrue(context.contains("https://github.com/eze-is/web-access"));
    }

    /**
     * 显式选择技能本身就是用户意图，短句也不能被普通闲聊或关于助手意图吞掉。
     */
    @Test
    void buildSkillContextTreatsSelectedSkillAsActiveInstruction() {
        when(chatSkillRepository.findBySkillCode("web-access")).thenReturn(
            ChatSkill.builder()
                .id(8105L)
                .skillCode("web-access")
                .displayName("联网访问")
                .sourceType("built-in")
                .enabled(1)
                .packageStorageFormat("zip")
                .storageKey(null)
                .build()
        );

        String context = chatSkillContextService.buildSkillContext(List.of("web-access"));

        assertTrue(context.contains("用户已显式选择以下技能"));
        assertTrue(context.contains("不要因为用户正文较短或像闲聊就忽略已选技能"));
        assertTrue(context.contains("技能编码不是可执行工具名"));
        assertTrue(context.contains("禁止把 /skill 或 skill code 当作 tool_call 名称"));
        assertTrue(context.contains("当用户正文使用“这个”“这些”“它”“有什么区别”等指代"));
        assertTrue(context.contains("默认先指向本轮已选技能"));
        assertTrue(context.contains("当用户只问“这是什么”“这是啥”等短指代"));
        assertTrue(context.contains("询问当前引用的已选技能本身"));
        assertTrue(context.contains("简洁解释它能做什么、适合什么场景"));
        assertTrue(context.contains("禁止声称已经读取网页、连接浏览器或完成联网操作"));
        assertTrue(context.contains("如果用户要求执行技能任务但缺少 URL、当前页面、搜索词、附件或其他必要目标"));
        assertTrue(context.contains("只有在可用工具或系统搜索实际完成后"));
    }

    /**
     * 对象存储中的 web-access 技能包仍可能带着上游 curl 示例，CodingX 必须在上下文末尾追加本地运行约束。
     * 运行约束要同时覆盖 PowerShell 语法和“优先复用用户现有浏览器 tab”的业务意图，避免模型反复新建空白 tab。
     */
    @Test
    void buildSkillContextAddsCodingXPowerShellGuidanceForStoredWebAccessManifest() {
        when(chatSkillRepository.findBySkillCode("web-access")).thenReturn(
            ChatSkill.builder()
                .id(8105L)
                .skillCode("web-access")
                .displayName("联网访问")
                .sourceType("uploaded")
                .enabled(1)
                .packageStorageFormat("directory")
                .storageKey("chat-skills/packages/web-access")
                .build()
        );
        when(rustFsSkillPackageClient.downloadDirectoryFile("chat-skills/packages/web-access", "SKILL.md")).thenReturn("""
            ---
            name: web-access
            description: >
              所有联网操作必须通过此 skill 处理。
            ---

            # web-access

            使用 curl -s -X POST 和 --data-raw 调用本地代理服务。
            """.getBytes(StandardCharsets.UTF_8));

        String context = chatSkillContextService.buildSkillContext(List.of("web-access"));

        assertTrue(context.contains("CodingX 运行时约束"));
        assertTrue(context.contains("Windows PowerShell"));
        assertTrue(context.contains("先 `/targets`"));
        assertTrue(context.contains("优先复用 URL 或标题匹配的现有 tab"));
        assertTrue(context.contains("只有 `/targets` 中没有匹配目标时才调用 `/new`"));
        assertTrue(context.contains("必须继续调用 `/info`"));
        assertTrue(context.contains("禁止在这里结束回复"));
        assertTrue(context.contains("Invoke-RestMethod"));
        assertTrue(context.contains("Invoke-WebRequest"));
        assertTrue(context.contains("-Method Post"));
        assertTrue(context.contains("-Body"));
        assertTrue(context.contains("不要照搬上游示例里的 curl -s -X POST 和 --data-raw"));
        assertTrue(context.contains("先调用 node \"$env:CLAUDE_SKILL_DIR\\scripts\\check-deps.mjs\""));
    }

    /**
     * 技能运行时元数据应进入系统提示，帮助模型知道技能声明的工具、资源和脚本边界。
     */
    @Test
    void buildSkillContextAddsRuntimeMetadataSummary() {
        ChatSkill skill = ChatSkill.builder()
            .id(8201L)
            .skillCode("meeting")
            .displayName("会议总结")
            .sourceType("uploaded")
            .enabled(1)
            .packageStorageFormat("directory")
            .storageKey("chat-skills/packages/meeting")
            .build();
        when(chatSkillRepository.findBySkillCode("meeting")).thenReturn(skill);
        when(rustFsSkillPackageClient.downloadDirectoryFile("chat-skills/packages/meeting", "SKILL.md")).thenReturn("""
            ---
            name: meeting
            ---
            # Meeting Skill
            """.getBytes(StandardCharsets.UTF_8));
        when(skillRuntimeService.loadSelectedSkillRuntimes(List.of("meeting"))).thenReturn(List.of(
            new SkillRuntimeService.SkillRuntimeDescriptor(
                "meeting",
                "会议总结",
                "# Meeting Skill",
                new SkillRuntimeService.SkillRuntimeMetadata(
                    "meeting",
                    "Summarize meetings",
                    List.of("ReadFile", "Bash"),
                    List.of("resources/guide.md"),
                    List.of("scripts/check.ps1")
                ),
                List.of("resources/guide.md"),
                List.of("scripts/check.ps1")
            )
        ));

        String context = chatSkillContextService.buildSkillContext(List.of("meeting"));

        assertTrue(context.contains("### 技能运行时元数据"));
        assertTrue(context.contains("工具声明：ReadFile、Bash"));
        assertTrue(context.contains("资源文件：resources/guide.md"));
        assertTrue(context.contains("脚本文件：scripts/check.ps1"));
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
