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
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.common.exception.BusinessException;
import com.codingx.skill.application.service.AdminChatSkillService;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.common.storage.RustFsSkillPackageClient;
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
     * 上传合法技能包时应解析 SKILL.md 并写入目录化对象存储与数据库。
     */
    @Test
    void uploadSkillPackageParsesSkillManifestAndPersistsDirectory() {
        when(chatSkillRepository.findBySkillCode("pdf-processing")).thenReturn(null);
        when(rustFsSkillPackageClient.uploadDirectory(any(), eq("pdf-processing")))
            .thenReturn("chat-skills/packages/pdf-processing-100");

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
            assertEquals("directory", saved.getPackageStorageFormat());
            assertEquals("pdf-processing", saved.getPackageFileName());
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
     * 目录上传时应保留文件路径并以目录方式落库。
     */
    @Test
    void uploadSkillPackageWithFolderFilesPersistsDirectoryStorage() {
        when(chatSkillRepository.findBySkillCode("meeting-notes")).thenReturn(null);
        when(rustFsSkillPackageClient.uploadDirectory(any(), eq("meeting-notes")))
            .thenReturn("chat-skills/packages/meeting-notes-101");

        try (MockedStatic<StpUtil> stpUtilMockedStatic = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            stpUtilMockedStatic.when(StpUtil::getLoginIdAsLong).thenReturn(9527L);
            MockMultipartFile skillManifest = new MockMultipartFile(
                "files",
                "SKILL.md",
                "text/markdown",
                """
                    ---
                    name: meeting-notes
                    description: meeting notes skill
                    ---
                    # meeting-notes
                    """.getBytes(StandardCharsets.UTF_8)
            );
            MockMultipartFile promptTemplate = new MockMultipartFile(
                "files",
                "templates/prompt.txt",
                "text/plain",
                "prompt-template".getBytes(StandardCharsets.UTF_8)
            );

            ChatSkill saved = adminChatSkillService.uploadSkillPackage(null, List.of(skillManifest, promptTemplate), "文档处理");

            assertEquals("meeting-notes", saved.getSkillCode());
            assertEquals("directory", saved.getPackageStorageFormat());
            assertEquals("folder-upload", saved.getPackageFileName());
            verify(rustFsSkillPackageClient).uploadDirectory(any(), eq("meeting-notes"));
        }
    }

    /**
     * 历史 zip 技能预览目录时应先自动迁移，再返回目录树。
     */
    @Test
    void listPackageEntriesMigratesLegacyZipBeforeListing() {
        ChatSkill legacySkill = ChatSkill.builder()
            .id(7101L)
            .skillCode("meeting-notes")
            .displayName("meeting-notes")
            .storageKey("chat-skills/packages/meeting-notes.zip")
            .packageStorageFormat("zip")
            .packageFileName("meeting-notes.zip")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .deleted(0)
            .build();
        when(chatSkillRepository.findById(7101L)).thenReturn(legacySkill);
        when(rustFsSkillPackageClient.download("chat-skills/packages/meeting-notes.zip"))
            .thenReturn(buildSkillZipWithNestedFiles());
        when(rustFsSkillPackageClient.uploadDirectory(any(), eq("meeting-notes")))
            .thenReturn("chat-skills/packages/meeting-notes-200");
        when(rustFsSkillPackageClient.listDirectory("chat-skills/packages/meeting-notes-200"))
            .thenReturn(List.of(
                new RustFsSkillPackageClient.SkillObjectMetadata(
                    "chat-skills/packages/meeting-notes-200/SKILL.md",
                    "SKILL.md",
                    128L
                ),
                new RustFsSkillPackageClient.SkillObjectMetadata(
                    "chat-skills/packages/meeting-notes-200/templates/prompt.txt",
                    "templates/prompt.txt",
                    64L
                )
            ));

        List<AdminChatSkillService.SkillPackageEntry> entries = adminChatSkillService.listPackageEntries(7101L);

        assertTrue(entries.stream().anyMatch(entry -> entry.directory() && "templates".equals(entry.path())));
        assertTrue(entries.stream().anyMatch(entry -> !entry.directory() && "SKILL.md".equals(entry.path())));
        verify(rustFsSkillPackageClient).deleteObject("chat-skills/packages/meeting-notes.zip");
        verify(chatSkillRepository).save(org.mockito.ArgumentMatchers.argThat(skill ->
            "meeting-notes".equals(skill.getPackageFileName())
                && "directory".equals(skill.getPackageStorageFormat())
        ));
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
            .storageKey("chat-skills/packages/meeting-notes-201")
            .packageStorageFormat("directory")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .deleted(0)
            .build();
        when(chatSkillRepository.findById(7102L)).thenReturn(uploadedSkill);
        when(rustFsSkillPackageClient.downloadDirectoryFile("chat-skills/packages/meeting-notes-201", "templates/prompt.txt"))
            .thenReturn("prompt-template".getBytes(StandardCharsets.UTF_8));

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
            .storageKey("chat-skills/packages/meeting-notes-202")
            .packageStorageFormat("directory")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .deleted(0)
            .build();
        when(chatSkillRepository.findById(7103L)).thenReturn(uploadedSkill);
        when(rustFsSkillPackageClient.downloadDirectoryFile("chat-skills/packages/meeting-notes-202", "assets/logo.png"))
            .thenReturn(new byte[] {1, 2, 0, 3, 4});

        assertThrows(BusinessException.class, () -> adminChatSkillService.readPackageFileContent(7103L, "assets/logo.png"));
    }

    /**
     * 批量迁移接口应覆盖对象存储中的全部技能（含 built-in），并统计迁移成功与跳过数量。
     */
    @Test
    void migrateUploadedSkillPackagesReturnsSummary() {
        ChatSkill legacySkill = ChatSkill.builder()
            .id(7105L)
            .skillCode("legacy-skill")
            .displayName("legacy-skill")
            .sourceType("uploaded")
            .storageKey("chat-skills/packages/legacy-skill.zip")
            .packageStorageFormat("zip")
            .packageFileName("legacy-skill.zip")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .deleted(0)
            .build();
        ChatSkill directorySkill = ChatSkill.builder()
            .id(7106L)
            .skillCode("new-skill")
            .displayName("new-skill")
            .sourceType("uploaded")
            .storageKey("chat-skills/packages/new-skill-300")
            .packageStorageFormat("directory")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .deleted(0)
            .build();
        ChatSkill builtInLegacySkill = ChatSkill.builder()
            .id(7107L)
            .skillCode("builtin-legacy")
            .displayName("builtin-legacy")
            .sourceType("built-in")
            .storageKey("chat-skills/packages/builtin-legacy.skill")
            .packageStorageFormat("zip")
            .packageFileName("builtin-legacy.skill")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .deleted(0)
            .build();
        ChatSkill noStorageKeySkill = ChatSkill.builder()
            .id(7108L)
            .skillCode("no-storage-key")
            .displayName("no-storage-key")
            .sourceType("uploaded")
            .storageKey(null)
            .packageStorageFormat("zip")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .deleted(0)
            .build();
        when(chatSkillRepository.findAll()).thenReturn(List.of(legacySkill, directorySkill, builtInLegacySkill, noStorageKeySkill));
        when(rustFsSkillPackageClient.download("chat-skills/packages/legacy-skill.zip"))
            .thenReturn(buildSkillZip("legacy-skill", "legacy description"));
        when(rustFsSkillPackageClient.download("chat-skills/packages/builtin-legacy.skill"))
            .thenReturn(buildSkillZip("builtin-legacy", "builtin legacy description"));
        when(rustFsSkillPackageClient.uploadDirectory(any(), eq("legacy-skill")))
            .thenReturn("chat-skills/packages/legacy-skill-301");
        when(rustFsSkillPackageClient.uploadDirectory(any(), eq("builtin-legacy")))
            .thenReturn("chat-skills/packages/builtin-legacy-302");

        AdminChatSkillService.SkillPackageMigrationSummary summary = adminChatSkillService.migrateUploadedSkillPackages();

        assertEquals(3, summary.total());
        assertEquals(2, summary.migrated());
        assertEquals(1, summary.skipped());
        assertEquals(0, summary.failures().size());
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
}
