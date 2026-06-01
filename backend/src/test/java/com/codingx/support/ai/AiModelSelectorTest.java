package com.codingx.common.support.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.config.AiProperties;
import com.codingx.config.DynamicAiProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 验证模型选择器会按首选模型、思考能力和兼容配置规则生成候选列表。
 */
class AiModelSelectorTest {

    /**
     * 指定 preferredModel 时，应将其排到候选首位。
     */
    @Test
    void selectChatCandidatesPrefersRequestedModel() {
        AiModelSelector selector = new AiModelSelector(buildProperties(
            candidate("deepseek-chat", "deepseek", "deepseek-chat", 10, false),
            candidate("stub-chat", "stub", "stub-chat", 1, false)
        ));

        List<AiModelTarget> targets = selector.selectChatCandidates("deepseek-chat", false);

        assertEquals("deepseek-chat", targets.getFirst().id());
    }

    /**
     * 未显式指定 preferredModel 时，应只按候选池优先级排序，历史默认模型指针不能抢占首位。
     */
    @Test
    void selectChatCandidatesUsesPriorityWhenRequestHasNoPreference() {
        AiProperties properties = buildProperties(
            candidate("priority-first", "stub", "stub-chat", 1, false),
            candidate("historical-default", "deepseek", "deepseek-chat", 10, false)
        );
        properties.getChat().setDefaultModel("historical-default");
        AiModelSelector selector = new AiModelSelector(properties);

        List<AiModelTarget> targets = selector.selectChatCandidates(null, false);

        assertEquals("priority-first", targets.getFirst().id());
    }

    /**
     * 普通请求未开启深度思考时，即使 thinking 候选优先级更高，也应先尝试非 thinking 候选。
     */
    @Test
    void selectChatCandidatesPrefersNonThinkingCandidatesWhenThinkingDisabled() {
        AiModelSelector selector = new AiModelSelector(buildProperties(
            candidate("thinking-priority-first", "deepseek", "deepseek-thinking", 1, true),
            candidate("normal-priority-second", "stub", "stub-chat", 5, false),
            candidate("normal-priority-third", "bailian", "qwen-plus", 6, false)
        ));

        List<AiModelTarget> targets = selector.selectChatCandidates(null, false);

        assertEquals(
            List.of("normal-priority-second", "normal-priority-third", "thinking-priority-first"),
            targets.stream().map(AiModelTarget::id).toList()
        );
    }

    /**
     * 深度思考模式未显式指定模型时，应在 thinking 候选内按优先级排序，历史深度思考指针不能抢占首位。
     */
    @Test
    void selectChatCandidatesUsesThinkingPriorityWhenRequestHasNoPreference() {
        AiProperties properties = buildProperties(
            candidate("thinking-priority-one", "deepseek", "deepseek-thinking-a", 1, true),
            candidate("thinking-configured", "deepseek", "deepseek-thinking-b", 9, true)
        );
        properties.getChat().setDeepThinkingModel("thinking-configured");
        AiModelSelector selector = new AiModelSelector(properties);

        List<AiModelTarget> targets = selector.selectChatCandidates(null, true);

        assertEquals("thinking-priority-one", targets.getFirst().id());
    }

    /**
     * 深度思考模式下应优先筛选支持 thinking 的候选。
     */
    @Test
    void selectChatCandidatesFiltersThinkingCapableTargets() {
        AiModelSelector selector = new AiModelSelector(buildProperties(
            candidate("deepseek-chat", "deepseek", "deepseek-chat", 1, false),
            candidate("deepseek-thinking", "deepseek", "deepseek-thinking", 2, true)
        ));

        List<AiModelTarget> targets = selector.selectChatCandidates(null, true);

        assertEquals(List.of("deepseek-thinking"), targets.stream().map(AiModelTarget::id).toList());
    }

    /**
     * 如果深度思考候选为空，应安全回退到普通候选列表，而不是返回空集合。
     */
    @Test
    void selectChatCandidatesFallsBackToNormalCandidatesWhenNoThinkingTargetExists() {
        AiModelSelector selector = new AiModelSelector(buildProperties(
            candidate("deepseek-chat", "deepseek", "deepseek-chat", 1, false),
            candidate("stub-chat", "stub", "stub-chat", 2, false)
        ));

        List<AiModelTarget> targets = selector.selectChatCandidates(null, true);

        assertEquals(List.of("deepseek-chat", "stub-chat"), targets.stream().map(AiModelTarget::id).toList());
    }

    /**
     * 仅保留旧式单模型配置时，选择器应补出默认真实候选，但不再偷偷拼接 stub 兜底。
     */
    @Test
    void selectChatCandidatesSynthesizesOnlyRealCandidateFromLegacyConfig() {
        AiProperties properties = new AiProperties();
        properties.setProvider("deepseek");
        properties.setBaseUrl("https://api.deepseek.com/v1");
        properties.setApiKey("test-key");
        properties.setChatModel("deepseek-chat");
        AiModelSelector selector = new AiModelSelector(properties);

        List<AiModelTarget> targets = selector.selectChatCandidates(null, false);

        assertEquals(List.of("deepseek-chat"), targets.stream().map(AiModelTarget::id).toList());
    }

    /**
     * 视觉候选应能作为普通聊天候选进入调度池，供附件路由复用。
     */
    @Test
    void selectChatCandidatesIncludesVisionCandidate() {
        AiModelSelector selector = new AiModelSelector(buildProperties(
            candidate("qwen-plus", "bailian", "qwen-plus-latest", 1, false, false),
            candidate("qwen3.6-plus", "bailian", "qwen3.6-plus", 2, true, true)
        ));

        List<AiModelTarget> targets = selector.selectChatCandidates("qwen3.6-plus", false);

        assertEquals("qwen3.6-plus", targets.getFirst().id());
        assertTrue(targets.stream().anyMatch(target -> Boolean.TRUE.equals(target.candidate().getSupportsVision())));
    }

    /**
     * 带图片附件时应优先选择优先级更高的硅基流动千问视觉候选。
     */
    @Test
    void selectChatCandidatesPrefersSiliconFlowVisionCandidateForImageAttachments() {
        AiModelSelector selector = new AiModelSelector(buildProperties(
            candidate("siliconflow-deepseek-v4-flash", "siliconflow", "deepseek-ai/DeepSeek-V4-Flash", 1, false, false),
            candidate("siliconflow-qwen3.5-122b-a10b", "siliconflow", "Qwen/Qwen3.5-122B-A10B", 3, true, true),
            candidate("qwen3.6-plus", "bailian", "qwen3.6-plus", 6, true, true)
        ));

        ChatAttachment imageAttachment = ChatAttachment.builder()
            .attachmentType("image")
            .build();

        List<AiModelTarget> targets = selector.selectChatCandidates(null, false, List.of(imageAttachment));

        assertEquals("siliconflow-qwen3.5-122b-a10b", targets.getFirst().id());
        assertTrue(Boolean.TRUE.equals(targets.getFirst().candidate().getSupportsVision()));
    }

    /**
     * 当系统配置表显式提供候选池时，选择器应优先使用动态候选而不是静态 YAML 骨架。
     */
    @Test
    void selectChatCandidatesPrefersDynamicCandidatesFromSystemSettings() {
        AiProperties properties = buildProperties(
            candidate("static-deepseek", "deepseek", "deepseek-chat", 1, false)
        );
        DynamicAiProperties dynamicAiProperties = Mockito.mock(DynamicAiProperties.class);
        Mockito.when(dynamicAiProperties.chatCandidates()).thenReturn(List.of(
            candidate("dynamic-siliconflow", "siliconflow", "deepseek-ai/DeepSeek-V4-Flash", 1, false)
        ));
        Mockito.when(dynamicAiProperties.providers()).thenReturn(Map.of(
            "siliconflow", provider("https://api.siliconflow.cn", "test-key"),
            "deepseek", provider("https://api.deepseek.com/v1", "test-key"),
            "stub", provider("stub://local", "")
        ));

        AiModelSelector selector = new AiModelSelector(properties, null, dynamicAiProperties);

        List<AiModelTarget> targets = selector.selectChatCandidates(null, false);

        assertEquals("dynamic-siliconflow", targets.getFirst().id());
    }

    /**
     * 动态候选池生效时，无显式首选模型也应按候选优先级排序，不再读取系统默认模型指针。
     */
    @Test
    void selectChatCandidatesUsesDynamicCandidatePriorityWhenRequestHasNoPreference() {
        AiProperties properties = buildProperties(
            candidate("static-deepseek", "deepseek", "deepseek-chat", 1, false)
        );
        DynamicAiProperties dynamicAiProperties = Mockito.mock(DynamicAiProperties.class);
        Mockito.when(dynamicAiProperties.chatCandidates()).thenReturn(List.of(
            candidate("priority-first", "siliconflow", "model-a", 1, false),
            candidate("configured-default", "siliconflow", "model-b", 9, false)
        ));
        Mockito.when(dynamicAiProperties.providers()).thenReturn(Map.of(
            "siliconflow", provider("https://api.siliconflow.cn", "test-key")
        ));

        AiModelSelector selector = new AiModelSelector(properties, null, dynamicAiProperties);

        List<AiModelTarget> targets = selector.selectChatCandidates(null, false);

        assertEquals("priority-first", targets.getFirst().id());
    }

    /**
     * 动态候选池生效时，深度思考候选也应按优先级排序，不再读取系统深度思考默认指针。
     */
    @Test
    void selectChatCandidatesUsesDynamicThinkingCandidatePriorityWhenRequestHasNoPreference() {
        AiProperties properties = buildProperties(
            candidate("static-deepseek", "deepseek", "deepseek-chat", 1, false)
        );
        DynamicAiProperties dynamicAiProperties = Mockito.mock(DynamicAiProperties.class);
        Mockito.when(dynamicAiProperties.chatCandidates()).thenReturn(List.of(
            candidate("thinking-priority-first", "siliconflow", "model-a", 1, true),
            candidate("configured-thinking", "siliconflow", "model-b", 9, true)
        ));
        Mockito.when(dynamicAiProperties.providers()).thenReturn(Map.of(
            "siliconflow", provider("https://api.siliconflow.cn", "test-key")
        ));

        AiModelSelector selector = new AiModelSelector(properties, null, dynamicAiProperties);

        List<AiModelTarget> targets = selector.selectChatCandidates(null, true);

        assertEquals("thinking-priority-first", targets.getFirst().id());
    }

    /**
     * 生成测试配置，避免依赖 Spring 配置绑定。
     * @param candidates 候选模型。
     * @return 测试配置。
     */
    private AiProperties buildProperties(AiProperties.ChatCandidate... candidates) {
        AiProperties properties = new AiProperties();
        HashMap<String, AiProperties.Provider> providers = new HashMap<>();
        providers.put("deepseek", provider("https://api.deepseek.com/v1", "test-key"));
        providers.put("siliconflow", provider("https://api.siliconflow.cn", "test-key"));
        providers.put("bailian", provider("https://dashscope.aliyuncs.com/compatible-mode/v1", "test-key"));
        providers.put("stub", provider("stub://local", ""));
        properties.setProviders(providers);

        AiProperties.ChatModelGroup chat = new AiProperties.ChatModelGroup();
        chat.setDefaultModel(candidates[0].getId());
        chat.setDeepThinkingModel(candidates[candidates.length - 1].getId());
        chat.setCandidates(List.of(candidates));
        properties.setChat(chat);
        return properties;
    }

    /**
     * 生成候选定义，保证测试关注点集中在选择规则本身。
     * @param id 候选 ID。
     * @param provider provider 名称。
     * @param model 模型名称。
     * @param priority 优先级。
     * @param supportsThinking 是否支持 thinking。
     * @return 候选配置。
     */
    private AiProperties.ChatCandidate candidate(
        String id,
        String provider,
        String model,
        int priority,
        boolean supportsThinking,
        boolean supportsVision
    ) {
        AiProperties.ChatCandidate candidate = new AiProperties.ChatCandidate();
        candidate.setId(id);
        candidate.setProvider(provider);
        candidate.setModel(model);
        candidate.setPriority(priority);
        candidate.setSupportsThinking(supportsThinking);
        candidate.setSupportsVision(supportsVision);
        candidate.setEnabled(true);
        return candidate;
    }

    /**
     * 兼容既有测试调用，默认不标记视觉能力。
     * @param id 候选 ID。
     * @param provider provider 名称。
     * @param model 模型名称。
     * @param priority 优先级。
     * @param supportsThinking 是否支持 thinking。
     * @return 候选配置。
     */
    private AiProperties.ChatCandidate candidate(
        String id,
        String provider,
        String model,
        int priority,
        boolean supportsThinking
    ) {
        return candidate(id, provider, model, priority, supportsThinking, false);
    }

    /**
     * 生成 provider 配置，满足模型目标构建依赖。
     * @param baseUrl 基础地址。
     * @param apiKey API Key。
     * @return provider 配置。
     */
    private AiProperties.Provider provider(String baseUrl, String apiKey) {
        AiProperties.Provider provider = new AiProperties.Provider();
        provider.setBaseUrl(baseUrl);
        provider.setApiKey(apiKey);
        return provider;
    }
}
