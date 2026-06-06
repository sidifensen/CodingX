package com.codingx.skill.application.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.common.storage.RustFsSkillPackageClient;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * 技能运行时解析服务。
 * <p>
 * 业务意图：把用户显式选择的技能包从“单段 SKILL.md 文本”升级为可诊断的运行时描述，
 * 包含 prompt、工具声明、资源清单和脚本清单，供聊天上下文和管理端诊断复用。
 */
@Service
@RequiredArgsConstructor
public class SkillRuntimeService {

    /** 根级技能说明文件。 */
    private static final String ROOT_SKILL_MANIFEST = "SKILL.md";

    /** Codex 风格技能元数据文件。 */
    private static final String CODEX_SKILL_METADATA = ".codex-skill/skill.json";

    /** 目录化技能包格式。 */
    private static final String STORAGE_FORMAT_DIRECTORY = "directory";

    /** 历史 zip 技能包格式。 */
    private static final String STORAGE_FORMAT_ZIP = "zip";

    /** 内置技能资源根目录。 */
    private static final String BUILT_IN_SKILL_RESOURCE_ROOT = "skills";

    /** 内置技能来源类型。 */
    private static final String SOURCE_TYPE_BUILT_IN = "built-in";

    /** 技能仓储，用于按用户选择的技能编码读取技能配置。 */
    private final ChatSkillRepository chatSkillRepository;

    /** 技能包对象存储客户端，用于读取目录化或历史压缩包内容。 */
    private final RustFsSkillPackageClient rustFsSkillPackageClient;

    /**
     * 加载本轮已选择技能的运行时描述。
     *
     * @param selectedSkillCodes 用户显式选择的技能编码。
     * @return 已成功解析的技能运行时列表，保持用户选择顺序。
     */
    public List<SkillRuntimeDescriptor> loadSelectedSkillRuntimes(List<String> selectedSkillCodes) {
        LinkedHashSet<String> normalizedCodes = normalizeSelectedSkillCodes(selectedSkillCodes);
        if (normalizedCodes.isEmpty()) {
            return List.of();
        }
        List<SkillRuntimeDescriptor> descriptors = new ArrayList<>();
        for (String skillCode : normalizedCodes) {
            ChatSkill skill = chatSkillRepository.findBySkillCode(skillCode);
            if (skill == null || StrUtil.isBlank(skill.getSkillCode())) {
                continue;
            }
            SkillRuntimeDescriptor descriptor = loadSkillRuntime(skill);
            if (descriptor != null) {
                descriptors.add(descriptor);
            }
        }
        return descriptors;
    }

    private SkillRuntimeDescriptor loadSkillRuntime(ChatSkill skill) {
        String manifestContent = readTextFile(skill, ROOT_SKILL_MANIFEST);
        if (StrUtil.isBlank(manifestContent)) {
            return null;
        }
        SkillRuntimeMetadata metadata = parseMetadata(readTextFile(skill, CODEX_SKILL_METADATA));
        List<String> packageEntries = listPackageEntries(skill);
        return new SkillRuntimeDescriptor(
            skill.getSkillCode(),
            StrUtil.blankToDefault(skill.getDisplayName(), skill.getSkillCode()),
            manifestContent,
            metadata,
            packageEntries.stream().filter(path -> path.startsWith("resources/")).toList(),
            packageEntries.stream().filter(path -> path.startsWith("scripts/")).toList()
        );
    }

    private LinkedHashSet<String> normalizeSelectedSkillCodes(List<String> selectedSkillCodes) {
        LinkedHashSet<String> normalizedCodes = new LinkedHashSet<>();
        if (CollUtil.isEmpty(selectedSkillCodes)) {
            return normalizedCodes;
        }
        for (String skillCode : selectedSkillCodes) {
            if (StrUtil.isNotBlank(skillCode)) {
                normalizedCodes.add(skillCode.trim());
            }
        }
        return normalizedCodes;
    }

    private String readTextFile(ChatSkill skill, String relativePath) {
        try {
            byte[] bytes = readFileBytes(skill, relativePath);
            if (bytes.length == 0 || looksLikeBinary(bytes)) {
                return "";
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private byte[] readFileBytes(ChatSkill skill, String relativePath) {
        if (StrUtil.isNotBlank(skill.getStorageKey())) {
            return isLegacyZipStorage(skill)
                ? readFileFromZip(skill.getStorageKey(), relativePath)
                : rustFsSkillPackageClient.downloadDirectoryFile(skill.getStorageKey(), relativePath);
        }
        if (isBuiltInSkill(skill)) {
            return readBuiltInFile(skill.getSkillCode(), relativePath);
        }
        return new byte[0];
    }

    private byte[] readBuiltInFile(String skillCode, String relativePath) {
        if (StrUtil.isBlank(skillCode) || StrUtil.isBlank(relativePath)) {
            return new byte[0];
        }
        String resourcePath = BUILT_IN_SKILL_RESOURCE_ROOT + "/" + skillCode + "/" + relativePath;
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            return new byte[0];
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return IoUtil.readBytes(inputStream);
        } catch (IOException exception) {
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

    private byte[] readFileFromZip(String storageKey, String targetPath) {
        byte[] archiveBytes = rustFsSkillPackageClient.download(storageKey);
        String normalizedTarget = normalizeArchivePath(targetPath);
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(archiveBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String normalizedPath = normalizeArchivePath(entry.getName());
                if (StrUtil.equalsIgnoreCase(normalizedPath, normalizedTarget)) {
                    return IoUtil.readBytes(zipInputStream, false);
                }
            }
        } catch (IOException exception) {
            return new byte[0];
        }
        return new byte[0];
    }

    private List<String> listPackageEntries(ChatSkill skill) {
        if (StrUtil.isBlank(skill.getStorageKey()) || isLegacyZipStorage(skill)) {
            return List.of();
        }
        try {
            return rustFsSkillPackageClient.listDirectory(skill.getStorageKey()).stream()
                .map(RustFsSkillPackageClient.SkillObjectMetadata::relativePath)
                .filter(StrUtil::isNotBlank)
                .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private SkillRuntimeMetadata parseMetadata(String metadataJson) {
        if (StrUtil.isBlank(metadataJson)) {
            return SkillRuntimeMetadata.empty();
        }
        try {
            JSONObject object = JSONUtil.parseObj(metadataJson);
            return new SkillRuntimeMetadata(
                object.getStr("name"),
                object.getStr("description"),
                readStringList(object.getJSONArray("tools")),
                readStringList(object.getJSONArray("resources")),
                readStringList(object.getJSONArray("scripts"))
            );
        } catch (RuntimeException exception) {
            return SkillRuntimeMetadata.empty();
        }
    }

    private List<String> readStringList(JSONArray array) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : array) {
            String value = String.valueOf(item);
            if (StrUtil.isNotBlank(value)) {
                values.add(value);
            }
        }
        return values;
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

    /**
     * 单个技能运行时描述。
     *
     * @param skillCode 技能编码。
     * @param displayName 技能展示名。
     * @param manifestContent SKILL.md 内容。
     * @param metadata .codex-skill/skill.json 解析结果。
     * @param resources resources 目录下的资源路径。
     * @param scripts scripts 目录下的脚本路径。
     */
    public record SkillRuntimeDescriptor(
        String skillCode, // 技能编码。
        String displayName, // 技能展示名。
        String manifestContent, // SKILL.md 内容。
        SkillRuntimeMetadata metadata, // 技能包元数据。
        List<String> resources, // resources 目录下的资源路径。
        List<String> scripts // scripts 目录下的脚本路径。
    ) {
    }

    /**
     * 技能运行时元数据。
     *
     * @param name 技能声明名称。
     * @param description 技能声明说明。
     * @param tools 技能声明可用工具名。
     * @param resources 技能声明资源路径。
     * @param scripts 技能声明脚本路径。
     */
    public record SkillRuntimeMetadata(
        String name, // 技能声明名称。
        String description, // 技能声明说明。
        List<String> tools, // 技能声明可用工具名。
        List<String> resources, // 技能声明资源路径。
        List<String> scripts // 技能声明脚本路径。
    ) {

        /**
         * 返回空元数据对象，避免调用方重复判空。
         * @return 空元数据。
         */
        public static SkillRuntimeMetadata empty() {
            return new SkillRuntimeMetadata(null, null, List.of(), List.of(), List.of());
        }
    }
}
