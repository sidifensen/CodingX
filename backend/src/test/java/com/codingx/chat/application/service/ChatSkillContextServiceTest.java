package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.codingx.common.storage.RustFsSkillPackageClient;
import com.codingx.skill.application.service.ChatSkillContextService;
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
        assertTrue(context.contains("当用户只问“这是什么”“这是啥”“介绍一下”“有什么用”等短句"));
        assertTrue(context.contains("直接概括该技能用途、典型场景和限制"));
        assertTrue(context.contains("如果用户明确要求执行技能任务但缺少 URL、页面、附件或其他必要目标"));
    }

    /**
     * 对象存储中的 web-access 技能包仍可能带着上游 curl 示例，CodingX 必须在上下文末尾追加本地运行约束，
     * 避免模型继续照搬 `curl --data-raw` 这类在 Windows PowerShell 中不稳定的写法。
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
        assertTrue(context.contains("Invoke-RestMethod"));
        assertTrue(context.contains("Invoke-WebRequest"));
        assertTrue(context.contains("-Method Post"));
        assertTrue(context.contains("-Body"));
        assertTrue(context.contains("不要照搬上游示例里的 curl -s -X POST 和 --data-raw"));
        assertTrue(context.contains("先调用 node \"$env:CLAUDE_SKILL_DIR\\scripts\\check-deps.mjs\""));
    }

    /**
     * 用户只问“这是什么”时，后端应能直接用技能简介生成确定性回答，不再交给模型猜测指代对象。
     */
    @Test
    void buildSkillIntroReplySummarizesSelectedSkillForShortDeicticQuestion() {
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

        String reply = chatSkillContextService.buildSkillIntroReply(List.of("web-access"));

        assertTrue(reply.contains("这是你当前选中的技能"));
        assertTrue(reply.contains("`web-access`（联网访问）"));
        assertTrue(reply.contains("所有联网操作必须通过此 skill 处理"));
        assertTrue(reply.contains("如果要执行它"));
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
