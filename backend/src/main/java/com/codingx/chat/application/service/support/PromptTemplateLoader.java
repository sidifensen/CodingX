package com.codingx.chat.application.service;

import com.codingx.common.error.ErrorMessageCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * 负责加载和渲染聊天编排所需的 Prompt 模板，兼容测试目录与运行期类路径资源。
 */
@Service
public class PromptTemplateLoader {

    /** Prompt 文件目录，用于测试场景从指定目录读取模板。 */
    private final Path promptDirectory;
    /** Spring 资源加载器，用于运行期从 classpath 加载 Prompt 模板。 */
    private final ResourceLoader resourceLoader;

    /**
     * 使用 Spring 资源加载器构造运行期 Prompt 加载器。
     * @param resourceLoader Spring 资源加载器。
     */
    @Autowired
    public PromptTemplateLoader(ResourceLoader resourceLoader) {
        this.promptDirectory = null;
        this.resourceLoader = resourceLoader;
    }

    /**
     * 使用指定目录构造加载器，便于测试隔离。
     * @param promptDirectory Prompt 目录。
     */
    public PromptTemplateLoader(Path promptDirectory) {
        this.promptDirectory = promptDirectory;
        this.resourceLoader = null;
    }

    /**
     * 按名称读取模板文本。
     * @param templateName 模板名，不含扩展名。
     * @return 模板内容。
     */
    public String load(String templateName) {
        try {
            if (promptDirectory != null) {
                return Files.readString(promptDirectory.resolve(templateName + ".st"));
            }
            Resource resource = resourceLoader.getResource("classpath:prompt/" + templateName + ".st");
            if (!resource.exists()) {
                throw new IllegalStateException(ErrorMessageCatalog.CHAT_PROMPT_TEMPLATE_NOT_FOUND + "：" + templateName);
            }
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(ErrorMessageCatalog.CHAT_PROMPT_TEMPLATE_NOT_FOUND + "：" + templateName, exception);
        }
    }

    /**
     * 渲染模板中的简单命名占位符，适配 `{slot}` 风格 Prompt 资产。
     * @param templateName 模板名。
     * @param slots 占位符映射。
     * @return 渲染后的 Prompt。
     */
    public String render(String templateName, Map<String, String> slots) {
        String content = load(templateName);
        if (slots == null || slots.isEmpty()) {
            return content;
        }
        String rendered = content;
        for (Map.Entry<String, String> entry : slots.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return rendered;
    }
}
