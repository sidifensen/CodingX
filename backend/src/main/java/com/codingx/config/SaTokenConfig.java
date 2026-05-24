package com.codingx.config;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 负责配置 SaTokenConfig 所需的 Spring Bean 与基础设施。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 执行 addInterceptors 定义的处理逻辑。
     * @param registry 输入参数。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            if (!"OPTIONS".equalsIgnoreCase(SaHolder.getRequest().getMethod())) {
                StpUtil.checkLogin();
            }
        })).addPathPatterns("/**")
            .excludePathPatterns(
                "/api/auth/login",
                "/api/chat/stream",
                "/api/chat/conversations/*/stream",
                "/api/chat/conversations/shared/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/v3/api-docs/**",
                "/actuator/**",
                "/error"
            );
    }
}
