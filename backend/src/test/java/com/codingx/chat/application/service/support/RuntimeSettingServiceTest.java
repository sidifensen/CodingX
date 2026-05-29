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
}
