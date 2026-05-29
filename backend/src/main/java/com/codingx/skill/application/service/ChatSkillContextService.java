package com.codingx.skill.application.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.common.storage.RustFsSkillPackageClient;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;

/**
 * 负责按当前消息选择的技能编码读取技能说明，并组装成可注入模型的上下文提示。
 */
@Service
@RequiredArgsConstructor
public class ChatSkillContextService {

    private static final String ROOT_SKILL_MANIFEST = "SKILL.md";
    private static final String STORAGE_FORMAT_DIRECTORY = "directory";
    private static final String STORAGE_FORMAT_ZIP = "zip";
    private static final String SOURCE_TYPE_BUILT_IN = "built-in";
    private static final String BUILT_IN_SKILL_RESOURCE_ROOT = "skills";
    private static final int MAX_SKILL_CONTEXT_COUNT = 6;
    private static final int MAX_SINGLE_MANIFEST_CHARS = 8_000;
    private static final int MAX_TOTAL_CONTEXT_CHARS = 24_000;

    private final ChatSkillRepository chatSkillRepository;
    private final RustFsSkillPackageClient rustFsSkillPackageClient;

    /**
     * 构建技能上下文提示词，缺失或读取失败的技能会自动跳过。
     * @param selectedSkillCodes 当前消息选择的技能编码。
     * @return 可直接作为系统提示注入模型的文本；无有效技能时返回空字符串。
     */
    public String buildSkillContext(List<String> selectedSkillCodes) {
        if (CollUtil.isEmpty(selectedSkillCodes)) {
            return "";
        }
        LinkedHashSet<String> normalizedCodes = new LinkedHashSet<>();
        for (String skillCode : selectedSkillCodes) {
            if (StrUtil.isNotBlank(skillCode)) {
                normalizedCodes.add(skillCode.trim());
            }
        }
        if (normalizedCodes.isEmpty()) {
            return "";
        }

        StringBuilder contentBuilder = new StringBuilder();
        int loadedSkillCount = 0;
        for (String skillCode : normalizedCodes) {
            if (loadedSkillCount >= MAX_SKILL_CONTEXT_COUNT || contentBuilder.length() >= MAX_TOTAL_CONTEXT_CHARS) {
                break;
            }
            ChatSkill skill = chatSkillRepository.findBySkillCode(skillCode);
            if (skill == null) {
                continue;
            }
            String manifestContent = readSkillManifest(skill);
            if (StrUtil.isBlank(manifestContent)) {
                continue;
            }
            String normalizedManifest = StrUtil.subPre(manifestContent.trim(), MAX_SINGLE_MANIFEST_CHARS);
            if (StrUtil.isBlank(normalizedManifest)) {
                continue;
            }
            contentBuilder
                .append("## /")
                .append(skill.getSkillCode())
                .append("（")
                .append(StrUtil.blankToDefault(skill.getDisplayName(), skill.getSkillCode()))
                .append("）\n")
                .append(normalizedManifest)
                .append("\n\n");
            loadedSkillCount++;
        }

        if (contentBuilder.isEmpty()) {
            return "";
        }
        return """
            用户已显式选择以下技能，这些技能就是本轮任务意图的一部分。
            请优先按已选技能的说明文档判断和执行当前问题，不要因为用户正文较短或像闲聊就忽略已选技能。
            如果缺少 URL、页面、附件或其他必要目标，应围绕已选技能追问缺失信息，或在可用工具允许时先获取上下文；不要转成关于助手或普通闲聊回答。
            若技能内容确实与当前问题无关，只说明缺少可执行目标，不要生硬引用技能文档。

            %s
            """.formatted(contentBuilder.toString().trim());
    }

    /**
     * 读取技能根级 SKILL.md，兼容目录格式与历史 zip 格式。
     * @param skill 技能配置。
     * @return SKILL.md 文本，读取失败返回空字符串。
     */
    private String readSkillManifest(ChatSkill skill) {
        try {
            byte[] bytes = readManifestBytes(skill);
            if (bytes.length == 0 || looksLikeBinary(bytes)) {
                return "";
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * 统一解析技能 SKILL.md 字节，优先对象存储，内置技能缺少 storageKey 时回退类路径技能目录。
     * @param skill 技能配置。
     * @return SKILL.md 字节，缺失返回空数组。
     */
    private byte[] readManifestBytes(ChatSkill skill) {
        if (StrUtil.isNotBlank(skill.getStorageKey())) {
            return isLegacyZipStorage(skill)
                ? readManifestFromZip(skill.getStorageKey())
                : rustFsSkillPackageClient.downloadDirectoryFile(skill.getStorageKey(), ROOT_SKILL_MANIFEST);
        }
        if (isBuiltInSkill(skill)) {
            return readBuiltInManifest(skill.getSkillCode());
        }
        return new byte[0];
    }

    /**
     * 内置技能回退读取：当数据库未配置存储键时，直接读取类路径内置技能目录的 SKILL.md。
     * @param skillCode 技能编码。
     * @return SKILL.md 字节。
     */
    private byte[] readBuiltInManifest(String skillCode) {
        if (StrUtil.isBlank(skillCode)) {
            return new byte[0];
        }
        String resourcePath = BUILT_IN_SKILL_RESOURCE_ROOT + "/" + skillCode + "/" + ROOT_SKILL_MANIFEST;
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            return new byte[0];
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return IoUtil.readBytes(inputStream);
        } catch (IOException ignored) {
            return new byte[0];
        }
    }

    private boolean isBuiltInSkill(ChatSkill skill) {
        return StrUtil.equalsIgnoreCase(StrUtil.blankToDefault(skill.getSourceType(), ""), SOURCE_TYPE_BUILT_IN);
    }

    private boolean isLegacyZipStorage(ChatSkill skill) {
        String storageFormat = StrUtil.blankToDefault(skill.getPackageStorageFormat(), "").trim().toLowerCase(Locale.ROOT);
        if (StrUtil.equals(storageFormat, STORAGE_FORMAT_DIRECTORY)) {
            return false;
        }
        if (StrUtil.equals(storageFormat, STORAGE_FORMAT_ZIP)) {
            return true;
        }
        String storageKey = StrUtil.blankToDefault(skill.getStorageKey(), "").toLowerCase(Locale.ROOT);
        return storageKey.endsWith(".zip") || storageKey.endsWith(".skill");
    }

    private byte[] readManifestFromZip(String storageKey) {
        byte[] archiveBytes = rustFsSkillPackageClient.download(storageKey);
        byte[] nestedManifestBytes = null;
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(archiveBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String normalizedPath = normalizeArchivePath(entry.getName());
                if (StrUtil.isBlank(normalizedPath)) {
                    continue;
                }
                byte[] entryBytes = IoUtil.readBytes(zipInputStream, false);
                if (StrUtil.equalsIgnoreCase(normalizedPath, ROOT_SKILL_MANIFEST)) {
                    return entryBytes;
                }
                if (nestedManifestBytes == null && StrUtil.endWithIgnoreCase(normalizedPath, "/" + ROOT_SKILL_MANIFEST)) {
                    nestedManifestBytes = entryBytes;
                }
            }
        } catch (IOException exception) {
            return new byte[0];
        }
        return nestedManifestBytes == null ? new byte[0] : nestedManifestBytes;
    }

    private String normalizeArchivePath(String originalPath) {
        String normalizedPath = StrUtil.blankToDefault(originalPath, "")
            .replace("\\", "/")
            .trim();
        while (StrUtil.startWith(normalizedPath, "./")) {
            normalizedPath = normalizedPath.substring(2);
        }
        normalizedPath = StrUtil.removePrefix(normalizedPath, "/");
        normalizedPath = StrUtil.removeSuffix(normalizedPath, "/");
        return normalizedPath;
    }

    private boolean looksLikeBinary(byte[] bytes) {
        int sampleLength = Math.min(bytes.length, 1024);
        for (int index = 0; index < sampleLength; index++) {
            if (bytes[index] == 0) {
                return true;
            }
        }
        return false;
    }
}
