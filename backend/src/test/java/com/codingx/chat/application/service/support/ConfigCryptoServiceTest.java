package com.codingx.chat.application.service.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codingx.config.ConfigCryptoProperties;
import org.junit.jupiter.api.Test;

/**
 * 验证系统配置密钥服务的加解密、脱敏与主密钥校验行为。
 */
class ConfigCryptoServiceTest {

    /**
     * 合法主密钥下，加密结果不应等于明文，且必须可以解回原值。
     */
    @Test
    void encryptAndDecryptRoundTrip() {
        ConfigCryptoProperties properties = new ConfigCryptoProperties();
        properties.setMasterKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        ConfigCryptoService service = new ConfigCryptoService(properties);

        String cipherText = service.encrypt("sk-test-123456");

        assertNotEquals("sk-test-123456", cipherText);
        assertEquals("sk-test-123456", service.decrypt(cipherText));
    }

    /**
     * 脱敏展示必须只暴露首尾少量字符，避免后台列表直接回显明文密钥。
     */
    @Test
    void maskHidesSensitiveBody() {
        ConfigCryptoProperties properties = new ConfigCryptoProperties();
        properties.setMasterKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        ConfigCryptoService service = new ConfigCryptoService(properties);

        assertEquals("sk-t***3456", service.mask("sk-test-123456"));
    }

    /**
     * 合法主密钥初始化不应抛错，确保服务可在运行时按需执行敏感配置加解密。
     */
    @Test
    void validMasterKeyCreatesService() {
        ConfigCryptoProperties properties = new ConfigCryptoProperties();
        properties.setMasterKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

        assertDoesNotThrow(() -> new ConfigCryptoService(properties));
    }

    /**
     * 缺失主密钥时允许应用启动，但首次执行加密必须失败，避免无敏感配置环境被强制阻断。
     */
    @Test
    void missingMasterKeyFailsWhenEncrypting() {
        ConfigCryptoProperties properties = new ConfigCryptoProperties();
        ConfigCryptoService service = new ConfigCryptoService(properties);

        assertThrows(IllegalStateException.class, () -> service.encrypt("plain-secret"));
    }
}
