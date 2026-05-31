package com.codingx.common.storage;

import cn.hutool.core.util.StrUtil;
import com.codingx.common.error.ErrorMessageCatalog;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * 封装技能包对象存储读写能力，统一返回桶内 key。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RustFsSkillPackageClient {

    /**
     * RustFS 的 S3 兼容客户端，用于执行技能包对象的上传、下载、列举与删除。
     */
    private final S3Client rustFsS3Client;

    /**
     * 技能包所在桶名，来自 rustfs.bucket 配置，默认使用 codingx-skills。
     */
    @Value("${rustfs.bucket:codingx-skills}")
    private String bucketName;

    /**
     * 技能包对象存储前缀，隔离技能包文件与聊天附件等其他对象。
     */
    @Value("${rustfs.skill-prefix:chat-skills/packages}")
    private String skillPrefix;

    /**
     * 桶存在性检查标记，避免每次技能文件读写都重复请求 RustFS。
     */
    private volatile boolean bucketEnsured;

    /**
     * 上传技能包并返回对象键。
     * @param bytes 文件字节。
     * @param originalFilename 上传原始文件名。
     * @return 对象 key（不含 bucket）。
     */
    public String upload(byte[] bytes, String originalFilename) {
        // 步骤 1：上传前确认桶存在；首次访问时会自动创建缺失的桶。
        ensureBucketExists();

        // 步骤 2：规范化原始文件名并生成对象 key，避免空文件名或前导斜杠污染存储路径。
        String normalizedName = StrUtil.blankToDefault(originalFilename, "skill-package.skill");
        String key = buildObjectKey(normalizedName);

        // 步骤 3：按二进制技能包写入 RustFS，技能包解析由上层服务负责。
        rustFsS3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("application/octet-stream")
                .build(),
            RequestBody.fromBytes(bytes)
        );
        // 步骤 4：返回桶内 key，数据库只保存路径标识，不保存对象存储访问细节。
        return key;
    }

    /**
     * 按目录批量上传技能文件并返回目录前缀。
     * @param fileObjects 技能文件集合，path 为目录内相对路径。
     * @param preferredDirectoryName 目录名称建议值（通常为技能编码）。
     * @return 目录前缀 key（不含 bucket）。
     */
    public String uploadDirectory(List<SkillFileObject> fileObjects, String preferredDirectoryName) {
        // 步骤 1：确保桶可用，并校验目录上传必须至少包含一个文件。
        ensureBucketExists();
        if (fileObjects == null || fileObjects.isEmpty()) {
            throw new IllegalArgumentException(ErrorMessageCatalog.CHAT_SKILL_FILE_LIST_REQUIRED);
        }

        // 步骤 2：根据技能编码或目录名生成固定目录前缀，保证同一技能重复上传覆盖同一位置。
        String directoryPrefix = buildDirectoryPrefix(preferredDirectoryName);
        List<SkillFileObject> sortedFiles = fileObjects.stream()
            .sorted(Comparator.comparing(SkillFileObject::path))
            .toList();

        // 固定目录前缀时，上传前先清空旧对象，避免删除文件后残留脏数据。
        deleteDirectory(directoryPrefix);
        for (SkillFileObject fileObject : sortedFiles) {
            // 步骤 3：逐个归一化相对路径并写入对象，禁止文件路径逃逸到目录前缀之外。
            String normalizedPath = normalizeRelativePath(fileObject.path());
            String key = directoryPrefix + "/" + normalizedPath;
            byte[] bytes = fileObject.bytes() == null ? new byte[0] : fileObject.bytes();
            rustFsS3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(StrUtil.blankToDefault(fileObject.contentType(), guessContentType(normalizedPath)))
                    .build(),
                RequestBody.fromBytes(bytes)
            );
        }
        // 步骤 4：返回目录前缀，后续列举、读取和删除都以该前缀为入口。
        return directoryPrefix;
    }

    /**
     * 使用指定的存储键上传技能文件。
     * @param fileObjects 技能文件集合，path 为目录内相对路径。
     * @param storageKey 完整的存储键（不含时间戳）。
     * @return 存储键。
     */
    public String uploadDirectoryWithKey(List<SkillFileObject> fileObjects, String storageKey) {
        // 步骤 1：确保桶可用，并校验指定 key 上传也必须携带文件列表。
        ensureBucketExists();
        if (fileObjects == null || fileObjects.isEmpty()) {
            throw new IllegalArgumentException(ErrorMessageCatalog.CHAT_SKILL_FILE_LIST_REQUIRED);
        }
        List<SkillFileObject> sortedFiles = fileObjects.stream()
            .sorted(Comparator.comparing(SkillFileObject::path))
            .toList();

        // 上传前先清空旧对象，避免删除文件后残留脏数据。
        deleteDirectory(storageKey);
        for (SkillFileObject fileObject : sortedFiles) {
            // 步骤 2：复用调用方指定的存储 key 作为目录前缀，逐个写入归一化后的相对路径。
            String normalizedPath = normalizeRelativePath(fileObject.path());
            String key = storageKey + "/" + normalizedPath;
            byte[] bytes = fileObject.bytes() == null ? new byte[0] : fileObject.bytes();
            rustFsS3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(StrUtil.blankToDefault(fileObject.contentType(), guessContentType(normalizedPath)))
                    .build(),
                RequestBody.fromBytes(bytes)
            );
        }
        // 步骤 3：返回原 storageKey，便于上层服务保持业务记录与对象目录一致。
        return storageKey;
    }

    /**
     * 根据对象键下载技能包字节。
     * @param objectKey 对象键。
     * @return 技能包字节。
     */
    public byte[] download(String objectKey) {
        // 步骤 1：读取前确认桶存在，避免新环境首次下载出现不明确的 SDK 错误。
        ensureBucketExists();

        // 步骤 2：对象 key 做 trim 与空值兜底，调用方仍需保证 key 真实存在。
        String normalizedObjectKey = StrUtil.blankToDefault(StrUtil.trim(objectKey), "");
        ResponseBytes<GetObjectResponse> responseBytes = rustFsS3Client.getObjectAsBytes(
            GetObjectRequest.builder()
                .bucket(bucketName)
                .key(normalizedObjectKey)
                .build()
        );
        // 步骤 3：只返回对象字节内容，隐藏 S3 响应对象与存储元数据。
        return responseBytes.asByteArray();
    }

    /**
     * 按目录前缀列举对象，返回相对路径与大小。
     * @param directoryPrefix 技能目录前缀。
     * @return 目录对象元数据列表。
     */
    public List<SkillObjectMetadata> listDirectory(String directoryPrefix) {
        // 步骤 1：归一化目录前缀并追加斜杠，保证只列举目录下的文件对象。
        ensureBucketExists();
        String normalizedPrefix = normalizeDirectoryPrefix(directoryPrefix);
        String queryPrefix = normalizedPrefix + "/";
        List<SkillObjectMetadata> objects = new ArrayList<>();
        String continuationToken = null;
        boolean truncated;
        do {
            // 步骤 2：按 S3 分页协议列举对象，continuationToken 用于读取下一页。
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(queryPrefix)
                .continuationToken(continuationToken)
                .build();
            ListObjectsV2Response response = rustFsS3Client.listObjectsV2(request);
            for (S3Object object : response.contents()) {
                String objectKey = object.key();
                if (!StrUtil.startWith(objectKey, queryPrefix)) {
                    // SDK 已按前缀过滤，这里保留防御性判断，避免异常数据泄漏到调用方。
                    continue;
                }
                String relativePath = objectKey.substring(queryPrefix.length());
                if (StrUtil.isBlank(relativePath) || StrUtil.endWith(relativePath, "/")) {
                    // 目录占位对象不代表真实技能文件，过滤后只返回可下载文件。
                    continue;
                }
                objects.add(new SkillObjectMetadata(objectKey, relativePath, object.size()));
            }
            truncated = Boolean.TRUE.equals(response.isTruncated());
            continuationToken = response.nextContinuationToken();
        } while (truncated);
        // 步骤 3：按相对路径排序，保证同一目录在不同存储返回顺序下结果稳定。
        objects.sort(Comparator.comparing(SkillObjectMetadata::relativePath));
        return objects;
    }

    /**
     * 删除目录前缀下的全部对象。
     * @param directoryPrefix 技能目录前缀。
     */
    public void deleteDirectory(String directoryPrefix) {
        // 步骤 1：归一化目录前缀并追加斜杠，删除范围只限定在该目录下。
        ensureBucketExists();
        String normalizedPrefix = normalizeDirectoryPrefix(directoryPrefix);
        String queryPrefix = normalizedPrefix + "/";
        String continuationToken = null;
        boolean truncated;
        do {
            // 步骤 2：分页列举目录对象，避免目录文件数量较多时漏删后续页。
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(queryPrefix)
                .continuationToken(continuationToken)
                .build();
            ListObjectsV2Response response = rustFsS3Client.listObjectsV2(request);
            for (S3Object object : response.contents()) {
                String objectKey = object.key();
                if (StrUtil.startWith(objectKey, queryPrefix) && !StrUtil.endWith(objectKey, "/")) {
                    // 步骤 3：只删除真实文件对象，目录占位对象不影响业务读取。
                    deleteObject(objectKey);
                }
            }
            truncated = Boolean.TRUE.equals(response.isTruncated());
            continuationToken = response.nextContinuationToken();
        } while (truncated);
    }

    /**
     * 按目录前缀与相对路径读取单个文件内容。
     * @param directoryPrefix 技能目录前缀。
     * @param relativePath 文件相对路径。
     * @return 文件字节内容。
     */
    public byte[] downloadDirectoryFile(String directoryPrefix, String relativePath) {
        // 步骤 1：分别校验目录前缀与文件相对路径，禁止路径穿越和空路径读取。
        String normalizedPrefix = normalizeDirectoryPrefix(directoryPrefix);
        String normalizedRelativePath = normalizeRelativePath(relativePath);

        // 步骤 2：拼接完整对象 key 后复用单对象下载逻辑，保持桶检查和 SDK 调用入口统一。
        return download(normalizedPrefix + "/" + normalizedRelativePath);
    }

    /**
     * 删除指定对象。
     * @param objectKey 对象键。
     */
    public void deleteObject(String objectKey) {
        // 步骤 1：删除前确认桶存在，并对空 key 做安全兜底。
        ensureBucketExists();
        String normalizedObjectKey = StrUtil.blankToDefault(StrUtil.trim(objectKey), "");
        if (StrUtil.isBlank(normalizedObjectKey)) {
            // 空 key 不代表有效对象，直接返回避免误发无意义删除请求。
            return;
        }
        // 步骤 2：删除指定对象；RustFS/S3 对不存在对象通常按幂等删除处理。
        rustFsS3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(normalizedObjectKey)
            .build());
    }

    private String buildObjectKey(String originalFilename) {
        // 步骤 1：清洗原始文件名，避免斜杠影响对象目录层级。
        String sanitizedName = StrUtil.removePrefix(originalFilename.trim(), "/");
        sanitizedName = sanitizedName.replace("\\", "_");
        if (StrUtil.isBlank(sanitizedName)) {
            // 文件名缺失时使用默认技能包名，保证对象 key 始终可生成。
            sanitizedName = "skill-package.skill";
        }
        // 步骤 2：规范化技能包前缀，末尾不保留斜杠，便于拼接时间戳。
        String prefix = StrUtil.removeSuffix(StrUtil.blankToDefault(skillPrefix, "chat-skills/packages"), "/");
        long now = System.currentTimeMillis();
        // 步骤 3：用时间戳降低同名对象覆盖概率，返回桶内路径。
        return prefix + "/" + now + "-" + sanitizedName;
    }

    private String buildDirectoryPrefix(String preferredDirectoryName) {
        // 步骤 1：使用调用方建议目录名作为技能目录标识，缺失时回退默认目录名。
        String rawName = StrUtil.blankToDefault(preferredDirectoryName, "skill-package");
        String sanitizedName = sanitizeObjectName(rawName);
        if (StrUtil.isBlank(sanitizedName)) {
            // 目录名清洗为空时使用稳定默认值，避免生成空前缀。
            sanitizedName = "skill-package";
        }
        // 步骤 2：拼接统一技能存储根前缀，形成后续目录级读写入口。
        String prefix = StrUtil.removeSuffix(StrUtil.blankToDefault(skillPrefix, "chat-skills/packages"), "/");
        return prefix + "/" + sanitizedName;
    }

    private String sanitizeObjectName(String rawName) {
        // 步骤 1：统一路径分隔符并仅保留最后一级名称，防止上传文件名携带目录逃逸。
        String name = StrUtil.blankToDefault(rawName, "")
            .replace("\\", "/");
        int index = name.lastIndexOf('/');
        if (index >= 0) {
            name = name.substring(index + 1);
        }
        // 步骤 2：仅保留对象名安全字符，其余字符替换为短横线以兼容 S3 key 与前端展示。
        name = name.trim()
            .replaceAll("[^a-zA-Z0-9._-]+", "-")
            .replaceAll("(^-+|-+$)", "");
        // 步骤 3：去掉扩展名，目录存储只需要技能包基础名称。
        return StrUtil.subBefore(name, ".", false);
    }

    private String normalizeDirectoryPrefix(String directoryPrefix) {
        // 步骤 1：统一路径分隔符并去掉首尾无效斜杠，保证目录前缀格式稳定。
        String normalized = StrUtil.blankToDefault(directoryPrefix, "")
            .replace("\\", "/")
            .trim();
        while (StrUtil.startWith(normalized, "./")) {
            normalized = normalized.substring(2);
        }
        normalized = StrUtil.removePrefix(normalized, "/");
        normalized = StrUtil.removeSuffix(normalized, "/");
        if (StrUtil.isBlank(normalized)) {
            // 目录前缀为空时无法限定对象范围，必须拒绝避免误删或误读根目录。
            throw new IllegalArgumentException(ErrorMessageCatalog.CHAT_SKILL_DIRECTORY_PREFIX_REQUIRED);
        }
        if (normalized.contains("..")) {
            // 禁止目录穿越片段，避免访问技能根目录之外的对象。
            throw new IllegalArgumentException(ErrorMessageCatalog.CHAT_SKILL_DIRECTORY_PREFIX_INVALID);
        }
        return normalized;
    }

    private String normalizeRelativePath(String relativePath) {
        // 步骤 1：统一相对路径分隔符，去掉入口传入的 ./ 与首尾斜杠。
        String normalized = StrUtil.blankToDefault(relativePath, "")
            .replace("\\", "/")
            .trim();
        while (StrUtil.startWith(normalized, "./")) {
            normalized = normalized.substring(2);
        }
        normalized = StrUtil.removePrefix(normalized, "/");
        normalized = StrUtil.removeSuffix(normalized, "/");
        if (StrUtil.isBlank(normalized)) {
            // 文件相对路径为空时无法定位对象，必须拒绝上传或读取。
            throw new IllegalArgumentException(ErrorMessageCatalog.CHAT_SKILL_FILE_PATH_REQUIRED);
        }
        if (normalized.contains("..")) {
            // 禁止路径穿越片段，避免技能包文件逃逸到目录前缀之外。
            throw new IllegalArgumentException(ErrorMessageCatalog.CHAT_SKILL_FILE_PATH_INVALID);
        }
        return normalized;
    }

    private String guessContentType(String filePath) {
        // 步骤 1：文本类技能配置使用 UTF-8 文本类型，方便存储端和调试工具直接预览。
        String normalizedPath = StrUtil.blankToDefault(filePath, "").toLowerCase(Locale.ROOT);
        if (normalizedPath.endsWith(".md") || normalizedPath.endsWith(".txt") || normalizedPath.endsWith(".yaml")
            || normalizedPath.endsWith(".yml") || normalizedPath.endsWith(".json")) {
            return "text/plain; charset=UTF-8";
        }
        // 步骤 2：无法识别的文件一律按二进制处理，避免错误声明文本编码。
        return "application/octet-stream";
    }

    private void ensureBucketExists() {
        if (bucketEnsured) {
            // 已确认过桶存在后直接返回，降低高频技能包接口的元数据请求成本。
            return;
        }
        try {
            // 步骤 1：优先探测桶是否存在，存在时只更新本地标记。
            rustFsS3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            bucketEnsured = true;
        } catch (NoSuchBucketException exception) {
            // 步骤 2：桶不存在时自动创建，便于新环境首次启动后直接上传技能包。
            log.info("RustFS 桶不存在，自动创建 bucket={}", bucketName);
            rustFsS3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            bucketEnsured = true;
        }
    }

    /**
     * 定义目录化技能文件对象。
     * @param path 相对路径。
     * @param bytes 文件字节。
     * @param contentType MIME 类型。
     */
    public record SkillFileObject(String path, byte[] bytes, String contentType) {
    }

    /**
     * 定义对象存储目录项元数据。
     * @param objectKey 对象完整 key。
     * @param relativePath 相对于目录前缀的路径。
     * @param size 文件大小。
     */
    public record SkillObjectMetadata(String objectKey, String relativePath, Long size) {
    }
}
