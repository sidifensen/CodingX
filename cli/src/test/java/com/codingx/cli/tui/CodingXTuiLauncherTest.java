package com.codingx.cli.tui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * TUI 启动器测试，锁定普通终端输出模式，避免启动页再次被 alt screen 撑成全屏空白界面。
 */
class CodingXTuiLauncherTest {

    /**
     * 官方 Codex 风格会保留用户输入命令的 shell 上下文，因此本地启动器不能进入 alt screen。
     *
     * @throws Exception 源文件读取失败时让测试直接失败。
     */
    @Test
    void launcherShouldKeepShellContextInsteadOfAltScreen() throws Exception {
        Path launcher = Path.of(System.getProperty("user.dir"))
            .resolve("src")
            .resolve("main")
            .resolve("java")
            .resolve("com")
            .resolve("codingx")
            .resolve("cli")
            .resolve("tui")
            .resolve("CodingXTuiLauncher.java");

        String source = Files.readString(launcher);

        assertFalse(source.contains("withAltScreen()"));
    }
}
