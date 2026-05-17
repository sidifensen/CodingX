package com.codingx.storage;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

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
}

