package com.codingx.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 验证数据库初始化配置默认关闭，避免开发环境每次启动重复执行 schema/init 脚本。
 */
class DatabaseInitializationConfigTest {

    /**
     * application.yml 必须声明 SQL 初始化模式与脚本路径，默认禁用初始化并支持环境变量覆盖。
     * @throws IOException 读取配置文件失败时抛出。
     */
    @Test
    void applicationYamlContainsExplicitSqlInitSettings() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(applicationYaml.contains("mode: ${SPRING_SQL_INIT_MODE:never}"), "缺少默认禁用 SQL 初始化配置");
        assertTrue(applicationYaml.contains("schema-locations: classpath:db/schema.sql"), "缺少 schema 初始化脚本路径配置");
        assertTrue(applicationYaml.contains("data-locations: classpath:db/init.sql"), "缺少 data 初始化脚本路径配置");
    }
}
