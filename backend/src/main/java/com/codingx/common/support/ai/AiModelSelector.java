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

    /**
     * 静态 AI 配置，作为候选池、provider 和选择参数的基础来源。
     */
    private final AiProperties aiProperties;

    /**
     * 动态路由配置，用于覆盖首包等待超时等路由参数。
     */
    private final DynamicAiRoutingProperties dynamicProperties;

    /**
     * 动态 AI 配置，用于从系统配置表读取 provider 和候选池覆盖值。
     */
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
        // 步骤 1：选择器只保存配置依赖，不在构造期解析候选池，确保运行时配置可动态生效。
        this.aiProperties = aiProperties;
        this.dynamicProperties = dynamicProperties;
        this.dynamicAiProperties = dynamicAiProperties;
    }

    /**
     * 返回当前配置的首包等待超时时间，供路由层决定首包握手窗口。
     * @return 首包等待毫秒数。
     */
    public long firstPacketTimeoutMs() {
        // 步骤 1：优先读取动态首包等待超时，缺省时回退静态配置。
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
        // 步骤 1：读取候选池；动态候选存在时会覆盖静态候选，静态配置用于兼容旧环境。
        AiProperties.ChatModelGroup group = chatGroupWithFallback();
        List<AiProperties.ChatCandidate> candidates = enabledCandidates(group.getCandidates());
        // 步骤 2：存在图片附件时优先筛选视觉模型；若没有视觉候选则保留原候选池。
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
        // 步骤 3：深度思考模式只选择支持 thinking 的候选；若没有可用候选则回退普通候选。
        List<AiProperties.ChatCandidate> filteredCandidates = thinkingEnabled
            ? candidates.stream().filter(candidate -> Boolean.TRUE.equals(candidate.getSupportsThinking())).toList()
            : candidates;
        boolean fellBackToNormalCandidates = false;
        if (thinkingEnabled && CollUtil.isEmpty(filteredCandidates)) {
            filteredCandidates = candidates;
            fellBackToNormalCandidates = true;
        }
        // 步骤 4：只保留请求显式首选模型；未指定时完全交由候选池 priority 决定默认顺序。
        String firstChoice = resolveFirstChoiceModel(preferredModel);
        Map<String, AiProperties.Provider> providers = mergedProviders();
        // 步骤 5：按首选模型、优先级和候选 ID 排序，并过滤缺少 provider 配置的候选。
        return filteredCandidates.stream()
            .sorted(Comparator
                .comparing((AiProperties.ChatCandidate candidate) -> !Objects.equals(candidate.getId(), firstChoice))
                .thenComparing(candidate -> shouldDeferThinkingCandidate(thinkingEnabled, firstChoice, candidate))
                .thenComparing(AiProperties.ChatCandidate::getPriority, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(AiProperties.ChatCandidate::getId, Comparator.nullsLast(String::compareTo)))
            .map(candidate -> toTarget(candidate, providers))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 兼容旧式单模型配置，确保至少有一个真实候选，避免历史环境因未迁移候选池而失去可用模型。
     * @return 具备候选列表的聊天模型组。
     */
    private AiProperties.ChatModelGroup chatGroupWithFallback() {
        // 步骤 1：优先读取动态候选池，允许管理端系统配置实时调整模型池。
        AiProperties.ChatModelGroup configured = aiProperties.getChat() == null ? new AiProperties.ChatModelGroup() : aiProperties.getChat();
        List<AiProperties.ChatCandidate> dynamicCandidates = dynamicAiProperties == null ? List.of() : dynamicAiProperties.chatCandidates();
        if (CollUtil.isNotEmpty(dynamicCandidates)) {
            AiProperties.ChatModelGroup dynamicGroup = new AiProperties.ChatModelGroup();
            dynamicGroup.setCandidates(dynamicCandidates);
            // 动态候选池来自系统配置表；默认顺序由候选 priority 决定，不再读取默认模型指针。
            return dynamicGroup;
        }
        // 步骤 2：动态候选为空时使用静态候选池；静态也为空则返回空模型组。
        if (CollUtil.isNotEmpty(configured.getCandidates())) {
            return configured;
        }
        // 步骤 3：历史环境可能只有 app.ai.provider/base-url/api-key/chat-model，仍需合成一个真实候选保障兼容。
        return legacyChatGroup();
    }

    /**
     * 普通请求默认避开 thinking 候选，避免运行时配置把思考模型 priority 调高后误触发深度思考。
     * @param thinkingEnabled 本轮请求是否显式开启深度思考。
     * @param firstChoice 显式首选候选 ID；存在时说明调用方有明确模型选择。
     * @param candidate 当前候选。
     * @return true 表示普通请求排序时应把该 thinking 候选延后到非 thinking 候选之后。
     */
    private boolean shouldDeferThinkingCandidate(
        boolean thinkingEnabled,
        String firstChoice,
        AiProperties.ChatCandidate candidate
    ) {
        // 步骤 1：深度思考请求和显式模型选择都保留原排序语义，只在普通自动路由时调整默认桶。
        if (thinkingEnabled || StrUtil.isNotBlank(firstChoice)) {
            return false;
        }
        // 步骤 2：thinking 候选仍保留在列表尾部作为故障回退，但不能抢占普通请求默认入口。
        return Boolean.TRUE.equals(candidate.getSupportsThinking());
    }

    /**
     * 合并显式 provider 配置与旧式单 provider 配置。
     * @return provider 映射。
     */
    private Map<String, AiProperties.Provider> mergedProviders() {
        // 步骤 1：默认使用静态 provider 配置，存在动态配置时整体切换为动态合并结果。
        Map<String, AiProperties.Provider> providers = new HashMap<>(aiProperties.getProviders());
        if (dynamicAiProperties != null) {
            providers = new HashMap<>(dynamicAiProperties.providers());
        }
        // 步骤 2：没有多 provider 配置的旧环境使用 app.ai.* 单 provider 字段补齐 provider 映射。
        mergeLegacyProvider(providers);
        return providers;
    }

    /**
     * 将旧式单模型配置合成为候选池，兼容还没有迁移到候选池配置的环境。
     * @return 旧式配置生成的模型组；关键字段缺失时返回空模型组。
     */
    private AiProperties.ChatModelGroup legacyChatGroup() {
        // 步骤 1：旧配置至少需要 provider 和模型名，否则无法生成可调度目标。
        AiProperties.ChatModelGroup legacyGroup = new AiProperties.ChatModelGroup();
        if (StrUtil.hasBlank(aiProperties.getProvider(), aiProperties.getChatModel())) {
            return legacyGroup;
        }
        // 步骤 2：候选 ID 沿用模型名，保持健康熔断和历史消息记录的维度稳定。
        AiProperties.ChatCandidate candidate = new AiProperties.ChatCandidate();
        candidate.setId(aiProperties.getChatModel());
        candidate.setProvider(aiProperties.getProvider());
        candidate.setModel(aiProperties.getChatModel());
        candidate.setPriority(100);
        candidate.setEnabled(true);
        candidate.setSupportsThinking(false);
        candidate.setSupportsVision(false);
        legacyGroup.setCandidates(List.of(candidate));
        return legacyGroup;
    }

    /**
     * 将旧式单 provider 字段补入 provider 映射，保证旧式候选能解析到连接配置。
     * @param providers 当前 provider 映射。
     */
    private void mergeLegacyProvider(Map<String, AiProperties.Provider> providers) {
        // 步骤 1：多 provider 已存在时不覆盖，避免旧字段污染新配置。
        if (StrUtil.isBlank(aiProperties.getProvider()) || providers.containsKey(aiProperties.getProvider())) {
            return;
        }
        // 步骤 2：旧式 provider 至少需要基础地址；API Key 可为空，由下游客户端按 provider 规则处理。
        if (StrUtil.isBlank(aiProperties.getBaseUrl())) {
            return;
        }
        AiProperties.Provider provider = new AiProperties.Provider();
        provider.setBaseUrl(aiProperties.getBaseUrl());
        provider.setApiKey(aiProperties.getApiKey());
        providers.put(aiProperties.getProvider(), provider);
    }

    /**
     * 过滤未启用候选，保证排序结果只包含真实可选项。
     * @param candidates 原始候选列表。
     * @return 启用候选。
     */
    private List<AiProperties.ChatCandidate> enabledCandidates(List<AiProperties.ChatCandidate> candidates) {
        // 步骤 1：过滤 null 候选和显式 disabled 候选，未配置 enabled 时默认可用。
        return CollUtil.emptyIfNull(candidates).stream()
            .filter(Objects::nonNull)
            .filter(candidate -> !Boolean.FALSE.equals(candidate.getEnabled()))
            .toList();
    }

    /**
     * 决定本次路由的首选模型。
     * @param preferredModel 显式偏好模型。
     * @return 首选模型 ID。
     */
    private String resolveFirstChoiceModel(String preferredModel) {
        // 步骤 1：显式首选模型拥有最高优先级；空值表示不插队，后续排序完全使用候选池 priority。
        if (StrUtil.isNotBlank(preferredModel)) {
            return preferredModel;
        }
        return null;
    }

    /**
     * 将候选与 provider 配置组装成路由目标。
     * @param candidate 候选定义。
     * @param providers provider 映射。
     * @return 路由目标；若 provider 缺失则返回 null。
     */
    private AiModelTarget toTarget(AiProperties.ChatCandidate candidate, Map<String, AiProperties.Provider> providers) {
        // 步骤 1：候选必须能找到 provider 连接配置，否则不能参与本次路由。
        AiProperties.Provider provider = providers.get(candidate.getProvider());
        if (provider == null) {
            return null;
        }
        // 步骤 2：候选 ID 缺失时用 provider::model 生成稳定标识，便于健康熔断按模型维度记录。
        String id = StrUtil.blankToDefault(candidate.getId(), candidate.getProvider() + "::" + candidate.getModel());
        return new AiModelTarget(id, candidate, provider);
    }

}

