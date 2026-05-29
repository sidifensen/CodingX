package com.codingx.skill.application.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.storage.RustFsSkillPackageClient;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import java.nio.charset.StandardCharsets;
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

    private static final String WEB_ACCESS_SKILL_CODE = "web-access";
    private static final String WEB_ACCESS_DEFAULT_BROWSER = "chrome";
    private static final String WEB_ACCESS_BROWSER_KEY = "WEB_ACCESS_BROWSER";

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

            configureWebAccessDefaultBrowser(skillCode, tempDir);
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

    /**
     * web-access 在云端运行时每轮都会下载到新的临时目录，无法复用上一次的 config.env。
     * 为避免模型每次都停下来询问浏览器偏好，默认绑定当前桌面调试链路使用的 Chrome。
     * @param skillCode skill 编码。
     * @param tempDir skill 临时目录。
     */
    private void configureWebAccessDefaultBrowser(String skillCode, Path tempDir) {
        if (!StrUtil.equalsIgnoreCase(WEB_ACCESS_SKILL_CODE, skillCode) || tempDir == null) {
            return;
        }
        Path configFile = tempDir.resolve("config.env");
        try {
            String content = Files.exists(configFile) ? Files.readString(configFile, StandardCharsets.UTF_8) : "";
            if (content.lines().anyMatch(line -> StrUtil.startWithIgnoreCase(line.trim(), WEB_ACCESS_BROWSER_KEY + "="))) {
                return;
            }
            String separator = content.isBlank() || content.endsWith("\n") || content.endsWith("\r\n")
                ? ""
                : System.lineSeparator();
            String nextContent = content + separator + WEB_ACCESS_BROWSER_KEY + "=" + WEB_ACCESS_DEFAULT_BROWSER + System.lineSeparator();
            Files.writeString(configFile, nextContent, StandardCharsets.UTF_8);
            log.info("web-access 默认浏览器配置已写入: skillCode={}, browser={}, config={}", skillCode, WEB_ACCESS_DEFAULT_BROWSER, configFile);
        } catch (Exception exception) {
            // 配置失败不阻断技能加载；后续 check-deps 会输出明确诊断，便于现场排查。
            log.warn("web-access 默认浏览器配置写入失败: skillCode={}, config={}", skillCode, configFile, exception);
        }
    }
}
