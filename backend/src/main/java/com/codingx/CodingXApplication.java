package com.codingx;
import com.codingx.config.AiProperties;
import com.codingx.config.ChatExecutorRuntimeProperties;
import com.codingx.config.ChatMemoryProperties;
import com.codingx.config.RuntimeProperties;
import com.codingx.config.WebAccessProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 启动 CodingX 后端应用。
 */
@SpringBootApplication
@MapperScan("com.codingx.**.infrastructure.persistence.mapper")
@EnableConfigurationProperties({
    AiProperties.class,
    RuntimeProperties.class,
    ChatMemoryProperties.class,
    ChatExecutorRuntimeProperties.class,
    WebAccessProperties.class
})
public class CodingXApplication {

    /**
     * 启动应用入口。
     * @param args 启动参数。
     */
    public static void main(String[] args) {
        SpringApplication.run(CodingXApplication.class, args);
    }
}
