package com.codingx.common.storage.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 提供 RustFS（S3 兼容）客户端装配。
 */
@Configuration
public class RustFsConfig {

    /**
     * RustFS 服务地址，支持本地开发默认端口，也可通过配置切换到外部对象存储。
     */
    @Value("${rustfs.endpoint:http://localhost:9000}")
    private String endpoint;

    /**
     * RustFS 访问密钥 ID，用于构建 S3 兼容认证凭证。
     */
    @Value("${rustfs.access-key:rustfsadmin}")
    private String accessKey;

    /**
     * RustFS 访问密钥 secret，用于构建 S3 兼容认证凭证。
     */
    @Value("${rustfs.secret-key:rustfsadmin}")
    private String secretKey;

    /**
     * S3 区域标识，RustFS 对区域不敏感但 AWS SDK 构建客户端时必须提供。
     */
    @Value("${rustfs.region:us-east-1}")
    private String region;

    /**
     * 构建 S3Client 以访问 RustFS 对象存储。
     * @return S3 客户端。
     */
    @Bean
    public S3Client rustFsS3Client() {
        // 步骤 1：使用配置的 endpoint 和 region 构建 S3 兼容客户端，便于本地与部署环境切换。
        return S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            // 步骤 2：使用静态凭证连接 RustFS，避免依赖宿主机 AWS 默认凭证链。
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            // 步骤 3：RustFS/MinIO 类服务通常需要 path-style 访问，避免 bucket 被解析为子域名。
            .forcePathStyle(true)
            .httpClientBuilder(ApacheHttpClient.builder())
            .build();
    }
}


