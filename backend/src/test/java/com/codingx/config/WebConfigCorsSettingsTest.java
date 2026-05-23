package com.codingx.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 验证跨域配置覆盖会话重命名链路所需的 PATCH 请求，避免前端重命名被 CORS 拦截。
 */
class WebConfigCorsSettingsTest {

    /**
     * WebConfig 必须显式允许 PATCH 方法，并使用 splitTrim 规避逗号分隔来源中的空格问题。
     * @throws IOException 读取配置文件失败时抛出。
     */
    @Test
    void webConfigShouldAllowPatchAndTrimOrigins() throws IOException {
        String webConfigSource = Files.readString(Path.of("src/main/java/com/codingx/config/WebConfig.java"));

        assertTrue(webConfigSource.contains("StrUtil.splitTrim(allowedOrigins, ',')"), "CORS 来源配置应使用 splitTrim 去除空格");
        assertTrue(webConfigSource.contains("\"PATCH\""), "CORS 允许方法必须包含 PATCH");
    }
}
