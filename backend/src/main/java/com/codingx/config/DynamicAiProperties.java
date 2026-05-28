package com.codingx.config;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.service.RuntimeSettingService;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 基于系统配置表组装 AI 运行时配置视图，逐步替代 `application.yml` 中的业务级 AI 默认值。
 */
@Component
@RequiredArgsConstructor
public class DynamicAiProperties {

    private final AiProperties aiProperties;
    private final RuntimeSettingService runtimeSettingService;

    /**
     * 获取 AI 连接超时毫秒。
     * @return 连接超时毫秒。
     */
    public int connectTimeoutMs() {
        return runtimeSettingService.getInt("ai.connect_timeout_ms", aiProperties.getConnectTimeoutMs());
    }

    /**
     * 获取 AI 读取超时毫秒。
     * @return 读取超时毫秒。
     */
    public int readTimeoutMs() {
        return runtimeSettingService.getInt("ai.read_timeout_ms", aiProperties.getReadTimeoutMs());
    }

    /**
     * 获取当前默认 provider 编码。
     * @return provider 编码。
     */
    public String provider() {
        return runtimeSettingService.getString("ai.provider", aiProperties.getProvider());
    }

    /**
     * 获取旧式默认 provider 的基础地址。
     * @return 基础地址。
     */
    public String baseUrl() {
        return runtimeSettingService.getString("ai.base_url", aiProperties.getBaseUrl());
    }

    /**
     * 获取旧式默认 provider 的 API Key。
     * @return API Key。
     */
    public String apiKey() {
        return runtimeSettingService.getString("ai.api_key", aiProperties.getApiKey());
    }

    /**
     * 获取旧式聊天模型名。
     * @return 聊天模型名。
     */
    public String chatModel() {
        return runtimeSettingService.getString("ai.chat_model", aiProperties.getChatModel());
    }

    /**
     * 获取系统提示词。
     * @return 系统提示词。
     */
    public String systemPrompt() {
        return runtimeSettingService.getString("ai.system_prompt", aiProperties.getSystemPrompt());
    }

    /**
     * 合并 provider 配置，并允许系统配置覆盖基础地址与密钥。
     * @return provider 映射。
     */
    public Map<String, AiProperties.Provider> providers() {
        Map<String, AiProperties.Provider> providers = new HashMap<>();
        if (aiProperties.getProviders() != null) {
            aiProperties.getProviders().forEach((providerCode, provider) -> providers.put(providerCode, cloneProvider(provider)));
        }
        mergeProvider(providers, "siliconflow");
        mergeProvider(providers, "bailian");
        mergeProvider(providers, "deepseek");
        mergeProvider(providers, "stub");
        return providers;
    }

    private void mergeProvider(Map<String, AiProperties.Provider> providers, String providerCode) {
        AiProperties.Provider provider = providers.getOrDefault(providerCode, new AiProperties.Provider());
        provider.setBaseUrl(runtimeSettingService.getString(
            "ai.providers." + providerCode + ".base_url",
            provider.getBaseUrl()
        ));
        provider.setApiKey(runtimeSettingService.getString(
            "ai.providers." + providerCode + ".api_key",
            provider.getApiKey()
        ));
        providers.put(providerCode, provider);
    }

    private AiProperties.Provider cloneProvider(AiProperties.Provider source) {
        AiProperties.Provider provider = new AiProperties.Provider();
        if (source == null) {
            return provider;
        }
        provider.setBaseUrl(source.getBaseUrl());
        provider.setApiKey(source.getApiKey());
        provider.setEndpoints(source.getEndpoints() == null ? new HashMap<>() : new HashMap<>(source.getEndpoints()));
        if (StrUtil.isBlank(provider.getBaseUrl())) {
            provider.setBaseUrl(null);
        }
        return provider;
    }
}
