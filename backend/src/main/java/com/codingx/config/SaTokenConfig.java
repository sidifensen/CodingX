package com.codingx.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 鉴权拦截器配置，集中定义登录校验入口与公开接口白名单。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 注册登录态校验拦截器。
     * @param registry Spring MVC 拦截器注册器。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 步骤 1：OPTIONS 预检请求不做登录校验，否则浏览器跨域请求会在业务接口前被拦截。
        registry.addInterceptor(new SaInterceptor(handle -> {
            if (!"OPTIONS".equalsIgnoreCase(SaHolder.getRequest().getMethod())) {
                // 步骤 2：非预检请求统一由 Sa-Token 校验登录态，Controller 不重复写登录检查。
                StpUtil.checkLogin();
            }
        })).addPathPatterns("/**")
            // 步骤 3：排除登录、公开流、分享、接口文档和健康检查等不需要登录的入口。
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
