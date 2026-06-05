package com.codingx.cli.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 负责读写 CodingX CLI 用户级配置，避免把 satoken 写入项目仓库。
 */
public class CliConfigStore {

    /**
     * 用户主目录路径，用于隔离 CLI 配置和当前工作区。
     */
    private final Path userHome;

    /**
     * @param userHome 当前系统用户主目录。
     */
    public CliConfigStore(Path userHome) {
        this.userHome = userHome;
    }

    /**
     * 加载配置；配置不存在时返回默认值。
     *
     * @return CLI 配置。
     */
    public CliConfig load() {
        Path configFile = configFile();
        if (!Files.exists(configFile)) {
            return CliConfig.defaults();
        }
        Yaml yaml = new Yaml();
        try (Reader reader = Files.newBufferedReader(configFile)) {
            Object loaded = yaml.load(reader);
            if (!(loaded instanceof Map<?, ?> map)) {
                return CliConfig.defaults();
            }
            return new CliConfig(
                stringValue(map.get("serverUrl"), CliConfig.defaults().serverUrl()),
                stringValue(map.get("token"), ""),
                stringValue(map.get("approvalPolicy"), CliConfig.defaults().approvalPolicy()),
                stringValue(map.get("lastSessionId"), null)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("读取 CLI 配置失败", exception);
        }
    }

    /**
     * 保存配置到用户主目录。
     *
     * @param config 配置快照。
     */
    public void save(CliConfig config) {
        Path configFile = configFile();
        try {
            Files.createDirectories(configFile.getParent());
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("serverUrl", config.serverUrl());
            values.put("token", config.token());
            values.put("approvalPolicy", config.approvalPolicy());
            values.put("lastSessionId", config.lastSessionId());
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                new Yaml().dump(values, writer);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("保存 CLI 配置失败", exception);
        }
    }

    /**
     * @return 配置文件路径。
     */
    public Path configFile() {
        return userHome.resolve(".codingx").resolve("cli.yml");
    }

    /**
     * 读取 YAML 文本字段；空值回退默认值，保证缺失配置不会破坏首次启动。
     *
     * @param value YAML 中读取出的原始值。
     * @param defaultValue 缺失或空白时的默认值。
     * @return 可直接写入配置对象的文本值。
     */
    private String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }
}
