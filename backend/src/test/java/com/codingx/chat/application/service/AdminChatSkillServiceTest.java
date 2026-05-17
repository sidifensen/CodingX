package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.chat.domain.model.ChatSkill;
import com.codingx.chat.domain.repository.ChatSkillRepository;
import com.codingx.common.exception.BusinessException;
import com.codingx.storage.RustFsSkillPackageClient;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
}

