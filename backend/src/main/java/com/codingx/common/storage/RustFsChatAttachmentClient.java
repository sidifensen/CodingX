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

    /**
     * RustFS 的 S3 兼容客户端，用于执行聊天附件对象的上传与读取。
     */
    private final S3Client rustFsS3Client;

    /**
     * 聊天附件所在桶名，来自 rustfs.bucket 配置，缺省与技能包共用 codingx-skills。
     */
    @Value("${rustfs.bucket:codingx-skills}")
    private String bucketName;

    /**
     * 聊天附件对象前缀，隔离普通聊天附件与技能包文件，避免对象 key 语义混用。
     */
    @Value("${rustfs.attachment-prefix:chat/attachments}")
    private String attachmentPrefix;

    /**
     * 桶存在性检查标记，避免每次附件读写都重复请求 RustFS。
     */
    private volatile boolean bucketEnsured;

    /**
     * 上传附件并返回对象 key。
     * @param bytes 文件字节。
     * @param originalFilename 原始文件名。
     * @param contentType MIME 类型。
     * @return 对象 key。
     */
    public String upload(byte[] bytes, String originalFilename, String contentType) {
        // 步骤 1：先确认桶存在；首次访问自动创建，后续通过 bucketEnsured 跳过检查。
        ensureBucketExists();

        // 步骤 2：根据原始文件名生成带时间戳的对象 key，避免同名附件互相覆盖。
        String key = buildObjectKey(StrUtil.blankToDefault(originalFilename, "attachment.bin"));

        // 步骤 3：写入 RustFS，并在 contentType 缺失时使用通用二进制类型兜底。
        rustFsS3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(StrUtil.blankToDefault(contentType, "application/octet-stream"))
                .build(),
            RequestBody.fromBytes(bytes)
        );
        // 步骤 4：返回桶内对象 key，业务表只保存 key，不耦合对象存储访问地址。
        return key;
    }

    /**
     * 根据对象 key 下载附件字节。
     * @param objectKey 对象键。
     * @return 文件字节。
     */
    public byte[] download(String objectKey) {
        // 步骤 1：下载前确认桶已初始化，保证本地开发首次读取也能得到明确的存储状态。
        ensureBucketExists();

        // 步骤 2：对象 key 做 trim 与空值兜底，避免调用方传入 null 导致 SDK 空指针。
        ResponseBytes<GetObjectResponse> responseBytes = rustFsS3Client.getObjectAsBytes(
            GetObjectRequest.builder()
                .bucket(bucketName)
                .key(StrUtil.blankToDefault(StrUtil.trim(objectKey), ""))
                .build()
        );
        // 步骤 3：只向业务层暴露文件字节，隐藏 S3 响应对象与元数据细节。
        return responseBytes.asByteArray();
    }

    private String buildObjectKey(String originalFilename) {
        // 步骤 1：去掉开头斜杠并替换反斜杠，避免用户文件名影响对象目录结构。
        String sanitizedName = StrUtil.removePrefix(originalFilename.trim(), "/");
        sanitizedName = sanitizedName.replace("\\", "_");
        if (StrUtil.isBlank(sanitizedName)) {
            // 文件名清洗为空时使用稳定默认名，保证对象 key 始终可生成。
            sanitizedName = "attachment.bin";
        }
        // 步骤 2：规范化配置前缀，末尾不保留斜杠，便于拼接时间戳和文件名。
        String prefix = StrUtil.removeSuffix(StrUtil.blankToDefault(attachmentPrefix, "chat/attachments"), "/");
        // 步骤 3：使用毫秒时间戳降低同名覆盖概率，最终 key 只表示桶内路径。
        return prefix + "/" + System.currentTimeMillis() + "-" + sanitizedName;
    }

    private void ensureBucketExists() {
        if (bucketEnsured) {
            // 已完成桶检查后直接返回，降低高频附件接口的存储元数据请求成本。
            return;
        }
        try {
            // 步骤 1：优先探测桶是否存在，存在时只更新本地标记。
            rustFsS3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            bucketEnsured = true;
        } catch (NoSuchBucketException exception) {
            // 步骤 2：桶不存在时自动创建，便于本地开发和新环境首次启动自愈。
            log.info("RustFS 桶不存在，自动创建 bucket={}", bucketName);
            rustFsS3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            bucketEnsured = true;
        }
    }
}

