package com.codingx.chat.application.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

/**
 * 负责从资源目录加载聊天编排所需的 Prompt 模板。
 */
@Service
public class PromptTemplateLoader {

    private final Path promptDirectory;

    /**
     * 使用默认资源目录构造加载器。
     */
    public PromptTemplateLoader() {
        this(Path.of("backend", "src", "main", "resources", "prompt"));
    }

    /**
     * 使用指定目录构造加载器，便于测试隔离。
     * @param promptDirectory Prompt 目录。
     */
    public PromptTemplateLoader(Path promptDirectory) {
        this.promptDirectory = promptDirectory;
    }

    /**
     * 按名称读取模板文本。
     * @param templateName 模板名，不含扩展名。
     * @return 模板内容。
     */
    public String load(String templateName) {
        try {
            return Files.readString(promptDirectory.resolve(templateName + ".st"));
        } catch (IOException exception) {
            throw new IllegalStateException("Prompt template not found: " + templateName, exception);
        }
    }
}
