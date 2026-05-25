package com.codingx.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * web_access 工具调用本机 CDP Proxy 的运行时配置。
 */
@Data
@ConfigurationProperties(prefix = "app.web-access")
public class WebAccessProperties {

    /**
     * CDP Proxy 基础地址。
     */
    private String baseUrl = "http://127.0.0.1:3456";

    /**
     * 单次 HTTP 请求超时时间，单位毫秒。
     */
    private long requestTimeoutMs = 15000L;

    /**
     * 连接超时时间，单位毫秒。
     */
    private long connectTimeoutMs = 5000L;

    /**
     * 读取超时时间，单位毫秒。
     */
    private long readTimeoutMs = 15000L;
}
