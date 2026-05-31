package com.codingx.skill.application.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.common.error.ErrorMessageCatalog;
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
 * 管理端聊天技能应用服务，负责技能配置、技能包上传、预览和历史包迁移。
 */
@Service
@RequiredArgsConstructor
public class AdminChatSkillService {

    /**
     * 管理端文件预览最大字节数，超过后只返回前缀内容并标记 truncated。
     */
    private static final int MAX_PREVIEW_BYTES = 128 * 1024;

    /**
     * 技能包根级清单文件名，上传和迁移都以该文件作为技能元信息来源。
     */
    private static final String ROOT_SKILL_MANIFEST = "SKILL.md";

    /**
     * 目录化技能包存储格式。
     */
    private static final String STORAGE_FORMAT_DIRECTORY = "directory";

    /**
     * 历史压缩包技能包存储格式。
     */
    private static final String STORAGE_FORMAT_ZIP = "zip";

    /**
     * 技能仓储，用于技能配置查询、保存和删除。
     */
    private final ChatSkillRepository chatSkillRepository;

    /**
     * 技能包对象存储客户端，用于上传目录、下载历史包和读取预览文件。
     */
    private final RustFsSkillPackageClient rustFsSkillPackageClient;

    /**
     * 返回技能列表。
     * @return 技能列表。
     */
    public List<ChatSkill> listAll() {
        // 步骤 1：管理端和运行时复用同一仓储排序规则读取全部未删除技能。
        return chatSkillRepository.findAll();
    }

    /**
     * 分页返回技能列表，供管理端列表页按页加载。
     * @param current 当前页码（从 1 开始）。
     * @param size 每页条数。
     * @return 技能分页结果。
     */
    public PageResult<ChatSkill> pageSkills(int current, int size) {
        // 步骤 1：分页边界和排序由仓储层统一处理，服务层不重复组装 PageResult。
        return chatSkillRepository.pageQuery(current, size);
    }

    /**
     * 创建技能。
     * @param request 请求对象。
     * @return 新增后的技能。
     */
    public ChatSkill create(ChatSkill request) {
        // 步骤 1：校验技能编码和展示名称必填，避免保存不可用配置。
        validateRequired(request);
        // 步骤 2：技能编码去首尾空格后参与唯一性校验。
        String normalizedSkillCode = request.getSkillCode().trim();
        if (chatSkillRepository.existsBySkillCode(normalizedSkillCode, null)) {
            throw new BusinessException("CHAT_SKILL_DUPLICATE_CODE", ErrorMessageCatalog.CHAT_SKILL_DUPLICATE_CODE);
        }
        // 步骤 3：补齐新增技能默认值，手工创建默认视为 built-in 且使用目录化格式。
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
        // 步骤 4：保存后返回持久化领域对象，Controller 直接透传给管理端。
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
        // 步骤 1：先读取旧记录，缺失时返回技能不存在。
        ChatSkill existing = chatSkillRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_SKILL_NOT_FOUND);
        }
        // 步骤 2：校验必填字段和技能编码唯一性，排除当前技能主键。
        validateRequired(request);
        String normalizedSkillCode = request.getSkillCode().trim();
        if (chatSkillRepository.existsBySkillCode(normalizedSkillCode, id)) {
            throw new BusinessException("CHAT_SKILL_DUPLICATE_CODE", ErrorMessageCatalog.CHAT_SKILL_DUPLICATE_CODE);
        }
        // 步骤 3：保留创建时间、删除标记和未显式覆盖的来源/启用/排序/存储格式。
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
        // 步骤 4：保存更新后的技能配置。
        chatSkillRepository.save(persisted);
        return persisted;
    }

    /**
     * 物理删除技能，同时删除 rustfs 中的文件。
     * @param id 主键。
     */
    public void delete(Long id) {
        // 步骤 1：先读取技能记录，避免对不存在技能执行对象存储删除。
        ChatSkill existing = chatSkillRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_SKILL_NOT_FOUND);
        }

        // 步骤 2：存在存储键时先删除对象存储资源，目录化技能按目录前缀删除，历史包按单对象删除。
        if (StrUtil.isNotBlank(existing.getStorageKey())) {
            try {
                if (StrUtil.equals(existing.getPackageStorageFormat(), STORAGE_FORMAT_DIRECTORY)) {
                    rustFsSkillPackageClient.deleteDirectory(existing.getStorageKey());
                } else {
                    rustFsSkillPackageClient.deleteObject(existing.getStorageKey());
                }
            } catch (Exception exception) {
                // 步骤 3：对象存储删除失败时阻断数据库删除，避免出现数据库记录丢失但文件残留。
                throw new BusinessException("CHAT_SKILL_DELETE_STORAGE_FAILED", "删除技能存储文件失败：" + exception.getMessage());
            }
        }

        // 步骤 4：对象存储删除成功后物理删除数据库记录。
        chatSkillRepository.deleteById(id);
    }

    /**
     * 上传技能包并解析 SKILL.md 生成技能配置。
     * @param file 单文件上传（zip/skill）。
     * @param category 可选分类。
     * @return 新增或更新后的技能。
     */
    public ChatSkill uploadSkillPackage(MultipartFile file, String category) {
        // 步骤 1：兼容旧单文件上传入口，默认没有目录文件且不强制覆盖同名存储键。
        return uploadSkillPackage(file, List.of(), category, false);
    }

    /**
     * 上传技能文件并解析 SKILL.md 生成技能配置。
     * @param file 单文件上传（zip/skill），与 files 二选一。
     * @param files 多文件上传（目录上传）。
     * @param category 可选分类。
     * @param forceOverwrite 是否强制覆盖同名技能。
     * @return 新增或更新后的技能。
     */
    public ChatSkill uploadSkillPackage(MultipartFile file, List<MultipartFile> files, String category, Boolean forceOverwrite) {
        // 步骤 1：统一解析 zip/skill 单文件或目录上传文件，并折叠单根目录。
        List<UploadedSkillFile> uploadedFiles = resolveUploadedFiles(file, files);
        // 步骤 2：读取根级 SKILL.md 并解析 name/description 元信息。
        UploadedSkillFile manifestFile = requireRootSkillManifest(uploadedFiles);
        SkillManifest manifest = parseSkillManifest(new String(manifestFile.bytes(), StandardCharsets.UTF_8));
        String normalizedSkillCode = normalizeSkillCode(manifest.name());
        // 步骤 3：计算目录总大小和内容摘要，用于展示与重复上传检测。
        long packageSize = uploadedFiles.stream().mapToLong(item -> item.bytes().length).sum();
        String packageChecksum = calculateDirectoryChecksum(uploadedFiles);

        // 步骤 4：同技能且内容摘要未变化时拒绝重复上传，避免对象存储重复写入。
        ChatSkill existing = chatSkillRepository.findBySkillCode(normalizedSkillCode);
        if (existing != null && StrUtil.equals(existing.getPackageChecksum(), packageChecksum)) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_DUPLICATE", ErrorMessageCatalog.CHAT_SKILL_UPLOAD_DUPLICATE);
        }

        // 步骤 5：检查目标目录存储键是否已存在，未显式覆盖时返回可确认的业务异常。
        String baseStorageKey = buildBaseStorageKey(normalizedSkillCode);
        boolean storageKeyExists = checkStorageKeyExists(baseStorageKey);

        if (storageKeyExists && !Boolean.TRUE.equals(forceOverwrite)) {
            throw new BusinessException("CHAT_SKILL_STORAGE_KEY_DUPLICATE", "技能存储键已存在，是否覆盖？");
        }

        // 步骤 6：强制覆盖时使用时间戳后缀生成新目录，避免直接破坏已有线上包。
        String storageKey;
        if (storageKeyExists && Boolean.TRUE.equals(forceOverwrite)) {
            String timestamp = String.valueOf(System.currentTimeMillis());
            storageKey = baseStorageKey + "-" + timestamp;
        } else {
            storageKey = baseStorageKey;
        }

        // 步骤 7：按确定后的 storageKey 上传目录化对象集合。
        try {
            rustFsSkillPackageClient.uploadDirectoryWithKey(toStorageFiles(uploadedFiles), storageKey);
        } catch (Exception exception) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_FAILED", ErrorMessageCatalog.CHAT_SKILL_UPLOAD_FAILED);
        }

        // 步骤 8：读取当前登录管理员作为上传人，并写入技能配置和包元数据。
        Long loginUserId = StpUtil.getLoginIdAsLong();
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
        // 步骤 9：新增或覆盖保存技能配置。
        chatSkillRepository.save(persisted);
        return persisted;
    }

    /**
     * 读取技能包目录树，用于管理端资源管理器展示。
     * @param id 技能主键。
     * @return 技能包条目列表（目录优先）。
     */
    public List<SkillPackageEntry> listPackageEntries(Long id) {
        // 步骤 1：读取技能并在必要时惰性迁移历史压缩包。
        ChatSkill skill = migrateLegacyPackageIfRequired(requireSkillById(id));
        // 步骤 2：列出目录化对象存储中的文件元数据。
        List<RustFsSkillPackageClient.SkillObjectMetadata> objects = listSkillDirectory(skill);
        LinkedHashSet<String> directoryPaths = new LinkedHashSet<>();
        List<SkillPackageEntry> fileEntries = new ArrayList<>();
        // 步骤 3：按文件路径补齐所有父级目录节点，同时收集文件节点。
        for (RustFsSkillPackageClient.SkillObjectMetadata objectMetadata : objects) {
            String normalizedPath = normalizeArchivePath(objectMetadata.relativePath());
            if (StrUtil.isBlank(normalizedPath)) {
                continue;
            }
            collectDirectoryPaths(normalizedPath, directoryPaths);
            fileEntries.add(new SkillPackageEntry(normalizedPath, extractName(normalizedPath), false, objectMetadata.size()));
        }
        // 步骤 4：目录节点排在文件节点前，再按路径稳定排序。
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
        // 步骤 1：读取技能并在必要时惰性迁移历史压缩包。
        ChatSkill skill = migrateLegacyPackageIfRequired(requireSkillById(id));
        // 步骤 2：规范化文件相对路径，空路径直接拒绝。
        String normalizedPath = normalizeArchivePath(path);
        if (StrUtil.isBlank(normalizedPath)) {
            throw new BusinessException("CHAT_SKILL_PACKAGE_INVALID_PATH", ErrorMessageCatalog.CHAT_SKILL_PACKAGE_PATH_REQUIRED);
        }
        // 步骤 3：从目录化存储读取文件内容，不存在时返回文件未找到。
        byte[] entryBytes;
        try {
            entryBytes = rustFsSkillPackageClient.downloadDirectoryFile(skill.getStorageKey(), normalizedPath);
        } catch (Exception exception) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_SKILL_PACKAGE_FILE_NOT_FOUND);
        }

        // 步骤 4：图片文件以 data URL 返回，便于管理端直接预览。
        if (looksLikeImage(normalizedPath)) {
            String base64Content = "data:image/" + getImageExtension(normalizedPath) + ";base64," +
                java.util.Base64.getEncoder().encodeToString(entryBytes);
            return new SkillPackageFileContent(normalizedPath, base64Content, false);
        }

        // 步骤 5：非图片二进制文件拒绝在线预览，避免前端展示乱码。
        if (looksLikeBinary(entryBytes)) {
            throw new BusinessException("CHAT_SKILL_PACKAGE_BINARY_FILE", ErrorMessageCatalog.CHAT_SKILL_PACKAGE_BINARY_FILE);
        }
        // 步骤 6：文本预览超过上限时截断并标记 truncated。
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
        // 步骤 1：只处理已经落对象存储的技能，兼容 uploaded 和 built-in 来源。
        List<ChatSkill> storedSkills = chatSkillRepository.findAll()
            .stream()
            .filter(skill -> StrUtil.isNotBlank(skill.getStorageKey()))
            .toList();

        int migrated = 0;
        int skipped = 0;
        List<SkillPackageMigrationFailure> failures = new ArrayList<>();

        // 步骤 2：目录化技能直接跳过，历史压缩包逐个迁移并记录失败明细。
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

        // 步骤 3：返回迁移总数、成功数、跳过数和失败列表供管理端展示。
        return new SkillPackageMigrationSummary(storedSkills.size(), migrated, skipped, failures);
    }

    private void validateRequired(ChatSkill request) {
        if (request == null) {
            throw new BusinessException("CHAT_SKILL_INVALID", ErrorMessageCatalog.CHAT_SKILL_REQUIRED);
        }
        if (StrUtil.isBlank(request.getSkillCode())) {
            throw new BusinessException("CHAT_SKILL_INVALID", ErrorMessageCatalog.CHAT_SKILL_CODE_REQUIRED);
        }
        if (StrUtil.isBlank(request.getDisplayName())) {
            throw new BusinessException("CHAT_SKILL_INVALID", ErrorMessageCatalog.CHAT_SKILL_NAME_REQUIRED);
        }
    }

    /**
     * 统一解析上传输入，输出目录化文件集合。
     */
    private List<UploadedSkillFile> resolveUploadedFiles(MultipartFile file, List<MultipartFile> files) {
        // 步骤 1：识别单压缩包上传和目录多文件上传两种模式，二者不能同时出现。
        boolean hasSingleFile = file != null && !file.isEmpty();
        List<MultipartFile> folderFiles = files == null ? List.of() : files.stream().filter(item -> item != null && !item.isEmpty()).toList();
        boolean hasFolderFiles = CollUtil.isNotEmpty(folderFiles);

        if (hasSingleFile && hasFolderFiles) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", ErrorMessageCatalog.CHAT_SKILL_UPLOAD_MODE_CONFLICT);
        }
        if (!hasSingleFile && !hasFolderFiles) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", ErrorMessageCatalog.CHAT_SKILL_UPLOAD_FILE_REQUIRED);
        }

        // 步骤 2：单文件模式只允许 zip/skill 压缩包，读取失败时返回统一上传失败文案。
        if (hasSingleFile) {
            validateArchiveUploadFile(file);
            try {
                return normalizeUploadedSkillFiles(readZipEntries(file.getBytes()));
            } catch (IOException exception) {
                throw new BusinessException("CHAT_SKILL_UPLOAD_FAILED", ErrorMessageCatalog.CHAT_SKILL_UPLOAD_READ_FAILED);
            }
        }

        // 步骤 3：目录上传模式逐个读取相对路径和文件字节，空路径文件跳过，读取失败直接中断。
        List<UploadedSkillFile> directoryFiles = new ArrayList<>();
        for (MultipartFile multipartFile : folderFiles) {
            String relativePath = normalizeArchivePath(StrUtil.blankToDefault(multipartFile.getOriginalFilename(), multipartFile.getName()));
            if (StrUtil.isBlank(relativePath)) {
                continue;
            }
            try {
                directoryFiles.add(new UploadedSkillFile(relativePath, multipartFile.getBytes(), multipartFile.getContentType()));
            } catch (IOException exception) {
                throw new BusinessException("CHAT_SKILL_UPLOAD_FAILED", ErrorMessageCatalog.CHAT_SKILL_UPLOAD_DIRECTORY_READ_FAILED);
            }
        }
        // 步骤 4：统一做路径标准化和安全校验，保证后续存储层只处理目录化文件集合。
        return normalizeUploadedSkillFiles(directoryFiles);
    }

    private void validateArchiveUploadFile(MultipartFile file) {
        String filename = StrUtil.blankToDefault(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!(filename.endsWith(".zip") || filename.endsWith(".skill"))) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", ErrorMessageCatalog.CHAT_SKILL_UPLOAD_ARCHIVE_ONLY);
        }
    }

    /**
     * 对路径去重与根目录折叠，确保最终以根级 SKILL.md 为准。
     */
    private List<UploadedSkillFile> normalizeUploadedSkillFiles(List<UploadedSkillFile> rawFiles) {
        if (CollUtil.isEmpty(rawFiles)) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", ErrorMessageCatalog.CHAT_SKILL_UPLOAD_EMPTY_CONTENT);
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
                throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", ErrorMessageCatalog.CHAT_SKILL_UPLOAD_DUPLICATE_PATH_PREFIX + normalizedPath);
            }
            deduplicatedFiles.put(uniqueKey, new UploadedSkillFile(normalizedPath, file.bytes(), file.contentType()));
        }

        if (deduplicatedFiles.isEmpty()) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", ErrorMessageCatalog.CHAT_SKILL_UPLOAD_NO_USABLE_FILE);
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
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", ErrorMessageCatalog.CHAT_SKILL_UPLOAD_INVALID_ARCHIVE);
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
            .orElseThrow(() -> new BusinessException("CHAT_SKILL_UPLOAD_INVALID", ErrorMessageCatalog.CHAT_SKILL_UPLOAD_ROOT_MANIFEST_REQUIRED));
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
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", ErrorMessageCatalog.CHAT_SKILL_MANIFEST_YAML_REQUIRED);
        }
        String[] segments = markdown.split("---", 3);
        if (segments.length < 3) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", ErrorMessageCatalog.CHAT_SKILL_MANIFEST_YAML_INVALID);
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
            throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", ErrorMessageCatalog.CHAT_SKILL_MANIFEST_NAME_REQUIRED);
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

    /**
     * 构建基础存储键（不含时间戳）。
     * @param skillCode 技能编码。
     * @return 基础存储键。
     */
    private String buildBaseStorageKey(String skillCode) {
        return "chat-skills/packages/" + skillCode;
    }

    /**
     * 检查存储键是否已存在。
     * @param storageKey 存储键。
     * @return 是否存在。
     */
    private boolean checkStorageKeyExists(String storageKey) {
        try {
            List<RustFsSkillPackageClient.SkillObjectMetadata> objects = rustFsSkillPackageClient.listDirectory(storageKey);
            return !objects.isEmpty();
        } catch (Exception exception) {
            return false;
        }
    }

    private ChatSkill requireSkillById(Long id) {
        ChatSkill skill = chatSkillRepository.findById(id);
        if (skill == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_SKILL_NOT_FOUND);
        }
        if (StrUtil.isBlank(skill.getStorageKey())) {
            throw new BusinessException("CHAT_SKILL_PACKAGE_NOT_FOUND", ErrorMessageCatalog.CHAT_SKILL_PACKAGE_NOT_FOUND);
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
        // 步骤 1：先下载历史压缩包；下载失败不修改数据库记录，保证迁移可重试。
        byte[] archiveBytes;
        try {
            archiveBytes = rustFsSkillPackageClient.download(legacySkill.getStorageKey());
        } catch (Exception exception) {
            throw new BusinessException("CHAT_SKILL_PACKAGE_DOWNLOAD_FAILED", ErrorMessageCatalog.CHAT_SKILL_PACKAGE_DOWNLOAD_FAILED);
        }

        // 步骤 2：解压并校验根目录存在 SKILL.md，避免把不完整包迁移成目录格式。
        List<UploadedSkillFile> uploadedFiles = normalizeUploadedSkillFiles(readZipEntries(archiveBytes));
        requireRootSkillManifest(uploadedFiles);

        // 步骤 3：把目录化文件重新上传到对象存储，上传失败时保留旧压缩包记录。
        String newStorageKey;
        try {
            newStorageKey = rustFsSkillPackageClient.uploadDirectory(toStorageFiles(uploadedFiles), legacySkill.getSkillCode());
        } catch (Exception exception) {
            throw new BusinessException("CHAT_SKILL_UPLOAD_FAILED", ErrorMessageCatalog.CHAT_SKILL_PACKAGE_MIGRATE_UPLOAD_FAILED);
        }

        // 步骤 4：计算目录化包大小和校验和，更新技能记录为 directory 存储格式。
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

        // 步骤 5：数据库已指向新目录对象后删除旧压缩包；删除失败需要暴露给调用方人工处理。
        try {
            rustFsSkillPackageClient.deleteObject(legacySkill.getStorageKey());
        } catch (Exception exception) {
            throw new BusinessException("CHAT_SKILL_PACKAGE_MIGRATE_DELETE_FAILED", ErrorMessageCatalog.CHAT_SKILL_PACKAGE_MIGRATE_DELETE_FAILED);
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
            throw new BusinessException("CHAT_SKILL_PACKAGE_PARSE_FAILED", ErrorMessageCatalog.CHAT_SKILL_PACKAGE_PARSE_FAILED);
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
                throw new BusinessException("CHAT_SKILL_UPLOAD_INVALID", ErrorMessageCatalog.CHAT_SKILL_UPLOAD_PATH_INVALID);
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

    private boolean looksLikeImage(String path) {
        String lowerPath = path.toLowerCase(Locale.ROOT);
        return lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") ||
               lowerPath.endsWith(".png") || lowerPath.endsWith(".gif") ||
               lowerPath.endsWith(".webp") || lowerPath.endsWith(".svg");
    }

    private String getImageExtension(String path) {
        String lowerPath = path.toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
            return "jpeg";
        } else if (lowerPath.endsWith(".png")) {
            return "png";
        } else if (lowerPath.endsWith(".gif")) {
            return "gif";
        } else if (lowerPath.endsWith(".webp")) {
            return "webp";
        } else if (lowerPath.endsWith(".svg")) {
            return "svg+xml";
        }
        return "jpeg";
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

