package com.codingx.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private String provider = "deepseek";
    private String baseUrl = "https://api.deepseek.com/v1";
    private String apiKey = "";
    private String chatModel = "deepseek-chat";
    private int connectTimeoutMs = 10_000;
    private int readTimeoutMs = 120_000;
    private String systemPrompt = "You are the CodingX assistant. Be concise, practical, and safe.";
}