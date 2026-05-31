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

    /**
     * 静态 AI 配置，用作系统配置表缺失时的默认值来源。
     */
    private final AiProperties aiProperties;

    /**
     * 运行时配置服务，用于读取数据库中的 AI provider、候选模型和超时覆盖值。
     */
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
        // 步骤 1：先复制静态 provider 配置，避免直接修改 ConfigurationProperties 原对象。
        Map<String, AiProperties.Provider> providers = new HashMap<>();
        if (aiProperties.getProviders() != null) {
            aiProperties.getProviders().forEach((providerCode, provider) -> providers.put(providerCode, cloneProvider(provider)));
        }
        // 步骤 2：按当前支持的 provider 编码合并系统配置表中的 baseUrl、apiKey 和 endpoint 覆盖。
        mergeProvider(providers, "siliconflow");
        mergeProvider(providers, "bailian");
        mergeProvider(providers, "deepseek");
        mergeProvider(providers, "stub");
        return providers;
    }

    private void mergeProvider(Map<String, AiProperties.Provider> providers, String providerCode) {
        // 步骤 1：provider 不存在时创建空配置，允许只通过系统配置表动态新增 provider 连接信息。
        AiProperties.Provider provider = providers.getOrDefault(providerCode, new AiProperties.Provider());
        // 步骤 2：基础地址和 API Key 由系统配置覆盖，空配置时保留静态默认值。
        provider.setBaseUrl(runtimeSettingService.getString(
            "ai.providers." + providerCode + ".base_url",
            provider.getBaseUrl()
        ));
        provider.setApiKey(runtimeSettingService.getString(
            "ai.providers." + providerCode + ".api_key",
            provider.getApiKey()
        ));
        // 步骤 3：endpoint 覆盖使用前缀批量读取，只写入 endpoint 名称和值都非空的配置项。
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
        // 步骤 4：合并后的 provider 放回映射，供模型选择器按候选 provider 读取。
        providers.put(providerCode, provider);
    }

    /**
     * 从系统配置表中聚合聊天候选池；未配置时回退到静态骨架。
     * @return 聊天候选列表。
     */
    public List<AiProperties.ChatCandidate> chatCandidates() {
        // 步骤 1：没有动态候选配置时回退静态候选池，兼容未迁移环境。
        Map<String, String> candidateSettings = runtimeSettingService.getByPrefix("ai.chat.candidates.");
        if (candidateSettings.isEmpty()) {
            return aiProperties.getChat() == null ? List.of() : aiProperties.getChat().getCandidates();
        }
        // 步骤 2：动态候选按 slot 编号聚合字段，确保候选顺序稳定。
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
        // 步骤 3：逐个 slot 解析候选，字段不完整的候选直接跳过。
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
        // 步骤 1：候选必须包含 id、provider 和 model，缺任一字段都不能参与路由。
        String id = trim(fields.get("id"));
        String provider = trim(fields.get("provider"));
        String model = trim(fields.get("model"));
        if (StrUtil.hasBlank(id, provider, model)) {
            return null;
        }
        // 步骤 2：优先级、启用状态、思考和视觉能力都有默认值，避免配置缺失导致候选不可用。
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
        // 步骤 1：空值或非数字值回退默认值，避免运行时配置错误导致应用启动失败。
        if (StrUtil.isBlank(value) || !StrUtil.isNumeric(value.trim())) {
            return fallback;
        }
        return Integer.parseInt(value.trim());
    }

    private Boolean parseBoolean(String value, boolean fallback) {
        // 步骤 1：兼容 true/false 和 1/0 两种配置写法。
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
        // 步骤 1：复制 provider 基础字段和 endpoints，避免动态覆盖污染静态配置对象。
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
