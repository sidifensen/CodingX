package com.codingx.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 负责配置 WebConfig 所需的 Spring Bean 与基础设施。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * allowedOrigins 字段。
     */
    @Value("${app.security.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    /**
     * 执行 addCorsMappings 定义的处理逻辑。
     * @param registry 输入参数。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins(allowedOrigins.split(","))
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true);
    }
}
