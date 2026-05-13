package com.codingx.backend.config;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures the Spring beans and infrastructure required by SaTokenConfig.
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * Executes the logic defined by addInterceptors.
     * @param registry input argument.
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
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/v3/api-docs/**",
                "/actuator/**",
                "/error"
            );
    }
}
