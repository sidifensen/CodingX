package com.codingx.config;

import com.codingx.common.support.ai.AiModelDispatchService;
import com.codingx.common.support.ai.AiProviderClient;
import com.codingx.common.support.ai.OpenAiStyleStreamParser;
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
     * @param aiProperties AI 配置。
     * @param dynamicAiRoutingProperties 模型路由动态配置。
     * @return 模型路由服务。
     */
    @Bean
    public AiModelDispatchService aiModelDispatchService(
        List<AiProviderClient> providerClients,
        AiProperties aiProperties,
        DynamicAiRoutingProperties dynamicAiRoutingProperties
    ) {
        return new AiModelDispatchService(providerClients, aiProperties, dynamicAiRoutingProperties);
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
     * 默认去重后处理器，避免相同 URL 来源重复展示。
     * @return 去重后处理器。
     */
    @Bean
    public com.codingx.chat.application.service.SearchResultPostProcessor defaultSearchDeduplicationPostProcessor() {
        return (context, candidates) -> candidates.stream()
            .collect(java.util.stream.Collectors.toMap(
                candidate -> candidate.url() == null ? candidate.title() : candidate.url(),
                candidate -> candidate,
                (left, right) -> left.score() >= right.score() ? left : right,
                java.util.LinkedHashMap::new
            ))
            .values()
            .stream()
            .toList();
    }

}

