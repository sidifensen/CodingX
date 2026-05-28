package com.codingx.common.support.ai;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.config.DynamicAiRoutingProperties;
import com.codingx.config.AiProperties;
import com.codingx.config.DynamicAiProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 根据配置、首选模型和思考模式选择有序模型候选列表。
 */
public class AiModelSelector {

    private static final String STUB_PROVIDER = "stub";
    private static final String STUB_MODEL_ID = "stub-chat";
    private static final String STUB_MODEL_NAME = "stub-chat";

    private final AiProperties aiProperties;
    private final DynamicAiRoutingProperties dynamicProperties;
    private final DynamicAiProperties dynamicAiProperties;

    /**
     * 使用 AI 配置构造模型选择器。
     * @param aiProperties AI 配置。
     */
    public AiModelSelector(AiProperties aiProperties) {
        this(aiProperties, null, null);
    }

    /**
     * 使用 AI 配置和动态路由配置构造模型选择器。
     * @param aiProperties AI 配置。
     * @param dynamicProperties 动态路由配置。
     */
    public AiModelSelector(AiProperties aiProperties, DynamicAiRoutingProperties dynamicProperties) {
        this(aiProperties, dynamicProperties, null);
    }

    /**
     * 使用 AI 配置、动态路由配置和动态 provider 配置构造模型选择器。
     * @param aiProperties AI 配置。
     * @param dynamicProperties 动态路由配置。
     * @param dynamicAiProperties 动态 AI 配置。
     */
    public AiModelSelector(
        AiProperties aiProperties,
        DynamicAiRoutingProperties dynamicProperties,
        DynamicAiProperties dynamicAiProperties
    ) {
        this.aiProperties = aiProperties;
        this.dynamicProperties = dynamicProperties;
        this.dynamicAiProperties = dynamicAiProperties;
    }

    /**
     * 返回当前配置的首包等待超时时间，供路由层决定首包握手窗口。
     * @return 首包等待毫秒数。
     */
    public long firstPacketTimeoutMs() {
        if (dynamicProperties != null) {
            return dynamicProperties.firstPacketTimeoutMs();
        }
        return aiProperties.getSelection().getFirstPacketTimeoutMs();
    }

    /**
     * 选择聊天候选模型。
     * @param preferredModel 显式指定的优先模型。
     * @param thinkingEnabled 是否开启思考模式。
     * @return 有序候选列表。
     */
    public List<AiModelTarget> selectChatCandidates(String preferredModel, boolean thinkingEnabled) {
        return selectChatCandidates(preferredModel, thinkingEnabled, List.of());
    }

    /**
     * 根据附件能力补充候选过滤，图片存在时优先命中视觉模型。
     * @param preferredModel 显式指定的优先模型。
     * @param thinkingEnabled 是否开启思考模式。
     * @param attachments 当前会话消息附件。
     * @return 有序候选列表。
     */
    public List<AiModelTarget> selectChatCandidates(
        String preferredModel,
        boolean thinkingEnabled,
        List<ChatAttachment> attachments
    ) {
        AiProperties.ChatModelGroup group = chatGroupWithFallback();
        List<AiProperties.ChatCandidate> candidates = enabledCandidates(group.getCandidates());
        boolean hasImageAttachment = CollUtil.emptyIfNull(attachments).stream()
            .anyMatch(attachment -> "image".equalsIgnoreCase(attachment.getAttachmentType()));
        if (hasImageAttachment) {
            List<AiProperties.ChatCandidate> visionCandidates = candidates.stream()
                .filter(candidate -> Boolean.TRUE.equals(candidate.getSupportsVision()))
                .toList();
            if (CollUtil.isNotEmpty(visionCandidates)) {
                candidates = visionCandidates;
            }
        }
        List<AiProperties.ChatCandidate> filteredCandidates = thinkingEnabled
            ? candidates.stream().filter(candidate -> Boolean.TRUE.equals(candidate.getSupportsThinking())).toList()
            : candidates;
        boolean fellBackToNormalCandidates = false;
        if (thinkingEnabled && CollUtil.isEmpty(filteredCandidates)) {
            filteredCandidates = candidates;
            fellBackToNormalCandidates = true;
        }
        String firstChoice = resolveFirstChoiceModel(group, preferredModel, thinkingEnabled && !fellBackToNormalCandidates);
        Map<String, AiProperties.Provider> providers = mergedProviders();
        return filteredCandidates.stream()
            .sorted(Comparator
                .comparing((AiProperties.ChatCandidate candidate) -> !Objects.equals(candidate.getId(), firstChoice))
                .thenComparing(AiProperties.ChatCandidate::getPriority, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(AiProperties.ChatCandidate::getId, Comparator.nullsLast(String::compareTo)))
            .map(candidate -> toTarget(candidate, providers))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 兼容旧式单模型配置，确保至少有一个真实候选和一个 stub 候选。
     * @return 具备候选列表的聊天模型组。
     */
    private AiProperties.ChatModelGroup chatGroupWithFallback() {
        AiProperties.ChatModelGroup configured = aiProperties.getChat() == null ? new AiProperties.ChatModelGroup() : aiProperties.getChat();
        if (CollUtil.isNotEmpty(configured.getCandidates())) {
            return configured;
        }
        AiProperties.ChatModelGroup fallbackGroup = new AiProperties.ChatModelGroup();
        fallbackGroup.setDefaultModel(StrUtil.blankToDefault(configured.getDefaultModel(), fallbackChatModel()));
        fallbackGroup.setDeepThinkingModel(StrUtil.blankToDefault(configured.getDeepThinkingModel(), fallbackGroup.getDefaultModel()));
        List<AiProperties.ChatCandidate> candidates = new ArrayList<>();
        candidates.add(realFallbackCandidate());
        candidates.add(stubCandidate());
        fallbackGroup.setCandidates(candidates);
        return fallbackGroup;
    }

    /**
     * 合并显式 provider 配置与旧式单 provider 配置。
     * @return provider 映射。
     */
    private Map<String, AiProperties.Provider> mergedProviders() {
        Map<String, AiProperties.Provider> providers = new HashMap<>(aiProperties.getProviders());
        if (dynamicAiProperties != null) {
            providers = new HashMap<>(dynamicAiProperties.providers());
        }
        if (!providers.containsKey(fallbackProvider())) {
            AiProperties.Provider provider = new AiProperties.Provider();
            provider.setBaseUrl(fallbackBaseUrl());
            provider.setApiKey(fallbackApiKey());
            providers.put(fallbackProvider(), provider);
        }
        if (!providers.containsKey(STUB_PROVIDER)) {
            AiProperties.Provider stubProvider = new AiProperties.Provider();
            stubProvider.setBaseUrl("stub://local");
            stubProvider.setApiKey("");
            providers.put(STUB_PROVIDER, stubProvider);
        }
        return providers;
    }

    /**
     * 过滤未启用候选，保证排序结果只包含真实可选项。
     * @param candidates 原始候选列表。
     * @return 启用候选。
     */
    private List<AiProperties.ChatCandidate> enabledCandidates(List<AiProperties.ChatCandidate> candidates) {
        return CollUtil.emptyIfNull(candidates).stream()
            .filter(Objects::nonNull)
            .filter(candidate -> !Boolean.FALSE.equals(candidate.getEnabled()))
            .toList();
    }

    /**
     * 决定本次路由的首选模型。
     * @param group 模型组。
     * @param preferredModel 显式偏好模型。
     * @param thinkingEnabled 是否开启思考模式。
     * @return 首选模型 ID。
     */
    private String resolveFirstChoiceModel(AiProperties.ChatModelGroup group, String preferredModel, boolean thinkingEnabled) {
        if (StrUtil.isNotBlank(preferredModel)) {
            return preferredModel;
        }
        String configuredThinkingModel = dynamicProperties == null ? group.getDeepThinkingModel() : dynamicProperties.deepThinkingModel();
        if (thinkingEnabled && StrUtil.isNotBlank(configuredThinkingModel)) {
            return configuredThinkingModel;
        }
        return StrUtil.blankToDefault(
            dynamicProperties == null ? group.getDefaultModel() : dynamicProperties.defaultModel(),
            group.getDefaultModel()
        );
    }

    /**
     * 将候选与 provider 配置组装成路由目标。
     * @param candidate 候选定义。
     * @param providers provider 映射。
     * @return 路由目标；若 provider 缺失则返回 null。
     */
    private AiModelTarget toTarget(AiProperties.ChatCandidate candidate, Map<String, AiProperties.Provider> providers) {
        AiProperties.Provider provider = providers.get(candidate.getProvider());
        if (provider == null) {
            return null;
        }
        String id = StrUtil.blankToDefault(candidate.getId(), candidate.getProvider() + "::" + candidate.getModel());
        return new AiModelTarget(id, candidate, provider);
    }

    /**
     * 由旧式单模型配置合成真实候选。
     * @return 默认真实候选。
     */
    private AiProperties.ChatCandidate realFallbackCandidate() {
        AiProperties.ChatCandidate candidate = new AiProperties.ChatCandidate();
        candidate.setId(fallbackChatModel());
        candidate.setProvider(fallbackProvider());
        candidate.setModel(fallbackChatModel());
        candidate.setPriority(1);
        candidate.setEnabled(true);
        candidate.setSupportsThinking(false);
        return candidate;
    }

    private String fallbackProvider() {
        return dynamicAiProperties == null ? aiProperties.getProvider() : dynamicAiProperties.provider();
    }

    private String fallbackBaseUrl() {
        return dynamicAiProperties == null ? aiProperties.getBaseUrl() : dynamicAiProperties.baseUrl();
    }

    private String fallbackApiKey() {
        return dynamicAiProperties == null ? aiProperties.getApiKey() : dynamicAiProperties.apiKey();
    }

    private String fallbackChatModel() {
        return dynamicAiProperties == null ? aiProperties.getChatModel() : dynamicAiProperties.chatModel();
    }

    /**
     * 生成本地 stub 保底候选。
     * @return stub 候选。
     */
    private AiProperties.ChatCandidate stubCandidate() {
        AiProperties.ChatCandidate candidate = new AiProperties.ChatCandidate();
        candidate.setId(STUB_MODEL_ID);
        candidate.setProvider(STUB_PROVIDER);
        candidate.setModel(STUB_MODEL_NAME);
        candidate.setPriority(999);
        candidate.setEnabled(true);
        candidate.setSupportsThinking(true);
        return candidate;
    }
}

