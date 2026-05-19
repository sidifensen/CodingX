package com.codingx.support.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.config.AiProperties;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

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
     * 仅保留旧式单模型配置时，选择器仍应补出默认真实候选与 stub 候选。
     */
    @Test
    void selectChatCandidatesSynthesizesDefaultAndStubCandidatesFromLegacyConfig() {
        AiProperties properties = new AiProperties();
        properties.setProvider("deepseek");
        properties.setBaseUrl("https://api.deepseek.com/v1");
        properties.setApiKey("test-key");
        properties.setChatModel("deepseek-chat");
        AiModelSelector selector = new AiModelSelector(properties);

        List<AiModelTarget> targets = selector.selectChatCandidates(null, false);

        assertTrue(targets.stream().anyMatch(target -> "deepseek-chat".equals(target.id())));
        assertTrue(targets.stream().anyMatch(target -> "stub-chat".equals(target.id())));
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
     * 生成测试配置，避免依赖 Spring 配置绑定。
     * @param candidates 候选模型。
     * @return 测试配置。
     */
    private AiProperties buildProperties(AiProperties.ChatCandidate... candidates) {
        AiProperties properties = new AiProperties();
        HashMap<String, AiProperties.Provider> providers = new HashMap<>();
        providers.put("deepseek", provider("https://api.deepseek.com/v1", "test-key"));
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
