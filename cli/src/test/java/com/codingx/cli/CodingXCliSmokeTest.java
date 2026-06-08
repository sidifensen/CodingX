package com.codingx.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * CLI 工程烟雾测试，确保 Maven 子工程能加载入口类。
 */
class CodingXCliSmokeTest {

    @Test
    void mainClassShouldExposeProductName() {
        assertEquals("CodingX CLI", CodingXCli.productName());
    }

    @Test
    void tuiAuthOutputConsumerShouldNotPrintBrowserLoginUrlIntoTuiScreen() {
        // TUI 登录进度由模型渲染；认证服务内部 URL 不能直接写入 stdout 破坏普通屏幕刷新。
        assertDoesNotThrow(() -> CodingXCli.TUI_AUTH_OUTPUT_CONSUMER.accept("正在打开浏览器登录 CodingX"));
    }
}
