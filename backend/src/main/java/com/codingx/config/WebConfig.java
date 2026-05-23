package com.codingx.config;
import cn.hutool.core.util.StrUtil;
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
        // 关键约束：跨域来源配置允许逗号分隔并自动去除空白，避免环境变量中夹带空格导致源匹配失败。
        String[] normalizedAllowedOrigins = StrUtil.splitTrim(allowedOrigins, ',').toArray(new String[0]);
        registry.addMapping("/**")
            .allowedOrigins(normalizedAllowedOrigins)
            // 关键约束：会话重命名走 PATCH，必须显式放行，否则预检请求会被 Spring 拒绝为 Invalid CORS request。
            .allowedMethods("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true);
    }
}
