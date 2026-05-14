package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 验证 Prompt 模板加载器能够从资源目录读取模板文本。
 */
class PromptTemplateLoaderTest {

    /**
     * 加载器应按名称读取 `.st` 模板文件内容。
     * @throws Exception 文件准备或读取失败时抛出。
     */
    @Test
    void loadReturnsTemplateContentByName() throws Exception {
        Path promptDir = Files.createTempDirectory("codingx-prompts");
        Files.writeString(promptDir.resolve("rewrite.st"), "rewrite prompt");
        PromptTemplateLoader loader = new PromptTemplateLoader(promptDir);

        String content = loader.load("rewrite");

        assertEquals("rewrite prompt", content);
    }
}
