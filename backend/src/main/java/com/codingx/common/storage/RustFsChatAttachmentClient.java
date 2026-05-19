package com.codingx.common.storage;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 封装聊天附件上传与下载能力，统一返回桶内对象 key。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RustFsChatAttachmentClient {

    private final S3Client rustFsS3Client;

    @Value("${rustfs.bucket:codingx-skills}")
    private String bucketName;

    @Value("${rustfs.attachment-prefix:chat/attachments}")
    private String attachmentPrefix;

    private volatile boolean bucketEnsured;

    /**
     * 上传附件并返回对象 key。
     * @param bytes 文件字节。
     * @param originalFilename 原始文件名。
     * @param contentType MIME 类型。
     * @return 对象 key。
     */
    public String upload(byte[] bytes, String originalFilename, String contentType) {
        ensureBucketExists();
        String key = buildObjectKey(StrUtil.blankToDefault(originalFilename, "attachment.bin"));
        rustFsS3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(StrUtil.blankToDefault(contentType, "application/octet-stream"))
                .build(),
            RequestBody.fromBytes(bytes)
        );
        return key;
    }

    /**
     * 根据对象 key 下载附件字节。
     * @param objectKey 对象键。
     * @return 文件字节。
     */
    public byte[] download(String objectKey) {
        ensureBucketExists();
        ResponseBytes<GetObjectResponse> responseBytes = rustFsS3Client.getObjectAsBytes(
            GetObjectRequest.builder()
                .bucket(bucketName)
                .key(StrUtil.blankToDefault(StrUtil.trim(objectKey), ""))
                .build()
        );
        return responseBytes.asByteArray();
    }

    private String buildObjectKey(String originalFilename) {
        String sanitizedName = StrUtil.removePrefix(originalFilename.trim(), "/");
        sanitizedName = sanitizedName.replace("\\", "_");
        if (StrUtil.isBlank(sanitizedName)) {
            sanitizedName = "attachment.bin";
        }
        String prefix = StrUtil.removeSuffix(StrUtil.blankToDefault(attachmentPrefix, "chat/attachments"), "/");
        return prefix + "/" + System.currentTimeMillis() + "-" + sanitizedName;
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

