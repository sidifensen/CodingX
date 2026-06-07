package com.codingx.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 验证 CLI 浏览器登录链路的 Sa-Token 白名单，防止回调兑换阶段被网页登录态拦截。
 */
class SaTokenConfigCliAuthTest {

    /**
     * CLI token 兑换和设备码轮询由终端匿名发起，不能被全局登录拦截器提前拦截。
     *
     * @throws IOException 读取鉴权配置失败时抛出。
     */
    @Test
    void cliTokenExchangeAndDevicePollingShouldBypassGlobalLoginInterceptor() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/codingx/config/SaTokenConfig.java"));

        assertTrue(source.contains("\"/api/auth/cli/token\""), "CLI 授权码兑换接口必须允许匿名访问");
        assertTrue(source.contains("\"/api/auth/cli/device/start\""), "CLI 设备码创建接口必须允许匿名访问");
        assertTrue(source.contains("\"/api/auth/cli/device/token\""), "CLI 设备码轮询接口必须允许匿名访问");
        assertFalse(source.contains("\"/api/auth/cli/**\""), "不能放开所有 CLI 授权接口，否则浏览器确认授权会绕过登录态");
    }
}
