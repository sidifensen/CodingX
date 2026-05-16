package com.codingx.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 验证数据库初始化配置已显式启用，避免 PostgreSQL 环境漏执行建表脚本。
 */
class DatabaseInitializationConfigTest {

    /**
     * application.yml 必须声明 SQL 初始化模式与脚本路径，确保 schema/init 脚本被稳定执行。
     * @throws IOException 读取配置文件失败时抛出。
     */
    @Test
    void applicationYamlContainsExplicitSqlInitSettings() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(applicationYaml.contains("mode: always"), "缺少 spring.sql.init.mode=always 配置");
        assertTrue(applicationYaml.contains("schema-locations: classpath:db/schema.sql"), "缺少 schema 初始化脚本路径配置");
        assertTrue(applicationYaml.contains("data-locations: classpath:db/init.sql"), "缺少 data 初始化脚本路径配置");
    }
}
