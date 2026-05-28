package com.codingx.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 承载系统配置密钥加密所需的主密钥配置。
 */
@Data
@ConfigurationProperties(prefix = "app.config-crypto")
public class ConfigCryptoProperties {

    /**
     * 数据库存储敏感配置时使用的主密钥，必须由宿主环境注入，不允许进入系统配置表。
     */
    private String masterKey = "";

    /**
     * 主密钥版本号，便于后续密钥轮换时区分旧密文。
     */
    private String keyVersion = "v1";

    /**
     * 对称加密算法标识。
     */
    private String algorithm = "AES_GCM";
}
