package com.codingx.config;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.service.RuntimeSettingService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
     * 兼容旧式 DeepSeek provider 回退路径，缺少明确模型目标时仍可读取基础地址。
     * @return 默认基础地址。
     */
    public String baseUrl() {
        return aiProperties.getBaseUrl();
    }

    /**
     * 兼容旧式 DeepSeek provider 回退路径，缺少明确模型目标时仍可读取 API Key。
     * @return 默认 API Key。
     */
    public String apiKey() {
        return aiProperties.getApiKey();
    }

    /**
     * 兼容旧式 DeepSeek provider 回退路径，缺少明确模型目标时仍可读取模型名。
     * @return 默认模型名。
     */
    public String chatModel() {
        return aiProperties.getChatModel();
    }

    /**
     * 获取普通聊天首选候选 ID；该值只作为候选池排序指针，不替代候选池本身。
     * @return 普通聊天首选候选 ID。
     */
    public String defaultChatModel() {
        String fallback = aiProperties.getChat() == null ? null : aiProperties.getChat().getDefaultModel();
        return runtimeSettingService.getString("ai.chat.default_model", fallback);
    }

    /**
     * 获取深度思考首选候选 ID；该值必须指向候选池中的 supports_thinking 候选。
     * @return 深度思考首选候选 ID。
     */
    public String deepThinkingChatModel() {
        String fallback = aiProperties.getChat() == null ? null : aiProperties.getChat().getDeepThinkingModel();
        return runtimeSettingService.getString("ai.chat.deep_thinking_model", fallback);
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
        Map<String, String> endpointOverrides = runtimeSettingService.getByPrefix("ai.providers." + providerCode + ".endpoints.");
        if (!endpointOverrides.isEmpty()) {
            Map<String, String> endpoints = provider.getEndpoints() == null ? new HashMap<>() : new HashMap<>(provider.getEndpoints());
            endpointOverrides.forEach((key, value) -> {
                String endpointName = StrUtil.removePrefix(key, "ai.providers." + providerCode + ".endpoints.");
                if (StrUtil.isNotBlank(endpointName) && StrUtil.isNotBlank(value)) {
                    endpoints.put(endpointName, value.trim());
                }
            });
            provider.setEndpoints(endpoints);
        }
        providers.put(providerCode, provider);
    }

    /**
     * 从系统配置表中聚合聊天候选池；未配置时回退到静态骨架。
     * @return 聊天候选列表。
     */
    public List<AiProperties.ChatCandidate> chatCandidates() {
        Map<String, String> candidateSettings = runtimeSettingService.getByPrefix("ai.chat.candidates.");
        if (candidateSettings.isEmpty()) {
            return aiProperties.getChat() == null ? List.of() : aiProperties.getChat().getCandidates();
        }
        Map<Integer, Map<String, String>> slotSettings = new TreeMap<>();
        candidateSettings.forEach((key, value) -> {
            String remainder = StrUtil.removePrefix(key, "ai.chat.candidates.");
            String[] fragments = remainder.split("\\.", 2);
            if (fragments.length != 2 || !StrUtil.isNumeric(fragments[0])) {
                return;
            }
            Integer slot = Integer.valueOf(fragments[0]);
            slotSettings.computeIfAbsent(slot, ignored -> new LinkedHashMap<>()).put(fragments[1], value);
        });
        List<AiProperties.ChatCandidate> candidates = new ArrayList<>();
        slotSettings.values().forEach(fields -> {
            AiProperties.ChatCandidate candidate = toCandidate(fields);
            if (candidate != null) {
                candidates.add(candidate);
            }
        });
        return candidates;
    }

    private AiProperties.ChatCandidate toCandidate(Map<String, String> fields) {
        String id = trim(fields.get("id"));
        String provider = trim(fields.get("provider"));
        String model = trim(fields.get("model"));
        if (StrUtil.hasBlank(id, provider, model)) {
            return null;
        }
        AiProperties.ChatCandidate candidate = new AiProperties.ChatCandidate();
        candidate.setId(id);
        candidate.setProvider(provider);
        candidate.setModel(model);
        candidate.setPriority(parseInteger(fields.get("priority"), 100));
        candidate.setEnabled(parseBoolean(fields.get("enabled"), true));
        candidate.setSupportsThinking(parseBoolean(fields.get("supports_thinking"), false));
        candidate.setSupportsVision(parseBoolean(fields.get("supports_vision"), false));
        return candidate;
    }

    private String trim(String value) {
        return StrUtil.trimToNull(value);
    }

    private Integer parseInteger(String value, int fallback) {
        if (StrUtil.isBlank(value) || !StrUtil.isNumeric(value.trim())) {
            return fallback;
        }
        return Integer.parseInt(value.trim());
    }

    private Boolean parseBoolean(String value, boolean fallback) {
        if (StrUtil.isBlank(value)) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase();
        if ("true".equals(normalized) || "1".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized)) {
            return false;
        }
        return fallback;
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
