package com.codingx.config;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 基础配置，当前只集中管理跨域策略，避免各 Controller 分散声明 CORS。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 允许跨域访问的前端来源，来自 app.security.cors.allowed-origins，支持英文逗号分隔多个来源。
     */
    @Value("${app.security.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    /**
     * 注册全局跨域规则。
     * @param registry Spring MVC 跨域规则注册器。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 步骤 1：跨域来源配置允许逗号分隔并自动去除空白，避免环境变量中夹带空格导致源匹配失败。
        String[] normalizedAllowedOrigins = StrUtil.splitTrim(allowedOrigins, ',').toArray(new String[0]);
        // 步骤 2：注册全局接口跨域规则，允许携带登录态 Cookie 或鉴权头。
        registry.addMapping("/**")
            .allowedOrigins(normalizedAllowedOrigins)
            // 步骤 3：会话重命名走 PATCH，必须显式放行，否则预检请求会被 Spring 拒绝为 Invalid CORS request。
            .allowedMethods("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true);
    }
}
