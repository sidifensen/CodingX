package com.codingx.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.service.RuntimeSettingService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证 AI 动态配置视图能从系统配置表聚合 provider endpoint 与候选池。
 */
@ExtendWith(MockitoExtension.class)
class DynamicAiPropertiesTest {

    @Mock
    private RuntimeSettingService runtimeSettingService;

    /**
     * provider endpoint 允许由系统配置表覆盖，避免 chat 路径继续写死在 application.yml。
     */
    @Test
    void providersMergeDynamicChatEndpoints() {
        AiProperties aiProperties = buildAiProperties();
        when(runtimeSettingService.getString("ai.providers.siliconflow.base_url", "https://api.siliconflow.cn"))
            .thenReturn("https://api.siliconflow.cn");
        when(runtimeSettingService.getString("ai.providers.siliconflow.api_key", "test-silicon-key"))
            .thenReturn("test-silicon-key");
        when(runtimeSettingService.getByPrefix("ai.providers.siliconflow.endpoints."))
            .thenReturn(Map.of("ai.providers.siliconflow.endpoints.chat", "/custom/chat/completions"));

        DynamicAiProperties dynamicAiProperties = new DynamicAiProperties(aiProperties, runtimeSettingService);
        Map<String, AiProperties.Provider> providers = dynamicAiProperties.providers();

        assertEquals("/custom/chat/completions", providers.get("siliconflow").getEndpoints().get("chat"));
    }

    /**
     * AI 候选池应可由扁平系统配置键聚合而成，而不是只能依赖 application.yml 静态列表。
     */
    @Test
    void chatCandidatesBuildFromFlatSystemSettings() {
        AiProperties aiProperties = buildAiProperties();
        when(runtimeSettingService.getByPrefix("ai.chat.candidates."))
            .thenReturn(Map.ofEntries(
                Map.entry("ai.chat.candidates.10.id", "siliconflow-deepseek-v4-flash"),
                Map.entry("ai.chat.candidates.10.provider", "siliconflow"),
                Map.entry("ai.chat.candidates.10.model", "deepseek-ai/DeepSeek-V4-Flash"),
                Map.entry("ai.chat.candidates.10.priority", "1"),
                Map.entry("ai.chat.candidates.10.enabled", "true"),
                Map.entry("ai.chat.candidates.10.supports_thinking", "false"),
                Map.entry("ai.chat.candidates.10.supports_vision", "false"),
                Map.entry("ai.chat.candidates.30.id", "siliconflow-qwen3.5-122b-a10b"),
                Map.entry("ai.chat.candidates.30.provider", "siliconflow"),
                Map.entry("ai.chat.candidates.30.model", "Qwen/Qwen3.5-122B-A10B"),
                Map.entry("ai.chat.candidates.30.priority", "3"),
                Map.entry("ai.chat.candidates.30.enabled", "true"),
                Map.entry("ai.chat.candidates.30.supports_thinking", "true"),
                Map.entry("ai.chat.candidates.30.supports_vision", "true")
            ));

        DynamicAiProperties dynamicAiProperties = new DynamicAiProperties(aiProperties, runtimeSettingService);
        List<AiProperties.ChatCandidate> candidates = dynamicAiProperties.chatCandidates();

        assertEquals(2, candidates.size());
        assertEquals("siliconflow-deepseek-v4-flash", candidates.getFirst().getId());
        assertFalse(Boolean.TRUE.equals(candidates.getFirst().getSupportsVision()));
        assertEquals("siliconflow-qwen3.5-122b-a10b", candidates.get(1).getId());
        assertTrue(Boolean.TRUE.equals(candidates.get(1).getSupportsVision()));
    }

    private AiProperties buildAiProperties() {
        AiProperties properties = new AiProperties();
        HashMap<String, AiProperties.Provider> providers = new HashMap<>();
        AiProperties.Provider siliconflow = new AiProperties.Provider();
        siliconflow.setBaseUrl("https://api.siliconflow.cn");
        siliconflow.setApiKey("test-silicon-key");
        siliconflow.setEndpoints(new HashMap<>(Map.of("chat", "/v1/chat/completions")));
        providers.put("siliconflow", siliconflow);
        properties.setProviders(providers);
        properties.setChat(new AiProperties.ChatModelGroup());
        return properties;
    }
}
