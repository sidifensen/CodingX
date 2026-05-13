package com.codingx.backend;

import com.codingx.backend.config.AiProperties;
import com.codingx.backend.config.RuntimeProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan("com.codingx.backend")
@EnableConfigurationProperties({AiProperties.class, RuntimeProperties.class})
public class CodingXApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodingXApplication.class, args);
    }
}