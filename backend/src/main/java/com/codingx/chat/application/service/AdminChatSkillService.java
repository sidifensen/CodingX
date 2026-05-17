package com.codingx.chat.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatSkill;
import com.codingx.chat.domain.repository.ChatSkillRepository;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.exception.NotFoundException;
import com.codingx.storage.RustFsSkillPackageClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import cn.dev33.satoken.stp.StpUtil;

/**
 * 提供聊天技能后台管理服务。
 */
@Service
@RequiredArgsConstructor
public class AdminChatSkillService {

    private static final int MAX_PREVIEW_BYTES = 128 * 1024;

    private final ChatSkillRepository chatSkillRepository;
    private final RustFsSkillPackageClient rustFsSkillPackageClient;

    /**
     * 返回技能列表。
     * @return 技能列表。
     */
    public List<ChatSkill> listAll() {
        return chatSkillRepository.findAll();
    }

    /**
     * 分页返回技能列表，供管理端列表页按页加载。
     * @param current 当前页码（从 1 开始）。
     * @param size 每页条数。
     * @return 技能分页结果。
     */
    public PageResult<ChatSkill> pageSkills(int current, int size) {
        return chatSkillRepository.pageQuery(current, size);
    }

    /**
     * 创建技能。
     * @param request 请求对象。
     * @return 新增后的技能。
     */
    public ChatSkill create(ChatSkill request) {
        validateRequired(request);
        String normalizedSkillCode = request.getSkillCode().trim();
        if (chatSkillRepository.existsBySkillCode(normalizedSkillCode, null)) {
            throw new BusinessException("CHAT_SKILL_DUPLICATE_CODE", "技能编码已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        ChatSkill persisted = request.toBuilder()
            .id(IdUtil.getSnowflakeNextId())
            .skillCode(normalizedSkillCode)
            .displayName(request.getDisplayName().trim())
            .enabled(request.getEnabled() == null ? 1 : request.getEnabled())
            .sortNo(request.getSortNo() == null ? 0 : request.getSortNo())
            .sourceType(StrUtil.blankToDefault(request.getSourceType(), "built-in"))
            .createdAt(now)
            .updatedAt(now)
            .deleted(0)
            .build();
        chatSkillRepository.save(persisted);
        return persisted;
    }

    /**
     * 更新技能。
     * @param id 主键。
     * @param request 请求对象。
     * @return 更新后的技能。
     */
    public ChatSkill update(Long id, ChatSkill request) {
        ChatSkill existing = chatSkillRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException("技能不存在");
        }
        validateRequired(request);
        String normalizedSkillCode = request.getSkillCode().trim();
        if (chatSkillRepository.existsBySkillCode(normalizedSkillCode, id)) {
            throw new BusinessException("CHAT_SKILL_DUPLICATE_CODE", "技能编码已存在");
        }
        ChatSkill persisted = request.toBuilder()
            .id(id)
            .skillCode(normalizedSkillCode)
            .displayName(request.getDisplayName().trim())
            .sourceType(StrUtil.blankToDefault(request.getSourceType(), existing.getSourceType()))
            .enabled(request.getEnabled() == null ? existing.getEnabled() : request.getEnabled())
            .sortNo(request.getSortNo() == null ? existing.getSortNo() : request.getSortNo())
            .createdAt(existing.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .deleted(existing.getDeleted())
            .build();
        chatSkillRepository.save(persisted);
        return persisted;
    }

    /**
     * 删除技能。
     * @param id 主键。
     */
    public void delete(Long id) {
        ChatSkill existing = chatSkillRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException("技能不存在");
        }
        chatSkillRepository.softDeleteById(id);
    }

    /**
     * 上传技能包并解析 SKILL.md 生成技能配置。
     * @param file 上传文件。
     * @param category 可选分类。
     * @return 新增或更新后的技能。
     */
    public ChatSkill uploadSkillPackage(MultipartFile file, String category) {
        validateUploadFile(file);
        try {
            byte[] bytes = file.getBytes();
            String skillMarkdown = extractRootSkillMarkdown(bytes);
            SkillManifest manifest = parseSkillManifest(skillMarkdown);
            String normalizedSkillCode = normalizeSkillCode(manifest.name());
            String storageKey = rustFsSkillPackageClient.upload(bytes, file.getOriginalFilename());
            Long loginUserId = StpUtil.getLoginIdAsLong();
            ChatSkill existing = chatSkillRepository.findBySkillCode(normalizedSkillCode);
            LocalDateTime now = LocalDateTime.now();
            ChatSkill persisted = (existing == null ? ChatSkill.builder().id(IdUtil.getSnowflakeNextId()) : existing.toBuilder())
                .skillCode(normalizedSkillCode)
                .displayName(manifest.name())
                .description(StrUtil.blankToDefault(manifest.description(), manifest.name()))
                .category(StrUtil.blankToDefault(StrUtil.trim(category), StrUtil.blankToDefault(existing == null ? null : existing.getCategory(), "上传技能")))
                .sourceType("uploaded")
                .enabled(existing == null ? 1 : existing.getEnabled())
                .sortNo(existing == null ? 0 : existing.getSortNo())
                .storageKey(storageKey)
                .packageFileName(StrUtil.blankToDefault(file.getOriginalFilename(), "unknown.skill"))
                .packageSize((long) bytes.length)
                .packageChecksum(DigestUtil.sha256Hex(bytes))
                .uploadedBy(loginUserId)
                .uploadedAt(now)
                .createdAt(existing == null ? now : existing.getCreatedAt())
                .updatedAt(now)
                .deleted(0)
                .build();
            chatSkillRepository.save(persisted);
            return persisted;
        } catch (IOException exception) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_FAILED", "技能包读取失败");
        }
    }

    /**
     * 读取技能包目录树，用于管理端资源管理器展示。
     * @param id 技能主键。
     * @return 技能包条目列表（目录优先）。
     */
    public List<SkillPackageEntry> listPackageEntries(Long id) {
        ChatSkill skill = requireSkillById(id);
        byte[] bytes = downloadSkillPackage(skill);
        LinkedHashSet<String> directoryPaths = new LinkedHashSet<>();
        List<SkillPackageEntry> fileEntries = new ArrayList<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                String normalizedPath = normalizeArchivePath(zipEntry.getName());
                if (StrUtil.isBlank(normalizedPath)) {
                    continue;
                }
                collectDirectoryPaths(normalizedPath, directoryPaths);
                if (!zipEntry.isDirectory()) {
                    fileEntries.add(new SkillPackageEntry(normalizedPath, extractName(normalizedPath), false, zipEntry.getSize()));
                }
            }
        } catch (IOException exception) {
            throw new BusinessException("CHAT_SKILL_PACKAGE_PARSE_FAILED", "技能包目录解析失败");
        }
        List<SkillPackageEntry> entries = new ArrayList<>();
        for (String directoryPath : directoryPaths) {
            entries.add(new SkillPackageEntry(directoryPath, extractName(directoryPath), true, null));
        }
        entries.addAll(fileEntries);
        entries.sort(Comparator
            .comparing(SkillPackageEntry::directory).reversed()
            .thenComparing(SkillPackageEntry::path));
        return entries;
    }

    /**
     * 读取技能包内文本文件内容，默认超限截断，避免管理端预览卡顿。
     * @param id 技能主键。
     * @param path 归档内文件路径。
     * @return 预览内容与截断标记。
     */
    public SkillPackageFileContent readPackageFileContent(Long id, String path) {
        ChatSkill skill = requireSkillById(id);
        String normalizedPath = normalizeArchivePath(path);
        if (StrUtil.isBlank(normalizedPath)) {
            throw new BusinessException("CHAT_SKILL_PACKAGE_INVALID_PATH", "文件路径不能为空");
        }
        byte[] bytes = downloadSkillPackage(skill);
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    continue;
                }
                String entryPath = normalizeArchivePath(zipEntry.getName());
                if (!StrUtil.equals(entryPath, normalizedPath)) {
                    continue;
                }
                byte[] entryBytes = IoUtil.readBytes(zipInputStream);
                if (looksLikeBinary(entryBytes)) {
                    throw new BusinessException("CHAT_SKILL_PACKAGE_BINARY_FILE", "该文件为二进制文件，暂不支持在线预览");
                }
                boolean truncated = entryBytes.length > MAX_PREVIEW_BYTES;
                int previewLength = Math.min(entryBytes.length, MAX_PREVIEW_BYTES);
                String content = new String(ArrayUtil.sub(entryBytes, 0, previewLength), StandardCharsets.UTF_8);
                return new SkillPackageFileContent(normalizedPath, content, truncated);
            }
        } catch (IOException exception) {
            throw new BusinessException("CHAT_SKILL_PACKAGE_PARSE_FAILED", "技能包文件读取失败");
        }
        throw new NotFoundException("技能包文件不存在");
    }

    private void validateRequired(ChatSkill request) {
        if (request == null) {
            throw new BusinessException("CHAT_SKILL_INVALID", "技能信息不能为空");
        }
        if (StrUtil.isBlank(request.getSkillCode())) {
            throw new BusinessException("CHAT_SKILL_INVALID", "技能编码不能为空");
        }
        if (StrUtil.isBlank(request.getDisplayName())) {
            throw new BusinessException("CHAT_SKILL_INVALID", "技能名称不能为空");
        }
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", "请上传技能包文件");
        }
        String filename = StrUtil.blankToDefault(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!(filename.endsWith(".zip") || filename.endsWith(".skill"))) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", "仅支持 zip 或 skill 文件");
        }
    }

    private String extractRootSkillMarkdown(byte[] bytes) {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = StrUtil.removePrefix(entry.getName(), "./");
                if (!StrUtil.equalsIgnoreCase(name, "SKILL.md")) {
                    continue;
                }
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                zipInputStream.transferTo(outputStream);
                return outputStream.toString(StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", "技能包不是有效压缩文件");
        }
        throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", "技能包缺少根目录 SKILL.md");
    }

    private SkillManifest parseSkillManifest(String markdown) {
        if (StrUtil.isBlank(markdown) || !markdown.startsWith("---")) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", "SKILL.md 缺少 YAML 元信息");
        }
        String[] segments = markdown.split("---", 3);
        if (segments.length < 3) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", "SKILL.md YAML 元信息格式不正确");
        }
        String yamlBlock = segments[1];
        String name = null;
        String description = null;
        for (String line : yamlBlock.split("\\R")) {
            String trimmed = StrUtil.trim(line);
            if (StrUtil.isBlank(trimmed) || StrUtil.startWith(trimmed, "#")) {
                continue;
            }
            int separatorIndex = trimmed.indexOf(':');
            if (separatorIndex <= 0) {
                continue;
            }
            String key = StrUtil.trim(trimmed.substring(0, separatorIndex));
            String value = sanitizeYamlValue(StrUtil.trim(trimmed.substring(separatorIndex + 1)));
            if (StrUtil.equalsAnyIgnoreCase(key, "name", "skill_name")) {
                name = value;
            } else if (StrUtil.equalsAnyIgnoreCase(key, "description", "desc")) {
                description = value;
            }
        }
        if (StrUtil.isBlank(name)) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", "SKILL.md 缺少 name 字段");
        }
        return new SkillManifest(name, description);
    }

    private String sanitizeYamlValue(String rawValue) {
        if (StrUtil.isBlank(rawValue)) {
            return "";
        }
        String value = rawValue;
        if ((StrUtil.startWith(value, "\"") && StrUtil.endWith(value, "\""))
            || (StrUtil.startWith(value, "'") && StrUtil.endWith(value, "'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return StrUtil.trim(value);
    }

    private String normalizeSkillCode(String displayName) {
        String normalized = StrUtil.trim(displayName).toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
        if (StrUtil.isBlank(normalized)) {
            return "skill-" + IdUtil.fastSimpleUUID().substring(0, 8);
        }
        return normalized;
    }

    private ChatSkill requireSkillById(Long id) {
        ChatSkill skill = chatSkillRepository.findById(id);
        if (skill == null) {
            throw new NotFoundException("技能不存在");
        }
        if (StrUtil.isBlank(skill.getStorageKey())) {
            throw new BusinessException("CHAT_SKILL_PACKAGE_NOT_FOUND", "该技能没有可预览的技能包");
        }
        return skill;
    }

    private byte[] downloadSkillPackage(ChatSkill skill) {
        try {
            return rustFsSkillPackageClient.download(skill.getStorageKey());
        } catch (Exception exception) {
            throw new BusinessException("CHAT_SKILL_PACKAGE_DOWNLOAD_FAILED", "技能包下载失败");
        }
    }

    private void collectDirectoryPaths(String filePath, LinkedHashSet<String> directories) {
        String current = filePath;
        int index = current.lastIndexOf('/');
        while (index > 0) {
            current = current.substring(0, index);
            directories.add(current);
            index = current.lastIndexOf('/');
        }
    }

    private String normalizeArchivePath(String originalPath) {
        String normalizedPath = StrUtil.blankToDefault(originalPath, "")
            .replace("\\", "/")
            .trim();
        normalizedPath = StrUtil.removePrefix(normalizedPath, "./");
        normalizedPath = StrUtil.removePrefix(normalizedPath, "/");
        normalizedPath = StrUtil.removeSuffix(normalizedPath, "/");
        return normalizedPath;
    }

    private String extractName(String path) {
        int index = path.lastIndexOf('/');
        if (index < 0) {
            return path;
        }
        return path.substring(index + 1);
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

    private record SkillManifest(String name, String description) {
    }

    public record SkillPackageEntry(String path, String name, boolean directory, Long size) {
    }

    public record SkillPackageFileContent(String path, String content, boolean truncated) {
    }
}
