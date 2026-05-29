package com.codingx.skill.application.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.storage.RustFsSkillPackageClient;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 负责将 skill 文件从 RustFS 下载到本地临时目录，供 shell 命令执行时访问。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillLocalCacheService {

    private final ChatSkillRepository chatSkillRepository;
    private final RustFsSkillPackageClient rustFsSkillPackageClient;

    /**
     * 下载 skill 文件到临时目录。
     * @param skillCode skill 编码。
     * @return 临时目录路径。
     */
    public Path downloadSkillToTemp(String skillCode) {
        ChatSkill skill = chatSkillRepository.findBySkillCode(skillCode);
        if (skill == null || StrUtil.isBlank(skill.getStorageKey())) {
            throw new BusinessException(
                "SKILL_NOT_FOUND",
                ErrorMessageCatalog.CHAT_SKILL_NOT_FOUND_PREFIX + skillCode
            );
        }

        // 创建临时目录：<tmpdir>/codingx-skills-<uuid>/<skillCode>/
        Path tempDir = Paths.get(
            System.getProperty("java.io.tmpdir"),
            "codingx-skills-" + UUID.fastUUID().toString(true),
            skillCode
        );

        try {
            Files.createDirectories(tempDir);

            // 从 RustFS 下载所有文件
            List<RustFsSkillPackageClient.SkillObjectMetadata> files =
                rustFsSkillPackageClient.listDirectory(skill.getStorageKey());

            for (RustFsSkillPackageClient.SkillObjectMetadata file : files) {
                byte[] content = rustFsSkillPackageClient.downloadDirectoryFile(
                    skill.getStorageKey(),
                    file.relativePath()
                );
                Path targetFile = tempDir.resolve(file.relativePath());
                if (targetFile.getParent() != null) {
                    Files.createDirectories(targetFile.getParent());
                }
                Files.write(targetFile, content);
            }

            log.info("Skill 文件已下载到临时目录: skillCode={}, tempDir={}", skillCode, tempDir);
            return tempDir;

        } catch (BusinessException exception) {
            // 清理已创建的临时目录
            FileUtil.del(tempDir.toFile());
            throw exception;
        } catch (Exception exception) {
            // 清理已创建的临时目录
            FileUtil.del(tempDir.toFile());
            throw new BusinessException(
                "SKILL_DOWNLOAD_FAILED",
                ErrorMessageCatalog.CHAT_SKILL_DOWNLOAD_FAILED_PREFIX + exception.getMessage()
            );
        }
    }
}
