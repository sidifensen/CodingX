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

    @Value("${rustfs.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${rustfs.access-key:rustfsadmin}")
    private String accessKey;

    @Value("${rustfs.secret-key:rustfsadmin}")
    private String secretKey;

    @Value("${rustfs.region:us-east-1}")
    private String region;

    /**
     * 构建 S3Client 以访问 RustFS 对象存储。
     * @return S3 客户端。
     */
    @Bean
    public S3Client rustFsS3Client() {
        return S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .forcePathStyle(true)
            .httpClientBuilder(ApacheHttpClient.builder())
            .build();
    }
}


