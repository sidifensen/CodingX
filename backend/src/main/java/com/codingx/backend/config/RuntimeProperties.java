package com.codingx.backend.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures the Spring beans and infrastructure required by RuntimeProperties.
 */
@Data
@ConfigurationProperties(prefix = "app.runtime")
public class RuntimeProperties {

    private long mockStepDelayMs = 300L;
    private String mockFailKeyword = "fail";
}
