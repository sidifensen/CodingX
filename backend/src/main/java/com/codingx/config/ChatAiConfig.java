package com.codingx.config;

import com.codingx.support.ai.AiModelDispatchService;
import com.codingx.support.ai.AiProviderClient;
import com.codingx.support.ai.OpenAiStyleStreamParser;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 负责装配聊天模型路由层所需的 AI 基础设施 Bean。
 */
@Configuration
public class ChatAiConfig {

    /**
     * 根据所有可用 provider 构造统一模型路由服务。
     * @param providerClients AI provider 列表。
     * @return 模型路由服务。
     */
    @Bean
    public AiModelDispatchService aiModelDispatchService(List<AiProviderClient> providerClients) {
        return new AiModelDispatchService(providerClients);
    }

    /**
     * 装配 OpenAI 风格流解析器，供 provider 统一复用。
     * @return 流解析器。
     */
    @Bean
    public OpenAiStyleStreamParser openAiStyleStreamParser() {
        return new OpenAiStyleStreamParser();
    }

    /**
     * 提供一个本地 mock 搜索 provider，确保搜索型链路在默认环境可运行。
     * @return mock 搜索 provider。
     */
    @Bean
    public com.codingx.chat.application.service.WebSearchExecutionService.WebSearchProvider mockWebSearchProvider() {
        return question -> {
            if (question != null && question.contains("Spring Boot SSE")) {
                return List.of(new com.codingx.chat.application.service.SearchReferenceCandidate(
                    "Spring Boot SSE 最佳实践",
                    "https://docs.spring.io",
                    "Spring",
                    "SSE 最佳实践摘要"
                ));
            }
            return List.of();
        };
    }
}
