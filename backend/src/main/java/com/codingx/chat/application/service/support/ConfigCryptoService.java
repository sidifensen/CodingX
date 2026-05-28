package com.codingx.chat.application.service.support;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import com.codingx.config.ConfigCryptoProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * 为系统配置中的敏感值提供统一加解密与脱敏能力。
 */
@Service
public class ConfigCryptoService {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH = 12;

    private final ConfigCryptoProperties configCryptoProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 构造阶段只保留配置引用，主密钥在首次加解密时再校验，避免未使用敏感配置的环境无法启动。
     * @param configCryptoProperties 主密钥配置。
     */
    public ConfigCryptoService(ConfigCryptoProperties configCryptoProperties) {
        this.configCryptoProperties = configCryptoProperties;
    }

    /**
     * 使用主密钥加密敏感明文。
     * @param plainText 明文。
     * @return Base64 编码的密文。
     */
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(decodeKey(), AES_ALGORITHM), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.encode(buffer.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("系统配置敏感值加密失败", exception);
        }
    }

    /**
     * 使用主密钥解密数据库中的密文。
     * @param cipherText Base64 编码密文。
     * @return 解密后的明文。
     */
    public String decrypt(String cipherText) {
        if (StrUtil.isBlank(cipherText)) {
            throw new IllegalStateException("系统配置敏感值缺少密文");
        }
        try {
            byte[] payload = Base64.decode(cipherText);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(decodeKey(), AES_ALGORITHM), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("系统配置敏感值解密失败", exception);
        }
    }

    /**
     * 生成后台列表展示用脱敏值，避免直接回显完整密钥。
     * @param plainText 明文。
     * @return 脱敏字符串。
     */
    public String mask(String plainText) {
        if (StrUtil.isBlank(plainText)) {
            return "";
        }
        String normalized = plainText.trim();
        if (normalized.length() <= 8) {
            return normalized.charAt(0) + "***" + normalized.charAt(normalized.length() - 1);
        }
        return normalized.substring(0, 4) + "***" + normalized.substring(normalized.length() - 4);
    }

    /**
     * 返回当前加密算法标识，便于密文入库时记录元数据。
     * @return 算法标识。
     */
    public String algorithm() {
        return StrUtil.blankToDefault(configCryptoProperties.getAlgorithm(), "AES_GCM");
    }

    /**
     * 返回当前主密钥版本。
     * @return 主密钥版本。
     */
    public String keyVersion() {
        return StrUtil.blankToDefault(configCryptoProperties.getKeyVersion(), "v1");
    }

    private byte[] decodeKey() {
        String base64Key = StrUtil.trimToEmpty(configCryptoProperties.getMasterKey());
        if (StrUtil.isBlank(base64Key)) {
            throw new IllegalStateException("APP_CONFIG_MASTER_KEY 未配置，无法启用系统配置密钥加密");
        }
        byte[] key = Base64.decode(base64Key);
        if (key.length != 32) {
            throw new IllegalStateException("APP_CONFIG_MASTER_KEY 必须是 32 字节 AES 密钥的 Base64 编码");
        }
        return key;
    }
}
