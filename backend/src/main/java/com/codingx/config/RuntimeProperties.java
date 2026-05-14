package com.codingx.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 负责配置 RuntimeProperties 所需的 Spring Bean 与基础设施。
 */
@Data
@ConfigurationProperties(prefix = "app.runtime")
public class RuntimeProperties {

    private long mockStepDelayMs = 300L;
    private String mockFailKeyword = "fail";
    private boolean useRedisStateStore = false;
}
