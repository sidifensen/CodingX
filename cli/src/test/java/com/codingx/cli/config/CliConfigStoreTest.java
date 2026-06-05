package com.codingx.cli.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CLI 配置存储测试，确保 satoken 只写入用户主目录配置文件。
 */
class CliConfigStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadShouldUseUserHomeConfigDirectory() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Path userHome = tempDir.resolve("home");
        Files.createDirectories(workspace);
        Files.createDirectories(userHome);
        CliConfigStore store = new CliConfigStore(userHome);

        store.save(new CliConfig("http://localhost:5001", "test-token", "conservative", null));

        Path configFile = userHome.resolve(".codingx").resolve("cli.yml");
        assertTrue(Files.exists(configFile));
        assertFalse(Files.exists(workspace.resolve("cli.yml")));
        CliConfig loaded = store.load();
        assertEquals("http://localhost:5001", loaded.serverUrl());
        assertEquals("test-token", loaded.token());
        assertEquals("conservative", loaded.approvalPolicy());
    }
}
