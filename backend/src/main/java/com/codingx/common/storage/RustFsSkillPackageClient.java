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

    private final S3Client rustFsS3Client;

    @Value("${rustfs.bucket:codingx-skills}")
    private String bucketName;

    @Value("${rustfs.skill-prefix:chat-skills/packages}")
    private String skillPrefix;

    private volatile boolean bucketEnsured;

    /**
     * 上传技能包并返回对象键。
     * @param bytes 文件字节。
     * @param originalFilename 上传原始文件名。
     * @return 对象 key（不含 bucket）。
     */
    public String upload(byte[] bytes, String originalFilename) {
        ensureBucketExists();
        String normalizedName = StrUtil.blankToDefault(originalFilename, "skill-package.skill");
        String key = buildObjectKey(normalizedName);
        rustFsS3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("application/octet-stream")
                .build(),
            RequestBody.fromBytes(bytes)
        );
        return key;
    }

    /**
     * 按目录批量上传技能文件并返回目录前缀。
     * @param fileObjects 技能文件集合，path 为目录内相对路径。
     * @param preferredDirectoryName 目录名称建议值（通常为技能编码）。
     * @return 目录前缀 key（不含 bucket）。
     */
    public String uploadDirectory(List<SkillFileObject> fileObjects, String preferredDirectoryName) {
        ensureBucketExists();
        if (fileObjects == null || fileObjects.isEmpty()) {
            throw new IllegalArgumentException(ErrorMessageCatalog.CHAT_SKILL_FILE_LIST_REQUIRED);
        }
        String directoryPrefix = buildDirectoryPrefix(preferredDirectoryName);
        List<SkillFileObject> sortedFiles = fileObjects.stream()
            .sorted(Comparator.comparing(SkillFileObject::path))
            .toList();
        // 固定目录前缀时，上传前先清空旧对象，避免删除文件后残留脏数据。
        deleteDirectory(directoryPrefix);
        for (SkillFileObject fileObject : sortedFiles) {
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
        return directoryPrefix;
    }

    /**
     * 使用指定的存储键上传技能文件。
     * @param fileObjects 技能文件集合，path 为目录内相对路径。
     * @param storageKey 完整的存储键（不含时间戳）。
     * @return 存储键。
     */
    public String uploadDirectoryWithKey(List<SkillFileObject> fileObjects, String storageKey) {
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
        return storageKey;
    }

    /**
     * 根据对象键下载技能包字节。
     * @param objectKey 对象键。
     * @return 技能包字节。
     */
    public byte[] download(String objectKey) {
        ensureBucketExists();
        String normalizedObjectKey = StrUtil.blankToDefault(StrUtil.trim(objectKey), "");
        ResponseBytes<GetObjectResponse> responseBytes = rustFsS3Client.getObjectAsBytes(
            GetObjectRequest.builder()
                .bucket(bucketName)
                .key(normalizedObjectKey)
                .build()
        );
        return responseBytes.asByteArray();
    }

    /**
     * 按目录前缀列举对象，返回相对路径与大小。
     * @param directoryPrefix 技能目录前缀。
     * @return 目录对象元数据列表。
     */
    public List<SkillObjectMetadata> listDirectory(String directoryPrefix) {
        ensureBucketExists();
        String normalizedPrefix = normalizeDirectoryPrefix(directoryPrefix);
        String queryPrefix = normalizedPrefix + "/";
        List<SkillObjectMetadata> objects = new ArrayList<>();
        String continuationToken = null;
        boolean truncated;
        do {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(queryPrefix)
                .continuationToken(continuationToken)
                .build();
            ListObjectsV2Response response = rustFsS3Client.listObjectsV2(request);
            for (S3Object object : response.contents()) {
                String objectKey = object.key();
                if (!StrUtil.startWith(objectKey, queryPrefix)) {
                    continue;
                }
                String relativePath = objectKey.substring(queryPrefix.length());
                if (StrUtil.isBlank(relativePath) || StrUtil.endWith(relativePath, "/")) {
                    continue;
                }
                objects.add(new SkillObjectMetadata(objectKey, relativePath, object.size()));
            }
            truncated = Boolean.TRUE.equals(response.isTruncated());
            continuationToken = response.nextContinuationToken();
        } while (truncated);
        objects.sort(Comparator.comparing(SkillObjectMetadata::relativePath));
        return objects;
    }

    /**
     * 删除目录前缀下的全部对象。
     * @param directoryPrefix 技能目录前缀。
     */
    public void deleteDirectory(String directoryPrefix) {
        ensureBucketExists();
        String normalizedPrefix = normalizeDirectoryPrefix(directoryPrefix);
        String queryPrefix = normalizedPrefix + "/";
        String continuationToken = null;
        boolean truncated;
        do {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(queryPrefix)
                .continuationToken(continuationToken)
                .build();
            ListObjectsV2Response response = rustFsS3Client.listObjectsV2(request);
            for (S3Object object : response.contents()) {
                String objectKey = object.key();
                if (StrUtil.startWith(objectKey, queryPrefix) && !StrUtil.endWith(objectKey, "/")) {
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
        String normalizedPrefix = normalizeDirectoryPrefix(directoryPrefix);
        String normalizedRelativePath = normalizeRelativePath(relativePath);
        return download(normalizedPrefix + "/" + normalizedRelativePath);
    }

    /**
     * 删除指定对象。
     * @param objectKey 对象键。
     */
    public void deleteObject(String objectKey) {
        ensureBucketExists();
        String normalizedObjectKey = StrUtil.blankToDefault(StrUtil.trim(objectKey), "");
        if (StrUtil.isBlank(normalizedObjectKey)) {
            return;
        }
        rustFsS3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(normalizedObjectKey)
            .build());
    }

    private String buildObjectKey(String originalFilename) {
        String sanitizedName = StrUtil.removePrefix(originalFilename.trim(), "/");
        sanitizedName = sanitizedName.replace("\\", "_");
        if (StrUtil.isBlank(sanitizedName)) {
            sanitizedName = "skill-package.skill";
        }
        String prefix = StrUtil.removeSuffix(StrUtil.blankToDefault(skillPrefix, "chat-skills/packages"), "/");
        long now = System.currentTimeMillis();
        return prefix + "/" + now + "-" + sanitizedName;
    }

    private String buildDirectoryPrefix(String preferredDirectoryName) {
        String rawName = StrUtil.blankToDefault(preferredDirectoryName, "skill-package");
        String sanitizedName = sanitizeObjectName(rawName);
        if (StrUtil.isBlank(sanitizedName)) {
            sanitizedName = "skill-package";
        }
        String prefix = StrUtil.removeSuffix(StrUtil.blankToDefault(skillPrefix, "chat-skills/packages"), "/");
        return prefix + "/" + sanitizedName;
    }

    private String sanitizeObjectName(String rawName) {
        String name = StrUtil.blankToDefault(rawName, "")
            .replace("\\", "/");
        int index = name.lastIndexOf('/');
        if (index >= 0) {
            name = name.substring(index + 1);
        }
        name = name.trim()
            .replaceAll("[^a-zA-Z0-9._-]+", "-")
            .replaceAll("(^-+|-+$)", "");
        return StrUtil.subBefore(name, ".", false);
    }

    private String normalizeDirectoryPrefix(String directoryPrefix) {
        String normalized = StrUtil.blankToDefault(directoryPrefix, "")
            .replace("\\", "/")
            .trim();
        while (StrUtil.startWith(normalized, "./")) {
            normalized = normalized.substring(2);
        }
        normalized = StrUtil.removePrefix(normalized, "/");
        normalized = StrUtil.removeSuffix(normalized, "/");
        if (StrUtil.isBlank(normalized)) {
            throw new IllegalArgumentException(ErrorMessageCatalog.CHAT_SKILL_DIRECTORY_PREFIX_REQUIRED);
        }
        if (normalized.contains("..")) {
            throw new IllegalArgumentException(ErrorMessageCatalog.CHAT_SKILL_DIRECTORY_PREFIX_INVALID);
        }
        return normalized;
    }

    private String normalizeRelativePath(String relativePath) {
        String normalized = StrUtil.blankToDefault(relativePath, "")
            .replace("\\", "/")
            .trim();
        while (StrUtil.startWith(normalized, "./")) {
            normalized = normalized.substring(2);
        }
        normalized = StrUtil.removePrefix(normalized, "/");
        normalized = StrUtil.removeSuffix(normalized, "/");
        if (StrUtil.isBlank(normalized)) {
            throw new IllegalArgumentException(ErrorMessageCatalog.CHAT_SKILL_FILE_PATH_REQUIRED);
        }
        if (normalized.contains("..")) {
            throw new IllegalArgumentException(ErrorMessageCatalog.CHAT_SKILL_FILE_PATH_INVALID);
        }
        return normalized;
    }

    private String guessContentType(String filePath) {
        String normalizedPath = StrUtil.blankToDefault(filePath, "").toLowerCase(Locale.ROOT);
        if (normalizedPath.endsWith(".md") || normalizedPath.endsWith(".txt") || normalizedPath.endsWith(".yaml")
            || normalizedPath.endsWith(".yml") || normalizedPath.endsWith(".json")) {
            return "text/plain; charset=UTF-8";
        }
        return "application/octet-stream";
    }

    private void ensureBucketExists() {
        if (bucketEnsured) {
            return;
        }
        try {
            rustFsS3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            bucketEnsured = true;
        } catch (NoSuchBucketException exception) {
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

