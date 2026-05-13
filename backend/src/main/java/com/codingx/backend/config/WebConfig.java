package com.codingx.backend.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures the Spring beans and infrastructure required by WebConfig.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * allowedOrigins value.
     */
    @Value("${app.security.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    /**
     * Executes the logic defined by addCorsMappings.
     * @param registry input argument.
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
