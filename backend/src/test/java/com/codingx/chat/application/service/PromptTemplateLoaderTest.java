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

    /**
     * 渲染器应按占位符替换模板变量，便于复用配置台 Prompt 资产。
     * @throws Exception 文件准备或读取失败时抛出。
     */
    @Test
    void renderReplacesNamedSlots() throws Exception {
        Path promptDir = Files.createTempDirectory("codingx-prompts");
        Files.writeString(promptDir.resolve("guidance-prompt.st"), "关于{topic_name}，候选如下：\n{options}");
        PromptTemplateLoader loader = new PromptTemplateLoader(promptDir);

        String content = loader.render("guidance-prompt", java.util.Map.of(
            "topic_name", "系统介绍",
            "options", "1) OA系统\n2) 保险系统"
        ));

        assertEquals("关于系统介绍，候选如下：\n1) OA系统\n2) 保险系统", content);
    }
}
