package com.codingx.chat.application.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatRuntimeSetting;
import com.codingx.chat.domain.repository.ChatRuntimeSettingRepository;
import com.codingx.chat.application.service.RuntimeSettingService;
import com.codingx.config.AiProperties;
import com.codingx.config.ChatExecutorRuntimeProperties;
import com.codingx.config.ChatMemoryProperties;
import com.codingx.config.RuntimeProperties;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证系统配置表中的本地工具调用轮次上限读取逻辑。
 */
@ExtendWith(MockitoExtension.class)
class RuntimeSettingServiceTest {

    @Mock
    private ChatRuntimeSettingRepository chatRuntimeSettingRepository;

    @Mock
    private RuntimeProperties runtimeProperties;

    @Mock
    private ChatExecutorRuntimeProperties chatExecutorRuntimeProperties;

    @Mock
    private ChatMemoryProperties chatMemoryProperties;

    @Mock
    private AiProperties aiProperties;

    @Mock
    private ConfigCryptoService configCryptoService;

    @InjectMocks
    private RuntimeSettingService runtimeSettingService;

    /**
     * 系统配置存在时，应优先读取数据库中的显式配置值。
     */
    @Test
    void chatToolMaxRoundsReadsConfiguredValue() {
        when(chatRuntimeSettingRepository.findAll()).thenReturn(List.of(
            ChatRuntimeSetting.builder()
                .id(1L)
                .settingKey("chat.tool.max_rounds")
                .settingValue("10")
                .valueType("INTEGER")
                .categoryCode("chat.tool")
                .description("本地工具调用最大轮次")
                .sortNo(10)
                .restartRequired(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(0)
                .build()
        ));

        runtimeSettingService.init();

        assertEquals(10, runtimeSettingService.chatToolMaxRounds());
    }

    /**
     * 工具轮次配置必须保留代码硬上限，避免管理端或脚本误配成超大值后让模型工具循环长期占用线程。
     */
    @Test
    void chatToolMaxRoundsClampsMisconfiguredLargeValue() {
        when(chatRuntimeSettingRepository.findAll()).thenReturn(List.of(
            ChatRuntimeSetting.builder()
                .id(1L)
                .settingKey("chat.tool.max_rounds")
                .settingValue("10000")
                .valueType("INTEGER")
                .categoryCode("chat.tool")
                .description("本地工具调用最大轮次")
                .sortNo(10)
                .restartRequired(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(0)
                .build()
        ));

        runtimeSettingService.init();

        assertEquals(20, runtimeSettingService.chatToolMaxRounds());
    }

    /**
     * 系统配置缺失时，应回退到代码内置默认值 10。
     */
    @Test
    void chatToolMaxRoundsFallsBackToDefaultWhenMissing() {
        when(chatRuntimeSettingRepository.findAll()).thenReturn(List.of());

        runtimeSettingService.init();

        assertEquals(10, runtimeSettingService.chatToolMaxRounds());
    }

    /**
     * 歧义引导参数由系统配置表统一控制，便于管理端按运行策略调整。
     */
    @Test
    void chatIntentGuidanceReadsConfiguredValues() {
        when(chatRuntimeSettingRepository.findAll()).thenReturn(List.of(
            ChatRuntimeSetting.builder()
                .settingKey("chat.intent.guidance.enabled")
                .settingValue("false")
                .valueType("BOOLEAN")
                .categoryCode("chat.intent.guidance")
                .description("是否启用聊天歧义引导")
                .build(),
            ChatRuntimeSetting.builder()
                .settingKey("chat.intent.guidance.ambiguity_score_ratio")
                .settingValue("0.72")
                .valueType("DECIMAL")
                .categoryCode("chat.intent.guidance")
                .description("歧义引导分数比值阈值")
                .build(),
            ChatRuntimeSetting.builder()
                .settingKey("chat.intent.guidance.ambiguity_margin")
                .settingValue("0.08")
                .valueType("DECIMAL")
                .categoryCode("chat.intent.guidance")
                .description("歧义引导边界缓冲宽度")
                .build(),
            ChatRuntimeSetting.builder()
                .settingKey("chat.intent.guidance.max_options")
                .settingValue("3")
                .valueType("INTEGER")
                .categoryCode("chat.intent.guidance")
                .description("歧义引导最大候选数量")
                .build()
        ));

        runtimeSettingService.init();

        assertEquals(false, runtimeSettingService.chatIntentGuidanceEnabled());
        assertEquals(0.72D, runtimeSettingService.chatIntentGuidanceAmbiguityScoreRatio());
        assertEquals(0.08D, runtimeSettingService.chatIntentGuidanceAmbiguityMargin());
        assertEquals(3, runtimeSettingService.chatIntentGuidanceMaxOptions());
    }

    /**
     * 缺省值保持与 ragent 的 guidance 配置一致，避免存量环境升级后必须手工补配置。
     */
    @Test
    void chatIntentGuidanceFallsBackToRagentDefaultsWhenMissing() {
        when(chatRuntimeSettingRepository.findAll()).thenReturn(List.of());

        runtimeSettingService.init();

        assertEquals(true, runtimeSettingService.chatIntentGuidanceEnabled());
        assertEquals(0.8D, runtimeSettingService.chatIntentGuidanceAmbiguityScoreRatio());
        assertEquals(0.15D, runtimeSettingService.chatIntentGuidanceAmbiguityMargin());
        assertEquals(6, runtimeSettingService.chatIntentGuidanceMaxOptions());
    }

    /**
     * 敏感配置应从密文字段解密读取，而不是继续依赖明文值。
     */
    @Test
    void webSearchApiKeyDecryptsSecretValue() {
        when(chatRuntimeSettingRepository.findAll()).thenReturn(List.of(
            ChatRuntimeSetting.builder()
                .settingKey("web_search.api_key")
                .settingValue("")
                .encryptedValue("cipher-secret")
                .secret(true)
                .valueType("STRING")
                .categoryCode("search")
                .description("联网搜索接口密钥")
                .build()
        ));
        when(configCryptoService.decrypt("cipher-secret")).thenReturn("plain-secret");

        runtimeSettingService.init();

        assertEquals("plain-secret", runtimeSettingService.webSearchApiKey());
    }

    /**
     * 敏感配置缺少密文时必须立即失败，避免业务静默读取空字符串。
     */
    @Test
    void webSearchApiKeyThrowsWhenSecretMissingCipherText() {
        when(chatRuntimeSettingRepository.findAll()).thenReturn(List.of(
            ChatRuntimeSetting.builder()
                .settingKey("web_search.api_key")
                .settingValue("")
                .secret(true)
                .valueType("STRING")
                .categoryCode("search")
                .description("联网搜索接口密钥")
                .build()
        ));

        runtimeSettingService.init();

        assertThrows(IllegalStateException.class, () -> runtimeSettingService.webSearchApiKey());
    }

    /**
     * 未配置 provider_order 时应使用内置默认顺序，确保新环境升级后天然具备多 provider 兜底能力。
     */
    @Test
    void webSearchProviderOrderFallsBackToDomesticDefault() {
        when(chatRuntimeSettingRepository.findAll()).thenReturn(List.of());

        runtimeSettingService.init();

        assertEquals(
            List.of("tavily", "serpapi", "exa", "duckduckgo_html", "bing_html"),
            runtimeSettingService.webSearchProviderOrder()
        );
    }

    /**
     * provider_order 允许按运维策略自由调整，读取时需清理空白项并保持顺序。
     */
    @Test
    void webSearchProviderOrderReadsConfiguredValue() {
        when(chatRuntimeSettingRepository.findAll()).thenReturn(List.of(
            setting("web_search.provider_order", "exa, tavily, ,duckduckgo_html")
        ));

        runtimeSettingService.init();

        assertEquals(List.of("exa", "tavily", "duckduckgo_html"), runtimeSettingService.webSearchProviderOrder());
    }

    /**
     * provider 级地址与密钥应优先读取 web_search.providers.<provider>.*，密钥读取必须走解密链路。
     */
    @Test
    void webSearchProviderConfigReadsProviderScopedValues() {
        when(chatRuntimeSettingRepository.findAll()).thenReturn(List.of(
            setting("web_search.providers.tavily.base_url", "https://api.tavily.test/search"),
            ChatRuntimeSetting.builder()
                .settingKey("web_search.providers.tavily.api_key")
                .settingValue("")
                .encryptedValue("cipher-tavily-key")
                .secret(true)
                .valueType("STRING")
                .categoryCode("search.providers")
                .description("Tavily 搜索接口密钥")
                .build()
        ));
        when(configCryptoService.decrypt("cipher-tavily-key")).thenReturn("plain-tavily-key");

        runtimeSettingService.init();

        assertEquals("https://api.tavily.test/search", runtimeSettingService.webSearchProviderBaseUrl("tavily"));
        assertEquals("plain-tavily-key", runtimeSettingService.webSearchProviderApiKey("tavily"));
    }

    /**
     * 旧版单 provider 配置仍需兼容：provider 名匹配旧 web_search.provider 时回退到旧 base_url/api_key。
     */
    @Test
    void webSearchProviderConfigFallsBackToLegacySingleProvider() {
        when(chatRuntimeSettingRepository.findAll()).thenReturn(List.of(
            setting("web_search.provider", "serpapi"),
            setting("web_search.base_url", "https://legacy.example/search"),
            ChatRuntimeSetting.builder()
                .settingKey("web_search.api_key")
                .settingValue("")
                .encryptedValue("cipher-legacy-key")
                .secret(true)
                .valueType("STRING")
                .categoryCode("search")
                .description("联网搜索接口密钥")
                .build()
        ));
        when(configCryptoService.decrypt("cipher-legacy-key")).thenReturn("plain-legacy-key");

        runtimeSettingService.init();

        assertEquals("https://legacy.example/search", runtimeSettingService.webSearchProviderBaseUrl("serpapi"));
        assertEquals("plain-legacy-key", runtimeSettingService.webSearchProviderApiKey("serpapi"));
        assertEquals("", runtimeSettingService.webSearchProviderBaseUrl("tavily"));
        assertEquals("", runtimeSettingService.webSearchProviderApiKey("tavily"));
    }

    /**
     * 联网搜索熔断参数由系统配置表读取，缺失时使用代码默认值。
     */
    @Test
    void webSearchCircuitBreakerReadsConfiguredValues() {
        when(chatRuntimeSettingRepository.findAll()).thenReturn(List.of(
            setting("web_search.failure_threshold", "4"),
            setting("web_search.open_duration_ms", "45000")
        ));

        runtimeSettingService.init();

        assertEquals(4, runtimeSettingService.webSearchFailureThreshold());
        assertEquals(45_000L, runtimeSettingService.webSearchOpenDurationMs());
    }

    /**
     * AI 候选池和 provider endpoint 依赖按前缀聚合读取，系统配置服务必须返回同前缀下的完整键值映射。
     */
    @Test
    void getByPrefixReturnsMatchingSettings() {
        when(chatRuntimeSettingRepository.findAll()).thenReturn(List.of(
            ChatRuntimeSetting.builder()
                .settingKey("ai.chat.candidates.10.id")
                .settingValue("siliconflow-deepseek-v4-flash")
                .valueType("STRING")
                .categoryCode("ai.candidates")
                .description("候选模型 ID")
                .build(),
            ChatRuntimeSetting.builder()
                .settingKey("ai.chat.candidates.10.provider")
                .settingValue("siliconflow")
                .valueType("STRING")
                .categoryCode("ai.candidates")
                .description("候选模型 provider")
                .build(),
            ChatRuntimeSetting.builder()
                .settingKey("search.top_k")
                .settingValue("5")
                .valueType("INTEGER")
                .categoryCode("search")
                .description("搜索召回数量")
                .build()
        ));

        runtimeSettingService.init();

        Map<String, String> prefixedValues = runtimeSettingService.getByPrefix("ai.chat.candidates.");

        assertEquals(2, prefixedValues.size());
        assertEquals("siliconflow-deepseek-v4-flash", prefixedValues.get("ai.chat.candidates.10.id"));
        assertEquals("siliconflow", prefixedValues.get("ai.chat.candidates.10.provider"));
    }

    /**
     * 前缀聚合读取命中敏感配置时也必须先解密，避免动态 provider 配置拿到空值。
     */
    @Test
    void getByPrefixDecryptsSecretValues() {
        when(chatRuntimeSettingRepository.findAll()).thenReturn(List.of(
            ChatRuntimeSetting.builder()
                .settingKey("ai.providers.siliconflow.api_key")
                .settingValue("")
                .encryptedValue("cipher-provider-key")
                .secret(true)
                .valueType("STRING")
                .categoryCode("ai.providers")
                .description("硅基流动接口密钥")
                .build()
        ));
        when(configCryptoService.decrypt("cipher-provider-key")).thenReturn("plain-provider-key");

        runtimeSettingService.init();

        Map<String, String> prefixedValues = runtimeSettingService.getByPrefix("ai.providers.siliconflow.");

        assertTrue(prefixedValues.containsKey("ai.providers.siliconflow.api_key"));
        assertEquals("plain-provider-key", prefixedValues.get("ai.providers.siliconflow.api_key"));
    }

    /**
     * 构造普通字符串配置，减少测试样板并保持每个用例只关注业务差异。
     * @param key 配置键。
     * @param value 配置值。
     * @return 运行时配置记录。
     */
    private ChatRuntimeSetting setting(String key, String value) {
        return ChatRuntimeSetting.builder()
            .settingKey(key)
            .settingValue(value)
            .secret(false)
            .valueType("STRING")
            .categoryCode("search")
            .description("测试配置")
            .build();
    }
}
