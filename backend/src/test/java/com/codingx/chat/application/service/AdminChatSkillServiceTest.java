package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.skill.application.service.AdminChatSkillService;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.common.exception.BusinessException;
import com.codingx.storage.RustFsSkillPackageClient;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 验证管理端技能上传服务的核心业务流程。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatSkillServiceTest {

    @Mock
    private ChatSkillRepository chatSkillRepository;

    @Mock
    private RustFsSkillPackageClient rustFsSkillPackageClient;

    @InjectMocks
    private AdminChatSkillService adminChatSkillService;

    /**
     * 分页查询应透传仓储分页结果，保持 records/total/current/size/pages 契约。
     */
    @Test
    void pageSkillsReturnsRepositoryPageResult() {
        PageResult<ChatSkill> repositoryPage = PageResult.<ChatSkill>builder()
            .records(List.of(ChatSkill.builder()
                .id(7101L)
                .skillCode("conversation-core")
                .displayName("会话核心")
                .build()))
            .total(1L)
            .size(10L)
            .current(1L)
            .pages(1L)
            .build();
        when(chatSkillRepository.pageQuery(1, 10)).thenReturn(repositoryPage);

        PageResult<ChatSkill> pageResult = adminChatSkillService.pageSkills(1, 10);

        assertEquals(1L, pageResult.total());
        assertEquals(1L, pageResult.current());
        assertEquals(1, pageResult.records().size());
        assertEquals("conversation-core", pageResult.records().getFirst().getSkillCode());
    }

    /**
     * 上传合法技能包时应解析 SKILL.md 并写入对象存储与数据库。
     */
    @Test
    void uploadSkillPackageParsesSkillManifestAndPersists() {
        when(chatSkillRepository.findBySkillCode("pdf-processing")).thenReturn(null);
        when(rustFsSkillPackageClient.upload(any(), eq("pdf-processing.skill"))).thenReturn("skills/packages/pdf-processing-v1.skill");

        try (MockedStatic<StpUtil> stpUtilMockedStatic = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            stpUtilMockedStatic.when(StpUtil::getLoginIdAsLong).thenReturn(9527L);
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "pdf-processing.skill",
                "application/octet-stream",
                buildSkillZip("pdf-processing", "处理 PDF 文档")
            );

            ChatSkill saved = adminChatSkillService.uploadSkillPackage(file, null);

            assertNotNull(saved);
            assertEquals("pdf-processing", saved.getSkillCode());
            assertEquals("pdf-processing", saved.getDisplayName());
            assertEquals("处理 PDF 文档", saved.getDescription());
            assertEquals("uploaded", saved.getSourceType());
            verify(chatSkillRepository).save(any(ChatSkill.class));
        }
    }

    /**
     * 缺少根级 SKILL.md 时应返回业务异常。
     */
    @Test
    void uploadSkillPackageThrowsWhenSkillManifestMissing() {
        try (MockedStatic<StpUtil> stpUtilMockedStatic = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            stpUtilMockedStatic.when(StpUtil::getLoginIdAsLong).thenReturn(9527L);
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "broken.skill",
                "application/octet-stream",
                buildZipWithoutSkillManifest()
            );

            assertThrows(BusinessException.class, () -> adminChatSkillService.uploadSkillPackage(file, null));
        }
    }

    /**
     * 已上传技能应支持读取包内目录树，目录在前、文件在后。
     */
    @Test
    void listPackageEntriesReturnsDirectoriesAndFiles() {
        ChatSkill uploadedSkill = ChatSkill.builder()
            .id(7101L)
            .skillCode("meeting-notes")
            .displayName("meeting-notes")
            .storageKey("chat-skills/packages/meeting-notes.zip")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .deleted(0)
            .build();
        when(chatSkillRepository.findById(7101L)).thenReturn(uploadedSkill);
        when(rustFsSkillPackageClient.download("chat-skills/packages/meeting-notes.zip"))
            .thenReturn(buildSkillZipWithNestedFiles());

        List<AdminChatSkillService.SkillPackageEntry> entries = adminChatSkillService.listPackageEntries(7101L);

        assertTrue(entries.stream().anyMatch(entry -> entry.directory() && "templates".equals(entry.path())));
        assertTrue(entries.stream().anyMatch(entry -> !entry.directory() && "SKILL.md".equals(entry.path())));
        assertTrue(entries.stream().anyMatch(entry -> !entry.directory() && "templates/prompt.txt".equals(entry.path())));
    }

    /**
     * 包内文本文件预览应返回内容并标记是否截断。
     */
    @Test
    void readPackageFileContentReturnsPlainTextContent() {
        ChatSkill uploadedSkill = ChatSkill.builder()
            .id(7102L)
            .skillCode("meeting-notes")
            .displayName("meeting-notes")
            .storageKey("chat-skills/packages/meeting-notes.zip")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .deleted(0)
            .build();
        when(chatSkillRepository.findById(7102L)).thenReturn(uploadedSkill);
        when(rustFsSkillPackageClient.download("chat-skills/packages/meeting-notes.zip"))
            .thenReturn(buildSkillZipWithNestedFiles());

        AdminChatSkillService.SkillPackageFileContent content = adminChatSkillService.readPackageFileContent(7102L, "templates/prompt.txt");

        assertEquals("templates/prompt.txt", content.path());
        assertEquals("prompt-template", content.content());
        assertEquals(false, content.truncated());
    }

    /**
     * 二进制文件不允许在线预览，避免前端展示乱码。
     */
    @Test
    void readPackageFileContentThrowsWhenBinaryFile() {
        ChatSkill uploadedSkill = ChatSkill.builder()
            .id(7103L)
            .skillCode("meeting-notes")
            .displayName("meeting-notes")
            .storageKey("chat-skills/packages/meeting-notes.zip")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .deleted(0)
            .build();
        when(chatSkillRepository.findById(7103L)).thenReturn(uploadedSkill);
        when(rustFsSkillPackageClient.download("chat-skills/packages/meeting-notes.zip"))
            .thenReturn(buildSkillZipWithBinaryFile());

        assertThrows(BusinessException.class, () -> adminChatSkillService.readPackageFileContent(7103L, "assets/logo.png"));
    }

    private byte[] buildSkillZip(String name, String description) {
        String skillMarkdown = """
            ---
            name: %s
            description: %s
            ---
            # %s
            """.formatted(name, description, name);
        return buildZipWithSkillManifest(skillMarkdown);
    }

    private byte[] buildZipWithSkillManifest(String skillMarkdown) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                zipOutputStream.putNextEntry(new ZipEntry("SKILL.md"));
                zipOutputStream.write(skillMarkdown.getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new RuntimeException("构造测试压缩包失败", exception);
        }
    }

    private byte[] buildZipWithoutSkillManifest() {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                zipOutputStream.putNextEntry(new ZipEntry("README.md"));
                zipOutputStream.write("# no skill manifest".getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new RuntimeException("构造测试压缩包失败", exception);
        }
    }

    private byte[] buildSkillZipWithNestedFiles() {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                zipOutputStream.putNextEntry(new ZipEntry("SKILL.md"));
                zipOutputStream.write("""
                    ---
                    name: meeting-notes
                    description: meeting notes skill
                    ---
                    # meeting-notes
                    """.getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();

                zipOutputStream.putNextEntry(new ZipEntry("templates/prompt.txt"));
                zipOutputStream.write("prompt-template".getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();

                zipOutputStream.putNextEntry(new ZipEntry("docs/readme.md"));
                zipOutputStream.write("# docs".getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new RuntimeException("构造测试压缩包失败", exception);
        }
    }

    private byte[] buildSkillZipWithBinaryFile() {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                zipOutputStream.putNextEntry(new ZipEntry("SKILL.md"));
                zipOutputStream.write("""
                    ---
                    name: binary-skill
                    description: binary test skill
                    ---
                    # binary-skill
                    """.getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();

                zipOutputStream.putNextEntry(new ZipEntry("assets/logo.png"));
                zipOutputStream.write(new byte[] {1, 2, 0, 3, 4});
                zipOutputStream.closeEntry();
            }
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new RuntimeException("构造测试压缩包失败", exception);
        }
    }
}
