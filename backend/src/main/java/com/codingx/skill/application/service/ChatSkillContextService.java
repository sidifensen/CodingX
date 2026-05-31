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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;

/**
 * 负责按当前消息选择的技能编码读取技能说明，并组装成可注入模型的上下文提示。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSkillContextService {

    private static final String ROOT_SKILL_MANIFEST = "SKILL.md";
    private static final String STORAGE_FORMAT_DIRECTORY = "directory";
    private static final String STORAGE_FORMAT_ZIP = "zip";
    private static final String SOURCE_TYPE_BUILT_IN = "built-in";
    private static final String BUILT_IN_SKILL_RESOURCE_ROOT = "skills";
    private static final int MAX_SKILL_CONTEXT_COUNT = 6;
    private static final int MAX_SINGLE_MANIFEST_CHARS = 8_000;
    private static final int MAX_TOTAL_CONTEXT_CHARS = 24_000;
    private static final int MAX_SKILL_INTRO_DESCRIPTION_CHARS = 260;

    private final ChatSkillRepository chatSkillRepository;
    private final RustFsSkillPackageClient rustFsSkillPackageClient;

    /**
     * 构建技能上下文提示词，缺失或读取失败的技能会自动跳过。
     * @param selectedSkillCodes 当前消息选择的技能编码。
     * @return 可直接作为系统提示注入模型的文本；无有效技能时返回空字符串。
     */
    public String buildSkillContext(List<String> selectedSkillCodes) {
        // 步骤 1：去重并标准化用户选择的技能编码，没有有效编码时不注入任何技能提示。
        LinkedHashSet<String> normalizedCodes = normalizeSelectedSkillCodes(selectedSkillCodes);
        if (normalizedCodes.isEmpty()) {
            return "";
        }

        // 步骤 2：按顺序加载技能配置和 SKILL.md，超过数量或字符上限的技能进入跳过列表。
        StringBuilder contentBuilder = new StringBuilder();
        int loadedSkillCount = 0;
        List<String> loadedSkillCodes = new ArrayList<>();
        List<String> skippedSkillCodes = new ArrayList<>();
        for (String skillCode : normalizedCodes) {
            if (loadedSkillCount >= MAX_SKILL_CONTEXT_COUNT || contentBuilder.length() >= MAX_TOTAL_CONTEXT_CHARS) {
                skippedSkillCodes.add(skillCode + ":超过上下文上限");
                continue;
            }
            ChatSkill skill = chatSkillRepository.findBySkillCode(skillCode);
            if (skill == null) {
                skippedSkillCodes.add(skillCode + ":配置不存在");
                continue;
            }
            String manifestContent = readSkillManifest(skill);
            if (StrUtil.isBlank(manifestContent)) {
                skippedSkillCodes.add(skillCode + ":SKILL.md为空或读取失败");
                continue;
            }
            String normalizedManifest = StrUtil.subPre(manifestContent.trim(), MAX_SINGLE_MANIFEST_CHARS);
            if (StrUtil.isBlank(normalizedManifest)) {
                skippedSkillCodes.add(skillCode + ":SKILL.md为空");
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
            loadedSkillCodes.add(skill.getSkillCode());
        }

        // 步骤 3：没有任何技能成功加载时返回空字符串并打印跳过原因，避免注入空模板。
        if (contentBuilder.isEmpty()) {
            log.warn("技能上下文未生效: 选择技能={}, 跳过技能={}", normalizedCodes, skippedSkillCodes);
            return "";
        }
        // 步骤 4：构造系统提示，明确已选技能是本轮任务意图的一部分，防止模型忽略短句技能问题。
        log.info(
            "技能上下文已生效: 选择数={}, 生效数={}, 生效技能={}, 跳过技能={}, 上下文长度={}",
            normalizedCodes.size(),
            loadedSkillCodes.size(),
            loadedSkillCodes,
            skippedSkillCodes,
            contentBuilder.length()
        );
        return """
            用户已显式选择以下技能，这些技能就是本轮任务意图的一部分。
            请优先按已选技能的说明文档判断和执行当前问题，不要因为用户正文较短或像闲聊就忽略已选技能。
            技能编码不是可执行工具名，禁止把 /skill 或 skill code 当作 tool_call 名称；如需执行能力，应按技能文档选择当前运行环境真实可用的工具或直接给出下一步。
            运行时会从模型可见的用户正文开头剥离 @skill 前缀；当用户正文使用“这个”“这些”“它”“有什么区别”等指代时，默认先指向本轮已选技能，多技能时按已选技能列表进行解释或对比。
            当用户只问“这是什么”“这是啥”“介绍一下”“有什么用”等短句时，默认是在询问已选技能本身，应直接概括该技能用途、典型场景和限制；不要先要求用户补充 URL、页面或文件。
            如果用户明确要求执行技能任务但缺少 URL、页面、附件或其他必要目标，应围绕已选技能追问缺失信息，或在可用工具允许时先获取上下文；不要转成关于助手或普通闲聊回答。
            若技能内容确实与当前问题无关，只说明缺少可执行目标，不要生硬引用技能文档。

            %s
            """.formatted(contentBuilder.toString().trim());
    }

    /**
     * 构建已选技能的用户可读简介，用于“这是什么/这是啥”这类短句的确定性直答。
     * @param selectedSkillCodes 当前消息选择的技能编码。
     * @return 简短说明；无有效技能时返回空字符串。
     */
    public String buildSkillIntroReply(List<String> selectedSkillCodes) {
        LinkedHashSet<String> normalizedCodes = normalizeSelectedSkillCodes(selectedSkillCodes);
        if (normalizedCodes.isEmpty()) {
            return "";
        }
        List<String> introLines = new ArrayList<>();
        for (String skillCode : normalizedCodes) {
            ChatSkill skill = chatSkillRepository.findBySkillCode(skillCode);
            if (skill == null) {
                continue;
            }
            String description = resolveSkillDescription(skill);
            String displayName = StrUtil.blankToDefault(skill.getDisplayName(), skill.getSkillCode());
            introLines.add("`" + skill.getSkillCode() + "`（" + displayName + "）：" + description);
        }
        if (introLines.isEmpty()) {
            return "";
        }
        if (introLines.size() == 1) {
            return "这是你当前选中的技能：" + introLines.getFirst()
                + "\n\n如果要执行它，请继续给出这个技能要处理的具体目标或问题。";
        }
        return "你当前选中了这些技能：\n- "
            + String.join("\n- ", introLines)
            + "\n\n如果你想比较它们，可以直接问“有什么区别”；如果要执行其中一个，请说明具体目标。";
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

    private String resolveSkillDescription(ChatSkill skill) {
        String description = skill.getDescription();
        if (StrUtil.isBlank(description)) {
            description = extractManifestDescription(readSkillManifest(skill));
        }
        String normalizedDescription = normalizeIntroText(description);
        return StrUtil.isBlank(normalizedDescription)
            ? "该技能用于扩展当前对话能力，具体规则已注入本轮上下文"
            : StrUtil.maxLength(normalizedDescription, MAX_SKILL_INTRO_DESCRIPTION_CHARS);
    }

    private String extractManifestDescription(String manifestContent) {
        if (StrUtil.isBlank(manifestContent)) {
            return "";
        }
        StringBuilder descriptionBuilder = new StringBuilder();
        boolean collectingDescription = false;
        for (String line : manifestContent.split("\\R", -1)) {
            String trimmedLine = line.trim();
            if (trimmedLine.startsWith("description:")) {
                collectingDescription = true;
                String inlineDescription = cleanYamlDescriptionValue(trimmedLine.substring("description:".length()));
                if (StrUtil.isNotBlank(inlineDescription)) {
                    descriptionBuilder.append(inlineDescription);
                }
                continue;
            }
            if (!collectingDescription) {
                continue;
            }
            if (StrUtil.isBlank(trimmedLine)) {
                continue;
            }
            if (!Character.isWhitespace(line.charAt(0)) || "---".equals(trimmedLine)) {
                break;
            }
            if (!descriptionBuilder.isEmpty()) {
                descriptionBuilder.append(' ');
            }
            descriptionBuilder.append(trimmedLine);
        }
        return descriptionBuilder.toString();
    }

    private String cleanYamlDescriptionValue(String rawValue) {
        String value = StrUtil.trimToEmpty(rawValue);
        if (StrUtil.equalsAny(value, ">", "|")) {
            return "";
        }
        value = StrUtil.removePrefix(value, "\"");
        value = StrUtil.removeSuffix(value, "\"");
        value = StrUtil.removePrefix(value, "'");
        value = StrUtil.removeSuffix(value, "'");
        return value;
    }

    private String normalizeIntroText(String text) {
        return StrUtil.blankToDefault(text, "").replaceAll("\\s+", " ").trim();
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
