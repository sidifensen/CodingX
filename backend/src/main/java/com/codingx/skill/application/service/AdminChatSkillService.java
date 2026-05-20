package com.codingx.skill.application.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.exception.NotFoundException;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.common.storage.RustFsSkillPackageClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 提供聊天技能后台管理服务。
 */
@Service
@RequiredArgsConstructor
public class AdminChatSkillService {

    private static final int MAX_PREVIEW_BYTES = 128 * 1024;
    private static final String ROOT_SKILL_MANIFEST = "SKILL.md";
    private static final String STORAGE_FORMAT_DIRECTORY = "directory";
    private static final String STORAGE_FORMAT_ZIP = "zip";

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
            .packageStorageFormat(StrUtil.blankToDefault(request.getPackageStorageFormat(), STORAGE_FORMAT_DIRECTORY))
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
            .packageStorageFormat(StrUtil.blankToDefault(request.getPackageStorageFormat(), existing.getPackageStorageFormat()))
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
     * @param file 单文件上传（zip/skill）。
     * @param category 可选分类。
     * @return 新增或更新后的技能。
     */
    public ChatSkill uploadSkillPackage(MultipartFile file, String category) {
        return uploadSkillPackage(file, List.of(), category);
    }

    /**
     * 上传技能文件并解析 SKILL.md 生成技能配置。
     * @param file 单文件上传（zip/skill），与 files 二选一。
     * @param files 多文件上传（目录上传）。
     * @param category 可选分类。
     * @return 新增或更新后的技能。
     */
    public ChatSkill uploadSkillPackage(MultipartFile file, List<MultipartFile> files, String category) {
        List<UploadedSkillFile> uploadedFiles = resolveUploadedFiles(file, files);
        UploadedSkillFile manifestFile = requireRootSkillManifest(uploadedFiles);
        SkillManifest manifest = parseSkillManifest(new String(manifestFile.bytes(), StandardCharsets.UTF_8));
        String normalizedSkillCode = normalizeSkillCode(manifest.name());

        String storageKey;
        try {
            storageKey = rustFsSkillPackageClient.uploadDirectory(toStorageFiles(uploadedFiles), normalizedSkillCode);
        } catch (Exception exception) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_FAILED", "技能包上传失败");
        }

        Long loginUserId = StpUtil.getLoginIdAsLong();
        ChatSkill existing = chatSkillRepository.findBySkillCode(normalizedSkillCode);
        LocalDateTime now = LocalDateTime.now();
        long packageSize = uploadedFiles.stream().mapToLong(item -> item.bytes().length).sum();
        String packageChecksum = calculateDirectoryChecksum(uploadedFiles);

        ChatSkill persisted = (existing == null ? ChatSkill.builder().id(IdUtil.getSnowflakeNextId()) : existing.toBuilder())
            .skillCode(normalizedSkillCode)
            .displayName(manifest.name())
            .description(StrUtil.blankToDefault(manifest.description(), manifest.name()))
            .category(StrUtil.blankToDefault(StrUtil.trim(category), StrUtil.blankToDefault(existing == null ? null : existing.getCategory(), "上传技能")))
            .sourceType("uploaded")
            .enabled(existing == null ? 1 : existing.getEnabled())
            .sortNo(existing == null ? 0 : existing.getSortNo())
            .storageKey(storageKey)
            .packageStorageFormat(STORAGE_FORMAT_DIRECTORY)
            .packageFileName(resolveDirectoryPackageFileName(file, files, normalizedSkillCode))
            .packageSize(packageSize)
            .packageChecksum(packageChecksum)
            .uploadedBy(loginUserId)
            .uploadedAt(now)
            .createdAt(existing == null ? now : existing.getCreatedAt())
            .updatedAt(now)
            .deleted(0)
            .build();
        chatSkillRepository.save(persisted);
        return persisted;
    }

    /**
     * 读取技能包目录树，用于管理端资源管理器展示。
     * @param id 技能主键。
     * @return 技能包条目列表（目录优先）。
     */
    public List<SkillPackageEntry> listPackageEntries(Long id) {
        ChatSkill skill = migrateLegacyPackageIfRequired(requireSkillById(id));
        List<RustFsSkillPackageClient.SkillObjectMetadata> objects = listSkillDirectory(skill);
        LinkedHashSet<String> directoryPaths = new LinkedHashSet<>();
        List<SkillPackageEntry> fileEntries = new ArrayList<>();
        for (RustFsSkillPackageClient.SkillObjectMetadata objectMetadata : objects) {
            String normalizedPath = normalizeArchivePath(objectMetadata.relativePath());
            if (StrUtil.isBlank(normalizedPath)) {
                continue;
            }
            collectDirectoryPaths(normalizedPath, directoryPaths);
            fileEntries.add(new SkillPackageEntry(normalizedPath, extractName(normalizedPath), false, objectMetadata.size()));
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
        ChatSkill skill = migrateLegacyPackageIfRequired(requireSkillById(id));
        String normalizedPath = normalizeArchivePath(path);
        if (StrUtil.isBlank(normalizedPath)) {
            throw new BusinessException("CHAT_SKILL_PACKAGE_INVALID_PATH", "文件路径不能为空");
        }
        byte[] entryBytes;
        try {
            entryBytes = rustFsSkillPackageClient.downloadDirectoryFile(skill.getStorageKey(), normalizedPath);
        } catch (Exception exception) {
            throw new NotFoundException("技能包文件不存在");
        }
        if (looksLikeBinary(entryBytes)) {
            throw new BusinessException("CHAT_SKILL_PACKAGE_BINARY_FILE", "该文件为二进制文件，暂不支持在线预览");
        }
        boolean truncated = entryBytes.length > MAX_PREVIEW_BYTES;
        int previewLength = Math.min(entryBytes.length, MAX_PREVIEW_BYTES);
        String content = new String(ArrayUtil.sub(entryBytes, 0, previewLength), StandardCharsets.UTF_8);
        return new SkillPackageFileContent(normalizedPath, content, truncated);
    }

    /**
     * 批量迁移对象存储中的历史压缩包技能为目录化存储。
     * 业务约束：只处理已落对象存储（storageKey 非空）的技能，兼容 uploaded 与 built-in 来源。
     * @return 迁移统计。
     */
    public SkillPackageMigrationSummary migrateUploadedSkillPackages() {
        List<ChatSkill> storedSkills = chatSkillRepository.findAll()
            .stream()
            .filter(skill -> StrUtil.isNotBlank(skill.getStorageKey()))
            .toList();

        int migrated = 0;
        int skipped = 0;
        List<SkillPackageMigrationFailure> failures = new ArrayList<>();

        for (ChatSkill skill : storedSkills) {
            if (!isLegacyArchiveStorage(skill)) {
                skipped++;
                continue;
            }
            try {
                migrateSkillPackageToDirectory(skill);
                migrated++;
            } catch (Exception exception) {
                failures.add(new SkillPackageMigrationFailure(
                    skill.getId(),
                    skill.getSkillCode(),
                    StrUtil.blankToDefault(exception.getMessage(), "迁移失败")
                ));
            }
        }

        return new SkillPackageMigrationSummary(storedSkills.size(), migrated, skipped, failures);
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

    /**
     * 统一解析上传输入，输出目录化文件集合。
     */
    private List<UploadedSkillFile> resolveUploadedFiles(MultipartFile file, List<MultipartFile> files) {
        boolean hasSingleFile = file != null && !file.isEmpty();
        List<MultipartFile> folderFiles = files == null ? List.of() : files.stream().filter(item -> item != null && !item.isEmpty()).toList();
        boolean hasFolderFiles = CollUtil.isNotEmpty(folderFiles);

        if (hasSingleFile && hasFolderFiles) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", "请仅选择一种上传方式");
        }
        if (!hasSingleFile && !hasFolderFiles) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", "请上传技能包文件");
        }

        if (hasSingleFile) {
            validateArchiveUploadFile(file);
            try {
                return normalizeUploadedSkillFiles(readZipEntries(file.getBytes()));
            } catch (IOException exception) {
                throw new BusinessException("CHAT_SKILL_UPLOAD_FAILED", "技能包读取失败");
            }
        }

        List<UploadedSkillFile> directoryFiles = new ArrayList<>();
        for (MultipartFile multipartFile : folderFiles) {
            String relativePath = normalizeArchivePath(StrUtil.blankToDefault(multipartFile.getOriginalFilename(), multipartFile.getName()));
            if (StrUtil.isBlank(relativePath)) {
                continue;
            }
            try {
                directoryFiles.add(new UploadedSkillFile(relativePath, multipartFile.getBytes(), multipartFile.getContentType()));
            } catch (IOException exception) {
                throw new BusinessException("CHAT_SKILL_UPLOAD_FAILED", "技能目录文件读取失败");
            }
        }
        return normalizeUploadedSkillFiles(directoryFiles);
    }

    private void validateArchiveUploadFile(MultipartFile file) {
        String filename = StrUtil.blankToDefault(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!(filename.endsWith(".zip") || filename.endsWith(".skill"))) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", "仅支持 zip 或 skill 文件");
        }
    }

    /**
     * 对路径去重与根目录折叠，确保最终以根级 SKILL.md 为准。
     */
    private List<UploadedSkillFile> normalizeUploadedSkillFiles(List<UploadedSkillFile> rawFiles) {
        if (CollUtil.isEmpty(rawFiles)) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", "技能包缺少文件内容");
        }

        List<UploadedSkillFile> flattenedFiles = collapseSingleRootDirectory(rawFiles);
        Map<String, UploadedSkillFile> deduplicatedFiles = new LinkedHashMap<>();
        for (UploadedSkillFile file : flattenedFiles) {
            String normalizedPath = normalizeArchivePath(file.path());
            if (StrUtil.isBlank(normalizedPath)) {
                continue;
            }
            String uniqueKey = normalizedPath.toLowerCase(Locale.ROOT);
            if (deduplicatedFiles.containsKey(uniqueKey)) {
                throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", "技能包存在重复文件路径: " + normalizedPath);
            }
            deduplicatedFiles.put(uniqueKey, new UploadedSkillFile(normalizedPath, file.bytes(), file.contentType()));
        }

        if (deduplicatedFiles.isEmpty()) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", "技能包缺少可用文件");
        }
        return new ArrayList<>(deduplicatedFiles.values());
    }

    private List<UploadedSkillFile> readZipEntries(byte[] archiveBytes) {
        List<UploadedSkillFile> files = new ArrayList<>();
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
                // ZipInputStream 需要保持打开以继续读取后续 entry，因此此处禁止自动关闭流。
                byte[] bytes = IoUtil.readBytes(zipInputStream, false);
                files.add(new UploadedSkillFile(normalizedPath, bytes, null));
            }
        } catch (IOException exception) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", "技能包不是有效压缩文件");
        }
        return files;
    }

    private List<UploadedSkillFile> collapseSingleRootDirectory(List<UploadedSkillFile> files) {
        if (containsRootSkillManifest(files)) {
            return files;
        }
        String firstPath = files.getFirst().path();
        int separatorIndex = firstPath.indexOf('/');
        if (separatorIndex <= 0) {
            return files;
        }
        String rootDirectory = firstPath.substring(0, separatorIndex);
        String rootManifestPath = rootDirectory + "/" + ROOT_SKILL_MANIFEST;
        boolean allUnderSameRoot = files.stream().allMatch(file -> StrUtil.startWith(file.path(), rootDirectory + "/"));
        boolean rootManifestExists = files.stream().anyMatch(file -> StrUtil.equalsIgnoreCase(file.path(), rootManifestPath));
        if (!allUnderSameRoot || !rootManifestExists) {
            return files;
        }
        return files.stream()
            .map(file -> new UploadedSkillFile(file.path().substring(rootDirectory.length() + 1), file.bytes(), file.contentType()))
            .toList();
    }

    private boolean containsRootSkillManifest(List<UploadedSkillFile> files) {
        return files.stream().anyMatch(file -> StrUtil.equalsIgnoreCase(file.path(), ROOT_SKILL_MANIFEST));
    }

    private UploadedSkillFile requireRootSkillManifest(List<UploadedSkillFile> files) {
        return files.stream()
            .filter(file -> StrUtil.equalsIgnoreCase(file.path(), ROOT_SKILL_MANIFEST))
            .findFirst()
            .orElseThrow(() -> new BusinessException("CHAT_SKILL_UPLOAD_INVALID", "技能包缺少根目录 SKILL.md"));
    }

    private List<RustFsSkillPackageClient.SkillFileObject> toStorageFiles(List<UploadedSkillFile> files) {
        return files.stream()
            .map(file -> new RustFsSkillPackageClient.SkillFileObject(file.path(), file.bytes(), file.contentType()))
            .toList();
    }

    /**
     * 统一生成目录化存储场景的 package_file_name。
     * 业务意图：该字段用于展示当前包形态，目录化后不应再保留 zip/skill 后缀。
     */
    private String resolveDirectoryPackageFileName(MultipartFile file, List<MultipartFile> files, String normalizedSkillCode) {
        if (file != null && !file.isEmpty()) {
            String originalFileName = StrUtil.blankToDefault(file.getOriginalFilename(), "unknown.skill");
            String normalizedFileName = stripArchiveExtension(originalFileName);
            if (StrUtil.isBlank(normalizedFileName)) {
                return StrUtil.blankToDefault(normalizedSkillCode, "skill-package");
            }
            return normalizedFileName;
        }
        if (CollUtil.isNotEmpty(files)) {
            return "folder-upload";
        }
        return StrUtil.blankToDefault(normalizedSkillCode, "skill-package");
    }

    /**
     * 去除 zip/skill 扩展名，避免目录化记录仍表现为压缩包。
     */
    private String stripArchiveExtension(String fileName) {
        String normalizedFileName = StrUtil.trim(fileName);
        if (StrUtil.isBlank(normalizedFileName)) {
            return "";
        }
        String lowerCaseFileName = normalizedFileName.toLowerCase(Locale.ROOT);
        if (lowerCaseFileName.endsWith(".zip")) {
            return normalizedFileName.substring(0, normalizedFileName.length() - 4);
        }
        if (lowerCaseFileName.endsWith(".skill")) {
            return normalizedFileName.substring(0, normalizedFileName.length() - 6);
        }
        return normalizedFileName;
    }

    private String calculateDirectoryChecksum(List<UploadedSkillFile> files) {
        StringBuilder builder = new StringBuilder();
        files.stream()
            .sorted(Comparator.comparing(UploadedSkillFile::path))
            .forEach(file -> builder
                .append(file.path())
                .append(':')
                .append(DigestUtil.sha256Hex(file.bytes()))
                .append('\n'));
        return DigestUtil.sha256Hex(builder.toString());
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

    /**
     * 对历史 zip 对象做惰性迁移，避免管理端预览依赖旧格式。
     */
    private ChatSkill migrateLegacyPackageIfRequired(ChatSkill skill) {
        if (!isLegacyArchiveStorage(skill)) {
            return skill;
        }
        return migrateSkillPackageToDirectory(skill);
    }

    private boolean isLegacyArchiveStorage(ChatSkill skill) {
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

    /**
     * 单技能迁移：下载 zip，解压上传目录，更新记录并删除旧对象。
     */
    private ChatSkill migrateSkillPackageToDirectory(ChatSkill legacySkill) {
        byte[] archiveBytes;
        try {
            archiveBytes = rustFsSkillPackageClient.download(legacySkill.getStorageKey());
        } catch (Exception exception) {
            throw new BusinessException("CHAT_SKILL_PACKAGE_DOWNLOAD_FAILED", "技能包下载失败");
        }

        List<UploadedSkillFile> uploadedFiles = normalizeUploadedSkillFiles(readZipEntries(archiveBytes));
        requireRootSkillManifest(uploadedFiles);

        String newStorageKey;
        try {
            newStorageKey = rustFsSkillPackageClient.uploadDirectory(toStorageFiles(uploadedFiles), legacySkill.getSkillCode());
        } catch (Exception exception) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_FAILED", "技能包迁移上传失败");
        }

        long packageSize = uploadedFiles.stream().mapToLong(item -> item.bytes().length).sum();
        String packageChecksum = calculateDirectoryChecksum(uploadedFiles);
        LocalDateTime now = LocalDateTime.now();

        ChatSkill migratedSkill = legacySkill.toBuilder()
            .storageKey(newStorageKey)
            .packageStorageFormat(STORAGE_FORMAT_DIRECTORY)
            .packageFileName(resolveMigratedDirectoryPackageFileName(legacySkill))
            .packageSize(packageSize)
            .packageChecksum(packageChecksum)
            .updatedAt(now)
            .build();
        chatSkillRepository.save(migratedSkill);

        try {
            rustFsSkillPackageClient.deleteObject(legacySkill.getStorageKey());
        } catch (Exception exception) {
            throw new BusinessException("CHAT_SKILL_PACKAGE_MIGRATE_DELETE_FAILED", "历史技能包清理失败");
        }
        return migratedSkill;
    }

    /**
     * 历史 zip 迁移后同步修正 package_file_name，避免页面仍显示压缩包后缀。
     */
    private String resolveMigratedDirectoryPackageFileName(ChatSkill legacySkill) {
        String currentFileName = StrUtil.trim(legacySkill.getPackageFileName());
        if (StrUtil.isNotBlank(currentFileName)) {
            String strippedFileName = stripArchiveExtension(currentFileName);
            if (StrUtil.isNotBlank(strippedFileName)) {
                return strippedFileName;
            }
        }
        String storageKeyName = StrUtil.subAfter(StrUtil.blankToDefault(legacySkill.getStorageKey(), ""), "/", true);
        String strippedStorageKeyName = stripArchiveExtension(storageKeyName);
        if (StrUtil.isNotBlank(strippedStorageKeyName)) {
            return strippedStorageKeyName;
        }
        return StrUtil.blankToDefault(StrUtil.trim(legacySkill.getSkillCode()), "skill-package");
    }

    private List<RustFsSkillPackageClient.SkillObjectMetadata> listSkillDirectory(ChatSkill skill) {
        try {
            return rustFsSkillPackageClient.listDirectory(skill.getStorageKey());
        } catch (Exception exception) {
            throw new BusinessException("CHAT_SKILL_PACKAGE_PARSE_FAILED", "技能包目录解析失败");
        }
    }

    private void collectDirectoryPaths(String filePath, Set<String> directories) {
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
        while (StrUtil.startWith(normalizedPath, "./")) {
            normalizedPath = normalizedPath.substring(2);
        }
        normalizedPath = StrUtil.removePrefix(normalizedPath, "/");
        normalizedPath = StrUtil.removeSuffix(normalizedPath, "/");
        if (StrUtil.isBlank(normalizedPath)) {
            return "";
        }
        List<String> segments = StrUtil.split(normalizedPath, '/');
        if (CollUtil.isEmpty(segments)) {
            return "";
        }
        List<String> sanitizedSegments = new ArrayList<>();
        for (String segment : segments) {
            String sanitizedSegment = StrUtil.trim(segment);
            if (StrUtil.isBlank(sanitizedSegment) || StrUtil.equals(sanitizedSegment, ".") || StrUtil.equals(sanitizedSegment, "..")) {
                throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", "技能包路径非法");
            }
            sanitizedSegments.add(sanitizedSegment);
        }
        return StrUtil.join("/", sanitizedSegments);
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

    private record UploadedSkillFile(String path, byte[] bytes, String contentType) {
    }

    public record SkillPackageEntry(String path, String name, boolean directory, Long size) {
    }

    public record SkillPackageFileContent(String path, String content, boolean truncated) {
    }

    public record SkillPackageMigrationFailure(Long skillId, String skillCode, String reason) {
    }

    public record SkillPackageMigrationSummary(int total, int migrated, int skipped, List<SkillPackageMigrationFailure> failures) {
    }
}

