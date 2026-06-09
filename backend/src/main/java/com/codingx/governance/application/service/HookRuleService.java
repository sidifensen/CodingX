package com.codingx.governance.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.domain.port.ChatStreamPublisher;
import com.codingx.common.exception.BusinessException;
import com.codingx.governance.domain.model.GovernanceHookRule;
import com.codingx.governance.domain.repository.GovernanceHookRuleRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Hook 规则应用服务，负责按任务生命周期事件匹配自动化 Hook 配置。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HookRuleService {

    /** Hook 规则仓储，用于按触发点读取当前启用规则。 */
    private final GovernanceHookRuleRepository ruleRepository;

    /** 聊天流事件发布端口，用于把桌面通知动作交给来源会话的在线桌面端消费。 */
    private final ChatStreamPublisher chatStreamPublisher;

    /**
     * 触发生命周期 Hook 并返回命中的自动化规则；当前方法只做规则匹配，不执行外部脚本或写审计表。
     * @param triggerPoint 触发点。
     * @param conversationId 会话 ID，可为空。
     * @param runId 运行 ID，可为空。
     * @param toolCode 工具编码，可为空。
     * @param contextText 当前触发上下文摘要。
     * @return 本次命中的启用 Hook 规则，调用方可交给桌面通知、宠物联动或脚本执行器处理。
     */
    public List<GovernanceHookRule> trigger(String triggerPoint, Long conversationId, Long runId, String toolCode, String contextText) {
        // 步骤 1：只读取当前触发点启用规则，禁用规则不会进入后续桌面通知或宠物联动。
        List<GovernanceHookRule> matchedRules = ruleRepository.findEnabledByTriggerPoint(triggerPoint).stream()
            .filter(rule -> matchesCondition(rule, contextText))
            .sorted(Comparator
                .comparing((GovernanceHookRule rule) -> rule.getSortNo() == null ? Integer.MAX_VALUE : rule.getSortNo())
                .thenComparing(rule -> StrUtil.blankToDefault(rule.getHookCode(), "")))
            .toList();
        // 步骤 2：命中规则后只执行已支持的轻量动作；其他动作仍返回给未来执行器扩展。
        for (GovernanceHookRule rule : matchedRules) {
            log.info(
                "Hook规则已匹配: 规则编码={}, 触发点={}, 动作类型={}, 会话ID={}, 运行ID={}, 工具编码={}",
                rule.getHookCode(),
                triggerPoint,
                rule.getActionType(),
                conversationId,
                runId,
                toolCode
            );
            publishDesktopNotificationIfNeeded(rule, triggerPoint, conversationId, runId, toolCode, contextText);
        }
        return matchedRules;
    }

    /**
     * 查询全部 Hook 规则。
     * @return 未删除规则列表。
     */
    public List<GovernanceHookRule> listRules() {
        return ruleRepository.findAll();
    }

    /**
     * 新增 Hook 规则，默认动作使用桌面通知；真实本机能力由后续桌面执行器按 actionType 消费。
     */
    public GovernanceHookRule createRule(GovernanceHookRule rule) {
        LocalDateTime now = LocalDateTime.now();
        GovernanceHookRule saved = rule.toBuilder()
            .id(IdUtil.getSnowflakeNextId())
            .hookCode(required(rule.getHookCode(), "Hook编码不能为空"))
            .hookName(required(rule.getHookName(), "Hook名称不能为空"))
            .triggerPoint(required(rule.getTriggerPoint(), "Hook触发点不能为空").toUpperCase(Locale.ROOT))
            .actionType(StrUtil.blankToDefault(rule.getActionType(), "DESKTOP_NOTIFY").toUpperCase(Locale.ROOT))
            .actionConfigJson(StrUtil.blankToDefault(rule.getActionConfigJson(), "{}"))
            .enabled(rule.getEnabled() == null ? 1 : rule.getEnabled())
            .sortNo(rule.getSortNo() == null ? 0 : rule.getSortNo())
            .createdAt(now)
            .updatedAt(now)
            .deleted(0)
            .build();
        ruleRepository.save(saved);
        return saved;
    }

    /**
     * 更新 Hook 规则，保留管理员配置的动作类型，供后续自动化执行器分发。
     */
    public GovernanceHookRule updateRule(Long id, GovernanceHookRule rule) {
        GovernanceHookRule existing = ruleRepository.findById(id);
        if (existing == null) {
            throw new BusinessException("GOVERNANCE_HOOK_NOT_FOUND", "Hook规则不存在");
        }
        GovernanceHookRule saved = rule.toBuilder()
            .id(id)
            .hookCode(required(rule.getHookCode(), "Hook编码不能为空"))
            .hookName(required(rule.getHookName(), "Hook名称不能为空"))
            .triggerPoint(required(rule.getTriggerPoint(), "Hook触发点不能为空").toUpperCase(Locale.ROOT))
            .actionType(StrUtil.blankToDefault(rule.getActionType(), existing.getActionType()).toUpperCase(Locale.ROOT))
            .actionConfigJson(StrUtil.blankToDefault(rule.getActionConfigJson(), "{}"))
            .enabled(rule.getEnabled() == null ? existing.getEnabled() : rule.getEnabled())
            .sortNo(rule.getSortNo() == null ? existing.getSortNo() : rule.getSortNo())
            .createdAt(existing.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .deleted(0)
            .build();
        ruleRepository.save(saved);
        return saved;
    }

    /**
     * 逻辑删除 Hook 规则。
     * @param id Hook 规则主键。
     */
    public void deleteRule(Long id) {
        ruleRepository.softDeleteById(id);
    }

    private boolean matchesCondition(GovernanceHookRule rule, String contextText) {
        return StrUtil.isBlank(rule.getConditionKeyword())
            || StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(contextText), rule.getConditionKeyword());
    }

    /**
     * 对 DESKTOP_NOTIFY 动作发布会话内 Hook 通知；无会话 ID 时无法路由，直接跳过。
     * @param rule 当前命中的 Hook 规则。
     * @param triggerPoint 当前触发点。
     * @param conversationId 来源会话 ID。
     * @param runId 后台运行 ID。
     * @param toolCode 工具编码。
     * @param contextText 触发上下文摘要。
     */
    private void publishDesktopNotificationIfNeeded(
        GovernanceHookRule rule,
        String triggerPoint,
        Long conversationId,
        Long runId,
        String toolCode,
        String contextText
    ) {
        // 步骤 1：只处理桌面通知动作，避免把宠物、脚本、Webhook 等未来动作误发给系统通知。
        if (!"DESKTOP_NOTIFY".equalsIgnoreCase(StrUtil.blankToDefault(rule.getActionType(), ""))) {
            return;
        }
        if (conversationId == null) {
            log.warn("Hook桌面通知缺少会话ID，已跳过通知发布: 规则编码={}, 触发点={}, 运行ID={}",
                rule.getHookCode(), triggerPoint, runId);
            return;
        }
        // 步骤 2：通知内容允许管理员通过 actionConfigJson 覆盖，解析失败时使用中文兜底文案。
        Map<String, String> actionConfig = parseDesktopNotificationConfig(rule);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "desktop-notify");
        payload.put("hookCode", StrUtil.nullToEmpty(rule.getHookCode()));
        payload.put("hookName", StrUtil.nullToEmpty(rule.getHookName()));
        payload.put("triggerPoint", triggerPoint);
        payload.put("actionType", "DESKTOP_NOTIFY");
        payload.put("conversationId", conversationId);
        if (runId != null) {
            payload.put("runId", runId);
        }
        if (StrUtil.isNotBlank(toolCode)) {
            payload.put("toolCode", toolCode);
        }
        payload.put("title", StrUtil.blankToDefault(actionConfig.get("title"), "CodingX 通知"));
        payload.put("body", StrUtil.blankToDefault(actionConfig.get("body"), "有一项后台任务状态已更新"));
        payload.put("contextText", StrUtil.nullToEmpty(contextText));
        // 步骤 3：通知作为运行期 SSE 发布，前端和桌面端失败不会反向影响任务执行。
        chatStreamPublisher.publishHookNotification(conversationId, payload);
    }

    /**
     * 解析桌面通知动作配置，仅识别 title/body 两个字段。
     * @param rule 当前 Hook 规则。
     * @return 归一化后的通知配置。
     */
    private Map<String, String> parseDesktopNotificationConfig(GovernanceHookRule rule) {
        if (StrUtil.isBlank(rule.getActionConfigJson())) {
            return Map.of();
        }
        try {
            JSONObject config = JSONUtil.parseObj(rule.getActionConfigJson());
            return Map.of(
                "title", StrUtil.nullToEmpty(config.getStr("title")).trim(),
                "body", StrUtil.nullToEmpty(config.getStr("body")).trim()
            );
        } catch (RuntimeException exception) {
            log.warn("Hook桌面通知配置解析失败，已使用默认中文文案: 规则编码={}, 异常信息={}",
                rule.getHookCode(), exception.getMessage());
            return Map.of();
        }
    }

    private String required(String value, String message) {
        if (StrUtil.isBlank(value)) {
            throw new BusinessException("GOVERNANCE_REQUIRED_FIELD", message);
        }
        return value.trim();
    }
}
